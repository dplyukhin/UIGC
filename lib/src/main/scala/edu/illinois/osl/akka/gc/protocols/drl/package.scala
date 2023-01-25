package edu.illinois.osl.akka.gc.protocols

import edu.illinois.osl.akka.gc
import edu.illinois.osl.akka.gc.proxy

package object drl {
  type Name = proxy.ActorRef[DRL.GCMessage[Nothing]]
  type Ref = DRL.Refob[Nothing]
}
