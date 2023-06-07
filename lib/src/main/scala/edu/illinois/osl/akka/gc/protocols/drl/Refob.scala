package edu.illinois.osl.akka.gc.protocols.drl

import edu.illinois.osl.akka.gc.interfaces._
import scala.annotation.unchecked.uncheckedVariance

/**
 * An opaque and globally unique token.
 */
case class Token(ref: Name, n: Int) extends Pretty {
  def pretty: String = s"Token#${Math.floorMod(this.hashCode(), 1000)}"
}

case class Refob[-T](
  token: Option[Token],
  owner: Option[RefLike[GCMessage[Nothing]]],
  target: RefLike[GCMessage[T]],
) extends RefobLike[T] {
  override def pretty: String = {
    f"<Refob#${Math.floorMod(token.hashCode(), 1000)}: ${owner.get.pretty} -> ${target.pretty}>"
  }

}