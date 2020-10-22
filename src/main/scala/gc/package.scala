import akka.actor.typed
import akka.actor.typed.{ActorRef => AkkaActorRef, Behavior => AkkaBehavior}

package object gc {

  /**
   * A behavior that can handle the GC protocol.
   */
  type Behavior[T <: Message] = AkkaBehavior[GCMessage[T]]

  /** The unique identifier of an actor that supports garbage collection */
  type ActorName = typed.ActorRef[GCMessage[Nothing]]

  /**
   * A factory that can be passed to `LocalGC.spawn` to create a garbage-collected actor.
   */
  type ActorFactory[T <: Message] = (ActorName, Token) => Behavior[T]

  /**
   * A type representing any kind of ActorRef.
   */
  type AnyActorRef = ActorRef[Nothing]
}
