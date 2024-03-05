package kmeans.scalasupport

import scala.jdk.CollectionConverters
import scala.jdk.CollectionConverters._
import kmeans.solrSupport.Coordinate

abstract sealed trait InitialSelectionStrategy
case object RandomSampling extends InitialSelectionStrategy
case object UniformSampling extends InitialSelectionStrategy
case object UniformChoice extends InitialSelectionStrategy

abstract sealed trait ConvergenceStrategy
case class ConvergedWhenSNRAbove(x: Double) extends ConvergenceStrategy
case class ConvergedAfterNSteps(n: Int) extends ConvergenceStrategy
case class ConvergedAfterMeansAreStill(eta: Double) extends ConvergenceStrategy

object Path {
  def process: String => String = (input: String) => "processed"
}

class IndexedColorFilter(initialImage: java.util.List[Coordinate]) extends KMeans:
  val initStrategy = RandomSampling;
  val convStrategy = ConvergedAfterNSteps(3)
  private var steps = 0
  val colorCount = 16   // add one to intended color count, off by one
  val points = CollectionConverters.ListHasAsScala(initialImage).asScala.map(coordinate => {
    Point(coordinate.XD(), coordinate.YD(), coordinate.ZD() )
  }).toList
  val means = initializeIndex(colorCount, points)


  val newMeans = kMeans(points, means, 0.1)

  /* And these are the results exposed */
  def getStatus() = s"Converged after $steps steps."
  def getResult(): java.util.List[Coordinate] = indexedImage(initialImage, newMeans).map(p => {
    Coordinate(p.x, p.y, p.z)
  }).asJava

  private def indexedImage(img: java.util.List[Coordinate], means: Seq[Point]): Seq[Point] =
    val pts = collection.mutable.Set[Point]()

    CollectionConverters.ListHasAsScala(initialImage).asScala.map(coordinate => {
      findClosest(Point(coordinate.XD(), coordinate.YD(), coordinate.ZD()), means)
    }).toList

  private def initializeIndex(numColors: Int, points: Seq[Point]): Seq[Point] = {
      val d: Int = math.max(1, (256 / math.cbrt(numColors.toDouble).ceil).toInt)
      (
        for r <- 0 until 256 by d; g <- 0 until 256 by d; b <- 0 until 256 by d yield
        Point(r.toDouble / 256,g.toDouble / 256, b.toDouble / 256)
        ).map(p => p);
  }
  private def computeSNR(points: Seq[Point], means: Seq[Point]): Double =
    var sound = 0.0
    var noise = 0.0

    for point <- points do
      import math.{pow, sqrt}
      val closest = findClosest(point, means)
      sound += sqrt(pow(point.x, 2) + pow(point.y, 2) + pow(point.z, 2))
      noise += sqrt(pow(point.x - closest.x, 2) + pow(point.y - closest.y, 2) + pow(point.z - closest.z, 2))
    sound/noise

  override def converged(eta: Double, oldMeans: Seq[Point], newMeans: Seq[Point]): Boolean =
    steps += 1


    convStrategy match
      case ConvergedAfterNSteps(n) =>
        steps >= n
