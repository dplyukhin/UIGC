package edu.illinois.osl.uigc.engines.crgc

import akka.actor.typed.ActorRef
import edu.illinois.osl.uigc.interfaces

import java.io.{IOException, ObjectInputStream, ObjectOutputStream}
import scala.annotation.unchecked.uncheckedVariance

class Refob[-T](
    var target: ActorRef[GCMessage[T]] @uncheckedVariance,
    // This is really a val, so the variance is correct---but we write to it (safely) in the deserializer.
    @volatile var targetShadow: Shadow
    // This field is read by actors that create new refobs and written-to by
    // the GC. Adding @volatile makes it more likely that the parent actor will
    // get the GC's version of the shadow. But it's also okay if the parent actor
    // reads a stale value of this field. We can remove @volatile if it worsens
    // performance.
) extends interfaces.Refob[T]
    with Serializable {

  private var _hasBeenRecorded: Boolean = false
  private var _info: Short = RefobInfo.activeRefob

  def info: Short = _info

  def hasBeenRecorded: Boolean = _hasBeenRecorded

  def setHasBeenRecorded(): Unit = {
    _hasBeenRecorded = true
  }

  def deactivate(): Unit = {
    _info = RefobInfo.deactivate(_info)
  }

  def incSendCount(): Unit = {
    _info = RefobInfo.incSendCount(_info)
  }

  def canIncSendCount: Boolean = {
    RefobInfo.canIncrement(_info)
  }

  def reset(): Unit = {
    _info = RefobInfo.resetCount(_info)
    _hasBeenRecorded = false
  }

  override def equals(that: Any): Boolean =
    that match {
      case that: Refob[_] => this.target == that.target
      case _              => false
    }

  override def hashCode(): Int = target.hashCode()

  // SpawnInfo is serialized by setting the Shadow field to None.
  @throws(classOf[IOException])
  private def writeObject(out: ObjectOutputStream): Unit =
    out.writeObject(target)

  @throws(classOf[IOException])
  private def readObject(in: ObjectInputStream): Unit = {
    this.target = in.readObject().asInstanceOf[ActorRef[GCMessage[T]]]
    this.targetShadow = null
  }

  override def typedActorRef: ActorRef[interfaces.GCMessage[T]] =
    target.asInstanceOf[ActorRef[interfaces.GCMessage[T]]]
}
