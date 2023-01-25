package edu.illinois.osl.akka

package object gc {

  val protocol: Protocol = protocols.NoProtocol

  /**
   * All messages to garbage-collected actors must implement this interface.
   * It allows the GC middleware to find references inside the message and
   * take necessary action accordingly.
   */
  trait Message {
    def refs: Iterable[ActorRef[Nothing]]
  }

  /**
   * Helper trait to indicate that a message doesn't contain any references.
   */
  trait NoRefs extends Message {
    override def refs: Iterable[ActorRef[Nothing]] = Nil
  }

  type ActorRef[-T <: Message] = protocol.Refob[T]

  type Behavior[T <: Message] = raw.Behavior[protocol.GCMessage[T]]

  type ActorName = raw.ActorRef[Nothing]

  /**
   * A recipe for spawning a garbage-collected actor. Similar to
   * [[AkkaBehavior]], but this recipe can only be used by *GC-aware* actors,
   * i.e. a root actor or another garbage-collected actor.
   */
  type ActorFactory[T <: Message] = protocol.SpawnInfo => Behavior[T]

  object proxy {
    import akka.actor.typed
    import akka.actor.typed.scaladsl
    import scala.annotation.unchecked.uncheckedVariance

    trait ActorRef[-T] {
      def !(msg: T): Unit
      def narrow[U <: T]: ActorRef[U]
      def unsafeUpcast[U >: T @uncheckedVariance]: ActorRef[U]
    }

    trait ActorContext[T] {
      def self: ActorRef[T]
      def children: Iterable[ActorRef[Nothing]]
      def watch[U](other: ActorRef[U]): Unit
    }

    case class ProxyRef[-T](ref: typed.ActorRef[T]) extends ActorRef[T] {
      override def !(msg: T): Unit = ref ! msg
      override def narrow[U <: T]: ProxyRef[U] = ProxyRef(ref.narrow[U])
      override def unsafeUpcast[U >: T @uncheckedVariance]: ProxyRef[U] = ProxyRef(ref.unsafeUpcast[U])
    }

    case class ProxyContext[T](ctx: scaladsl.ActorContext[T]) extends ActorContext[T] {
      override def self: ProxyRef[T] = ProxyRef(ctx.self)
      override def children: Iterable[ProxyRef[Nothing]] = LazyList.from(ctx.children).map(ProxyRef[Nothing](_))
      override def watch[U](other: ActorRef[U]): Unit = ctx.watch(other.asInstanceOf[ProxyRef[U]].ref)
    }
  }

  object raw {
    import akka.actor.typed
    import akka.actor.typed.scaladsl
    type Ref = typed.ActorRef[Nothing]
    type ActorRef[-T] = typed.ActorRef[T]
    type Behavior[T] = typed.Behavior[T]
    type ActorContext[T] = scaladsl.ActorContext[T]
    type AbstractBehavior[T] = scaladsl.AbstractBehavior[T]
    val Behaviors = scaladsl.Behaviors
  }
}
