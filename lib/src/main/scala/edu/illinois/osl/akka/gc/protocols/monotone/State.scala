package edu.illinois.osl.akka.gc.protocols.monotone

import akka.actor.typed.{PostStop, Terminated, Signal}
import scala.collection.mutable
import edu.illinois.osl.akka.gc.protocols.Protocol
import edu.illinois.osl.akka.gc.interfaces.Pretty
import edu.illinois.osl.akka.gc.interfaces.Pretty._

// TODO move this to Java to avoid the wrapper
// TODO use short instead
object State {
  /**
   * RefobInfo consists of a message send count and a status bit indicating
   * whether the actor is deactivated. This is packed into a single integer 
   * whose most significant bit is on iff the refob has been deactivated.
   */
  type RefobInfo = Short
}

class State extends Pretty {
  import State._

  /** A sequence number used for generating unique tokens */
  var count: Int = 0
  /** This actor's refob to itself */
  var selfRef: Ref = _
  /** Tracks references created, activated, and deactivated by this actor */
  val created: mutable.ArrayBuffer[Ref] = mutable.ArrayBuffer()
  /** Tracks how many messages are sent using each reference. */
  val refs: mutable.HashMap[Token, RefobInfo] = mutable.HashMap()
  /** Tracks how many messages are received using each reference. */
  val recvCount: mutable.HashMap[Token, Int] = mutable.HashMap()

  override def pretty: String =
    s"""STATE:
      < count:        $count
        created refs: ${created.pretty}
        refs:         ${prettifyMap(refs).pretty}
        recv counts:  ${recvCount.pretty}
      >
      """
}