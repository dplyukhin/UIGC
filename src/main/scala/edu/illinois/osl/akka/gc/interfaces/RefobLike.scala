package edu.illinois.osl.akka.gc.interfaces

import scala.annotation.unchecked.uncheckedVariance
import akka.actor.typed.ActorRef
import edu.illinois.osl.akka.gc.ActorContext
import edu.illinois.osl.akka.gc.proxies.AkkaRef

trait RefobLike[-T] extends Pretty {
  def !(msg: T, refs: Iterable[RefobLike[Nothing]])(implicit ctx: ActorContext[_]): Unit =
    ctx.sendMessage(this, msg, refs)

  private[gc] def target: RefLike[Nothing]

  def rawActorRef: ActorRef[Nothing] =
    this.target.asInstanceOf[AkkaRef[Nothing]].ref

  def tell(msg: T, refs: Iterable[RefobLike[Nothing]], ctx: ActorContext[_]): Unit =
    this.!(msg, refs)(ctx)

  def ![U <: T with Message](msg: U)(implicit ctx: ActorContext[_]): Unit =
    this.!(msg, msg.refs)

  def unsafeUpcast[U >: T @uncheckedVariance]: RefobLike[U] = 
    this.asInstanceOf[RefobLike[U]]

  def narrow[U <: T]: RefobLike[U] = 
    this
}