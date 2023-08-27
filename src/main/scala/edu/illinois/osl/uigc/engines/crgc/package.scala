package edu.illinois.osl.uigc.engines

import edu.illinois.osl.uigc.interfaces._
import akka.actor.typed.ActorRef
import akka.actor.typed.scaladsl.ActorContext

package object crgc {
  type Name = ActorRef[CRGC.GCMessage[Nothing]]
  type Ref = CRGC.Refob[Nothing]
}
