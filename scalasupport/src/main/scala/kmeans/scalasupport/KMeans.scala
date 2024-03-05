package kmeans.scalasupport

import scala.annotation.tailrec
import scala.util.Random

import java.util.concurrent.ForkJoinPool

class KMeans extends KMeansInterface:

  def generatePoints(k: Int, num: Int): Seq[Point] =
    val randx = Random(1)
    val randy = Random(3)
    val randz = Random(5)
    (0 until num)
      .map({ i =>
        val x = ((i + 1) % k) * 1.0 / k + randx.nextDouble() * 0.5
        val y = ((i + 5) % k) * 1.0 / k + randy.nextDouble() * 0.5
        val z = ((i + 7) % k) * 1.0 / k + randz.nextDouble() * 0.5
        Point(x, y, z)
      })

  def initializeMeans(k: Int, points: Seq[Point]): Seq[Point] =
    val rand = Random(7)
    (0 until k).map(_ => points(rand.nextInt(points.length)))

  def findClosest(p: Point, means: IterableOnce[Point]): Point =
    val it = means.iterator
    assert(it.nonEmpty)
    var closest = it.next()
    var minDistance = p.squareDistance(closest)
    while it.hasNext do
      val point = it.next()
      val distance = p.squareDistance(point)
      if distance < minDistance then
        minDistance = distance
        closest = point
    closest

  def classify(points: Seq[Point], means: Seq[Point]): Map[Point, Seq[Point]] = {
    var mns = Option(means)
    var meansEmpty = false
    if (means.isEmpty) {
      meansEmpty = true
      mns = Option.empty
    }
    var lst = Option(points)
    var pointsEmpty = false
    if (points.isEmpty) {
      pointsEmpty = true
      lst = Option.empty
    }
    val out = lst.getOrElse(Seq(Point(0.0, 0.0, 0.0))).map((point) => {
      // find the means that this point is closest to
      (mns.getOrElse(Seq(Point(0.0, 0.0, 0.0))).map(m => {
        (m, m.squareDistance(point))
      }).minBy((m, d) => {
        d
      })._1, point)
    }).groupBy(_._1).map(item => {
      (item._1, item._2.map(items => {
        items._2
      }))
    })
    if (!meansEmpty && pointsEmpty) {

      out.map(item => {
        (item._1, Vector())
      })
    } else if (meansEmpty && !pointsEmpty) {
      Map()

    }
    else if (meansEmpty && pointsEmpty) {
      Map()

    }
    else {
      out
    }
  }

  def findAverage(oldMean: Point, points: Seq[Point]): Point = if points.isEmpty then oldMean else
    var x = 0.0
    var y = 0.0
    var z = 0.0
    points.seq.foreach { p =>
      x += p.x
      y += p.y
      z += p.z
    }
    Point(x / points.length, y / points.length, z / points.length)


  def update(classified: Map[Point, Seq[Point]], oldMeans: Seq[Point]): Seq[Point] = {
    classified.map(point => {
      var lst = Option(point._2)
      if (lst.isEmpty) {
        lst = Option.empty
      }
      lst.getOrElse(Vector(Point(0.0, 0.0, 0.0))).fold(Point(0.0, 0.0, 0.0))((s, r) => {
        Point(s.x + r.x, s.y + r.y, s.z + r.z)
      }) / Point(point._2.size, point._2.size, point._2.size)
    }).toVector
  }

  def converged(eta: Double, oldMeans: Seq[Point], newMeans: Seq[Point]): Boolean = {

    !(oldMeans zip newMeans).map((p1, p2) => {
      p1.squareDistance(p2) <= eta
    }).exists(_ == false)

  }

  @tailrec
  final def kMeans(points: Seq[Point], means: Seq[Point], eta: Double): Seq[Point] =
    val newMeans = update(classify(points, means), means)

    if (!converged(eta, means, newMeans)) kMeans(points, newMeans, eta) else newMeans.reverse // your implementation need to be tail recursive

/** Describes one point in three-dimensional space.
 *
 * Note: deliberately uses reference equality.
 */
class Point(val x: Double, val y: Double, val z: Double):
  private def square(v: Double): Double = v * v

  def squareDistance(that: Point): Double =
    square(that.x - x) + square(that.y - y) + square(that.z - z)

  private def round(v: Double): Double = (v * 100).toInt / 100.0

  override def toString = s"(${round(x)}, ${round(y)}, ${round(z)})"

  def /(that: Point): Point = Point(this.x / that.x, this.y / that.y, this.z / that.z)
