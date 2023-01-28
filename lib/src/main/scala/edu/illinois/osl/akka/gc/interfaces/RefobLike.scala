package edu.illinois.osl.akka.gc.interfaces

import scala.annotation.unchecked.uncheckedVariance
import akka.actor.typed.ActorRef
import edu.illinois.osl.akka.gc.proxies.AkkaRef

trait RefobLike[-T] extends Pretty {
  def !(msg: T, refs: Iterable[RefobLike[Nothing]]): Unit

  private[gc] def target: RefLike[Nothing]

  def rawActorRef: ActorRef[Nothing] =
    this.target.asInstanceOf[AkkaRef[Nothing]].ref

  def tell(msg: T, refs: Iterable[RefobLike[Nothing]]): Unit =
    this.!(msg, refs)

  def ![U <: T with Message](msg: U): Unit =
    this.!(msg, msg.refs)

  def unsafeUpcast[U >: T @uncheckedVariance]: RefobLike[U] = 
    this.asInstanceOf[RefobLike[U]]

  def narrow[U <: T]: RefobLike[U] = 
    this
}