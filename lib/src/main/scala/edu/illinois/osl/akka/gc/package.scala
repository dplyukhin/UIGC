package edu.illinois.osl.akka

import edu.illinois.osl.akka.gc.unmanaged
import edu.illinois.osl.akka.gc.protocols.Protocol

package object gc {

  val protocol: Protocol = protocols.drl.DRL

  type ActorRef[-T] = protocol.Refob[T]

  type Behavior[T] = unmanaged.Behavior[protocol.GCMessage[T]]

  type ActorName = unmanaged.ActorRef[Nothing]

  /**
   * A recipe for spawning a garbage-collected actor. Similar to
   * [[AkkaBehavior]], but this recipe can only be used by *GC-aware* actors,
   * i.e. a root actor or another garbage-collected actor.
   */
  type ActorFactory[T] = protocol.SpawnInfo => Behavior[T]

  object unmanaged {
    import akka.actor.typed
    import akka.actor.typed.scaladsl
    type ActorRef[-T] = typed.ActorRef[T]
    type Behavior[T] = typed.Behavior[T]
    type ActorContext[T] = scaladsl.ActorContext[T]
  }
}
