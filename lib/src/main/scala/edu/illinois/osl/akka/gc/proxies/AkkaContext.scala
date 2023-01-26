package edu.illinois.osl.akka.gc.proxies

import akka.actor.typed.scaladsl.ActorContext
import edu.illinois.osl.akka.gc.interfaces._

case class AkkaContext[T](ctx: ActorContext[T]) extends ContextLike[T] {
  override def self: AkkaRef[T] = AkkaRef(ctx.self)
  override def children: Iterable[AkkaRef[Nothing]] = LazyList.from(ctx.children).map(AkkaRef[Nothing](_))
  override def watch[U](other: RefLike[U]): Unit = ctx.watch(other.asInstanceOf[AkkaRef[U]].ref)
}