package kmeans.scalasupport

//import scala.collection.{Map, Seq}
//import scala.collection.parallel.{Map, Seq}

/**
 * The interface used by the grading infrastructure. Do not change signatures
 * or your submission will fail with a NoSuchMethodError.
 */
trait KMeansInterface:
  def classify(points: Seq[Point], means: Seq[Point]): Map[Point, Seq[Point]]
  def update(classified: Map[Point, Seq[Point]], oldMeans: Seq[Point]): Seq[Point]
  def converged(eta: Double, oldMeans: Seq[Point], newMeans: Seq[Point]): Boolean
  def kMeans(points: Seq[Point], means: Seq[Point], eta: Double): Seq[Point]
