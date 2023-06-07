package edu.illinois.osl.akka.gc.protocols.monotone

import edu.illinois.osl.akka.gc.interfaces._

/**
 * An opaque and globally unique token.
 */
class Token(val creator: Name, val seqnum: Int, val targetShadow: Shadow) extends Pretty {
  override def equals(obj: Any): Boolean = {
    obj match {
      case other: Token => this.creator == other.creator && this.seqnum == other.seqnum
    }
  }
  def pretty: String = s"Token#${Math.floorMod(this.hashCode(), 1000)}"
}

case class Refob[-T](
  token: Option[Token],
  owner: Option[RefLike[GCMessage[Nothing]]],
  target: RefLike[GCMessage[T]],
) extends RefobLike[T] {

  var hasChangedThisPeriod: Boolean = false
  var info: Short = RefobInfo.activeRefob

  def resetInfo(): Unit = {
    info = RefobInfo.resetCount(info)
    hasChangedThisPeriod = false
  }

  override def pretty: String = {
    f"<Refob#${Math.floorMod(token.hashCode(), 1000)}: ${owner.get.pretty} -> ${target.pretty}>"
  }

}