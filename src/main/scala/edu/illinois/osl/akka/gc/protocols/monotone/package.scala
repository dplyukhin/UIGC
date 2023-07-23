package edu.illinois.osl.akka.gc.protocols

import edu.illinois.osl.akka.gc.interfaces._
import akka.actor.typed.ActorRef
import akka.actor.typed.scaladsl.ActorContext

package object monotone {
  type Name = ActorRef[Monotone.GCMessage[Nothing]]
  type Ref = Monotone.Refob[Nothing]
}
