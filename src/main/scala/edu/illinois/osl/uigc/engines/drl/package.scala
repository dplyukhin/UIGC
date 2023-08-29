package edu.illinois.osl.uigc.engines

import akka.actor.typed.ActorRef

package object drl {
  type Name = ActorRef[drl.GCMessage[Nothing]]
  type Ref = drl.Refob[Nothing]
}
