import akka.actor.typed.{ActorRef => AkkaActorRef, Behavior => AkkaBehavior}

package object gc {

  /**
   * A behavior that can handle the GC protocol.
   */
  type Behavior[T <: Message] = AkkaBehavior[GCMessage[T]]

  /**
   * A factory that can be passed to `LocalGC.spawn` to create a garbage-collected actor.
   */
  type ActorFactory[T <: Message] = (AkkaActorRef[Nothing], Token) => Behavior[T]

  /**
   * An alias for any vanilla actor ref.
   */
  type AnyName = AkkaActorRef[Nothing]

  /**
   * An alias for the GC actor ref to help differentiate it from Akka's.
   */
  type RefOb[T] = ActorRef[T]

  /**
   * A type representing any kind of ActorRef.
   */
  type AnyRefOb = ActorRef[Nothing]
}
