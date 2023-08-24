package edu.illinois.osl.uigc.interfaces

import scala.annotation.unchecked.uncheckedVariance
import akka.actor.typed.ActorRef
import edu.illinois.osl.uigc.ActorContext

trait RefobLike[-T] extends Pretty {
  def !(msg: T, refs: Iterable[RefobLike[Nothing]])(implicit ctx: ActorContext[_]): Unit =
    ctx.sendMessage(this, msg, refs)

  private[uigc] def target: ActorRef[Nothing]

  def rawActorRef: ActorRef[Nothing] =
    this.target

  def tell(msg: T, refs: Iterable[RefobLike[Nothing]], ctx: ActorContext[_]): Unit =
    this.!(msg, refs)(ctx)

  def ![U <: T with Message](msg: U)(implicit ctx: ActorContext[_]): Unit =
    this.!(msg, msg.refs)

  def unsafeUpcast[U >: T @uncheckedVariance]: RefobLike[U] = 
    this.asInstanceOf[RefobLike[U]]

  def narrow[U <: T]: RefobLike[U] = 
    this
}
