package edu.illinois.osl.akka.gc.protocols.monotone

import edu.illinois.osl.akka.gc.interfaces._
import edu.illinois.osl.akka.gc.protocols.monotone.Monotone.Refob

import java.io.{IOException, ObjectInputStream, ObjectOutputStream}
import scala.annotation.unchecked.uncheckedVariance

class Refob[-T](
  var target: RefLike[GCMessage[T]] @uncheckedVariance,
    // This is really a val, so the variance is correct---but we write to it (safely) in the deserializer.
  var targetShadow: Shadow,
) extends RefobLike[T] with Serializable {

  var hasChangedThisPeriod: Boolean = false
  var info: Short = RefobInfo.activeRefob

  def resetInfo(): Unit = {
    info = RefobInfo.resetCount(info)
    hasChangedThisPeriod = false
  }

  override def pretty: String = target.pretty

  // SpawnInfo is serialized by setting the Shadow field to None.
  @throws(classOf[IOException])
  private def writeObject(out: ObjectOutputStream): Unit = {
    out.writeObject(target)
  }

  @throws(classOf[IOException])
  private def readObject(in: ObjectInputStream): Unit = {
    this.target = in.readObject().asInstanceOf[RefLike[GCMessage[T]]]
    this.targetShadow = null
  }
}