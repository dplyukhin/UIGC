package edu.illinois.osl.uigc.protocols

import edu.illinois.osl.uigc.interfaces._
import akka.actor.typed.ActorRef
import akka.actor.typed.scaladsl.ActorContext

package object monotone {
  type Name = ActorRef[Monotone.GCMessage[Nothing]]
  type Ref = Monotone.Refob[Nothing]
}
