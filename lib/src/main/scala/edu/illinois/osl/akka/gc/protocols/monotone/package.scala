package edu.illinois.osl.akka.gc.protocols

import edu.illinois.osl.akka.gc.interfaces._

package object monotone {
  type RefobStatus = Integer
  type RefobInfo = Short
  type Name = RefLike[Monotone.GCMessage[Nothing]]
  type Ref = Monotone.Refob[Nothing]
}
