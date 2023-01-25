package edu.illinois.osl.akka.gc.protocols

import edu.illinois.osl.akka.gc
import akka.actor.typed
import akka.actor.typed.{ActorRef => AkkaActorRef, Behavior => AkkaBehavior}

package object drl {
  type Behavior[T <: gc.Message] = AkkaBehavior[DRL.GCMessage[T]]

  type Name = AkkaActorRef[DRL.GCMessage[Nothing]]

  type Ref = DRL.ActorRef[Nothing]
}
