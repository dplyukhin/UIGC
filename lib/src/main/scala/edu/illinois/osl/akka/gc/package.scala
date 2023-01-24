package edu.illinois.osl.akka

import akka.actor.typed
import akka.actor.typed.{ActorRef => AkkaActorRef, Behavior => AkkaBehavior}

package object gc {
  /**
   * An interface that all messages sent to a garbage-collected actor must adhere to.
   */
  trait Message {
    /**
     * This method must return all the references contained in the message.
     */
    def refs: Iterable[AnyActorRef]
  }

  val protocol: Protocol = DRL

  /**
   * A behavior that can handle the GC protocol.
   */
  type Behavior[T <: Message] = AkkaBehavior[protocol.GCMessage[T]]

  /** The unique identifier of an actor that supports garbage collection */
  type ActorName = typed.ActorRef[protocol.GCMessage[Nothing]]

  /**
   * A factory that can be passed to `LocalGC.spawn` to create a garbage-collected actor.
   */
  type ActorFactory[T <: Message] = protocol.SpawnInfo => Behavior[T]

  /**
   * A type representing any kind of ActorRef.
   */
  type AnyActorRef = protocol.ActorRef[Nothing]
}
