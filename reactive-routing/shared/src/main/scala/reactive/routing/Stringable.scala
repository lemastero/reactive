package reactive
package routing

import scala.util.Try

/**
* A typeclass for converting values to and from strings
*/
trait Stringable[A] {
  def format: A => String
  def parse: String => Option[A]
}

object Stringable extends StringablesCompat {
  implicit val string: Stringable[String] = new Stringable[String] {
    def format = identity
    def parse = Some(_)
  }
  implicit def long: Stringable[Long] = new Stringable[Long] {
    def format = _.toString
    def parse = s => try { Some(s.toLong) } catch { case e: NumberFormatException => None }
  }
  implicit val int: Stringable[Int] = new Stringable[Int] {
    def format = _.toString
    def parse = s => Try(s.toInt).toOption
  }
  implicit val bool: Stringable[Boolean] = new Stringable[Boolean] {
    def format = _.toString
    def parse = {
      case "true" => Some(true)
      case "false" => Some(false)
      case _ => None
    }
  }
}
