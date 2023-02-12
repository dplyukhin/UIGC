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

/**
 * A version of [[AkkaActorRef]] used to send messages to actors with GC enabled. It
 * should only be used by the *owner* to send messages to the *target.
 *
 * @param token A token that uniquely identifies this reference. If None, it represents an external actor.
 * @param owner The [[AkkaActorRef]] of the only actor that can use this reference. If None, it represents an external actor.
 * @param target The [[AkkaActorRef]] of the actor that will receive messages.
 * @tparam T The type of messages handled by the target actor. Must implement the [[Message]] interface.
 */
case class Refob[-T](
  token: Option[Token],
  owner: Option[RefLike[GCMessage[Nothing]]],
  target: RefLike[GCMessage[T]],
) extends RefobLike[T] {

  var state: State = _
  var ctx: ContextLike[GCMessage[_]] = _
  var hasChangedThisPeriod: Boolean = false
  var info: Short = RefobInfo.activeRefob

  def resetInfo(): Unit = {
    info = RefobInfo.resetCount(info)
    hasChangedThisPeriod = false
  }
  
  def initialize[T](state: State, ctx: ContextLike[GCMessage[T]]): Unit = {
    this.state = state
    this.ctx = ctx.asInstanceOf[ContextLike[GCMessage[_]]]
  }

  override def !(msg: T, refs: Iterable[RefobLike[Nothing]]): Unit = {
    Monotone.onSend(this, this.state, this.ctx)
    target ! AppMsg(msg, token, refs.asInstanceOf[Iterable[Refob[Nothing]]])
  }

  override def pretty: String = {
    f"<Refob#${Math.floorMod(token.hashCode(), 1000)}: ${owner.get.pretty} -> ${target.pretty}>"
  }

}