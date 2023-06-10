package edu.illinois.osl.akka.gc.protocols.monotone

import edu.illinois.osl.akka.gc.interfaces._

case class Refob[-T](
  target: RefLike[GCMessage[T]],
) extends RefobLike[T] {

  var hasChangedThisPeriod: Boolean = false
  var info: Short = RefobInfo.activeRefob

  def resetInfo(): Unit = {
    info = RefobInfo.resetCount(info)
    hasChangedThisPeriod = false
  }

  override def pretty: String = target.pretty

}