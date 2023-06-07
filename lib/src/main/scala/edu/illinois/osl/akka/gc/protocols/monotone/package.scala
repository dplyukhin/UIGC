package edu.illinois.osl.akka.gc.protocols

import edu.illinois.osl.akka.gc.interfaces._

package object monotone {
  type Name = RefLike[Monotone.GCMessage[Nothing]]
  type Ref = Monotone.Refob[Nothing]
}
