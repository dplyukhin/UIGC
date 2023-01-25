package edu.illinois.osl.akka.gc.properties

import edu.illinois.osl.akka.gc.protocol
import edu.illinois.osl.akka.gc.proxy
import edu.illinois.osl.akka.gc

package object model {
  case class Name(n: Int) extends proxy.ActorRef[Msg] {
    def !(msg: Msg): Unit = ???
  }
  class Msg extends gc.Message {
    def refs: Iterable[gc.ActorRef[Nothing]] = ???
  }
  trait Behavior {
    def activeRefs: Set[Ref]
    def selfRef: Ref
  }
  class Snapshot

  type Execution = Seq[Event]
  type Ref = protocol.Refob[Msg]
}
