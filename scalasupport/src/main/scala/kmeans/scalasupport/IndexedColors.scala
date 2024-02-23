package kmeans.scalasupport

import scala.jdk.CollectionConverters
import scala.jdk.CollectionConverters._
//import scala.collection.Seq
//import scala.collection.parallel.Seq
//import scala.collection.parallel.CollectionConverters.*
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
    Point(coordinate.getX(), coordinate.getY(), coordinate.getZ() )
  }).toList
  val means = initializeIndex(colorCount, points)


  val newMeans = kMeans(points, means, 0.1)

  /* And these are the results exposed */
  def getStatus() = s"Converged after $steps steps."
  def getResult(): java.util.List[Coordinate] = indexedImage(initialImage, newMeans).map(p => {
    Coordinate(p.x, p.y, p.z)
  }).asJava

//  private def imageToPoints(list: Seq[Coordinate]): Seq[Point] = {
//    list.map(coordinate => {
//      Point(coordinate.getX(), coordinate.getY(), coordinate.getZ() )
//
//    })
//  }

  //    for x <- 0 until img.width; y <- 0 until img.height yield
  //      val rgba = img(x, y)

  private def indexedImage(img: java.util.List[Coordinate], means: Seq[Point]): Seq[Point] =
//    val dst = Img(img.width, img.height)
    val pts = collection.mutable.Set[Point]()

    CollectionConverters.ListHasAsScala(initialImage).asScala.map(coordinate => {
      findClosest(Point(coordinate.getX, coordinate.getY, coordinate.getZ), means)
    }).toList

//    for x <- 0 until img.width; y <- 0 until img.height yield
//      val v = img(x, y)
//      var point = Point(red(v), green(v), blue(v))
//      point = findClosest(point, means)
//      pts += point
//      dst(x, y) = rgba(point.x, point.y, point.z, 1d)
//
//    dst

  private def initializeIndex(numColors: Int, points: Seq[Point]): Seq[Point] = {
    //val initialPoints: Seq[Point] =
    //      initStrategy match
    //{
      //        case RandomSampling =>
//        (0 until numColors).map(colorIndex => {
//          // 255 colors
//          numberOfExpectedMeans = numColors;
//          val meansColor = (255 / numColors) * colorIndex
//          Point(meansColor, meansColor, meansColor)
//        }
//
//        )
      //        val d: Int = points.size / numColors
//        if ( points.size != 0 ) { (0 until numColors) map (idx => points(d * idx)) }
      //        case UniformSampling =>
//                val sep: Int = 32
//                (for r <- 0 until 255 by sep; g <- 0 until 255 by sep; b <- 0 until 255 by sep yield {
//                  def inside(p: Point): Boolean =
//                    (p.x >= (r.toDouble / 255)) &&
//                      (p.x <= ((r.toDouble + sep) / 255)) &&
//                      (p.y >= (g.toDouble / 255)) &&
//                      (p.y <= ((g.toDouble + sep) / 255)) &&
//                      (p.z >= (b.toDouble / 255)) &&
//                      (p.z <= ((b.toDouble + sep) / 255))
//
//                  val pts = points.filter(inside(_))
//                  val cnt = pts.size * 3 * numColors / points.size
//                  if cnt >= 1 then {
//                    val d = pts.size / cnt
//                    (0 until cnt) map (idx => pts(d * idx))
//                  } else
//                    Seq()
//                }).flatten
      //        case UniformChoice =>
      val d: Int = math.max(1, (256 / math.cbrt(numColors.toDouble).ceil).toInt)
      (
        for r <- 0 until 256 by d; g <- 0 until 256 by d; b <- 0 until 256 by d yield
        Point(r.toDouble / 256,g.toDouble / 256, b.toDouble / 256)
        ).map(p => p);

//    }.toList
//    val d2 = initialPoints.size.toDouble / numColors
//    if ( initialPoints.size != 0 ) {
//      (0 until numColors) map (idx => initialPoints((idx * d2).toInt))
//    } else {
//      (0 until numColors).map(p => Point(0,0,0))
//    }
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
//      case ConvergedAfterMeansAreStill(eta) =>
//        super.converged(eta, oldMeans, newMeans)
//      case ConvergedWhenSNRAbove(snr_desired) =>
//        val snr_computed = computeSNR(points, newMeans)
//        snr_computed >= snr_desired
