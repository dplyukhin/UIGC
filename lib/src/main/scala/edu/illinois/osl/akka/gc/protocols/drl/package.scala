package edu.illinois.osl.akka.gc.protocols

import edu.illinois.osl.akka.gc.interfaces._

package object drl {
  type Name = RefLike[DRL.GCMessage[Nothing]]
  type Ref = DRL.Refob[Nothing]
}
