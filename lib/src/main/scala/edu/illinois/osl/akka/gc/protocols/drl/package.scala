package edu.illinois.osl.akka.gc.protocols

import edu.illinois.osl.akka.gc
import edu.illinois.osl.akka.gc.raw

package object drl {
  type Name = raw.ActorRef[DRL.GCMessage[Nothing]]
  type Ref = DRL.Refob[Nothing]
}
