package edu.illinois.osl.uigc.interfaces

import akka.actor.typed.ActorRef
import edu.illinois.osl.uigc.ActorContext

import scala.annotation.unchecked.uncheckedVariance

/** In Akka, two actors on the same ActorSystem can share the same [[akka.actor.typed.ActorRef]]. In
  * most actor GCs, it's important that actors do not share references. It's therefore useful to
  * create a wrapper for ActorRefs. In UIGC, this wrapper is called a [[Refob]] (short for
  * "reference object").
  *
  * @tparam T
  *   The type of application messages handled by this actor
  */
trait Refob[-T] {
  def !(msg: T, refs: Iterable[Refob[Nothing]])(implicit ctx: ActorContext[_]): Unit =
    ctx.engine.sendMessage(this, msg, refs, ctx.state, ctx.typedContext)

  private[uigc] def target: ActorRef[Nothing]

  def typedActorRef: ActorRef[Nothing] =
    this.target

  def tell(msg: T, refs: Iterable[Refob[Nothing]], ctx: ActorContext[_]): Unit =
    this.!(msg, refs)(ctx)

  def ![U <: T with Message](msg: U)(implicit ctx: ActorContext[_]): Unit =
    this.!(msg, msg.refs)

  def unsafeUpcast[U >: T @uncheckedVariance]: Refob[U] =
    this.asInstanceOf[Refob[U]]

  def narrow[U <: T]: Refob[U] =
    this
}
