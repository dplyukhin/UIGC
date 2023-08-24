package edu.illinois.osl.uigc.protocols

import akka.actor.typed.ActorRef
import akka.actor.typed.scaladsl.ActorContext
import edu.illinois.osl.uigc.interfaces._

package object drl {
  type Name = ActorRef[DRL.GCMessage[Nothing]]
  type Ref = DRL.Refob[Nothing]
}
