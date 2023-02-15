package edu.illinois.osl.akka.gc.proxies

import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.ActorContext
import akka.dispatch.MessageQueue
import edu.illinois.osl.akka.gc.interfaces._

case class AkkaContext[T](ctx: ActorContext[T]) extends ContextLike[T] {
  override def self: AkkaRef[T] = AkkaRef(ctx.self)
  override def anyChildren: Boolean = ctx.children.nonEmpty
  override def watch[U](other: RefLike[U]): Unit = ctx.watch(other.asInstanceOf[AkkaRef[U]].ref)
  override def hasMessages: Boolean = queue.hasMessages
  def system: ActorSystem[Nothing] = ctx.system
  def queue: MessageQueue = ctx.queue
}