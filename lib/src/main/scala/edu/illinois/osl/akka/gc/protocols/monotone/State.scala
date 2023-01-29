package edu.illinois.osl.akka.gc.protocols.monotone

import akka.actor.typed.{PostStop, Terminated, Signal}
import scala.collection.mutable
import edu.illinois.osl.akka.gc.protocols.Protocol
import edu.illinois.osl.akka.gc.interfaces.Pretty
import edu.illinois.osl.akka.gc.interfaces.Pretty._

object State {
  sealed trait Fact extends Pretty
  case class Created(ref: Ref) extends Fact {
    def pretty: String = s"Created(${ref.pretty})"
  }
  case class Activated(ref: Ref) extends Fact {
    def pretty: String = s"Activated(${ref.pretty})"
  }
  case class Deactivated(ref: Ref) extends Fact {
    def pretty: String = s"Deactivated(${ref.pretty})"
  }
}

class State extends Pretty {
  import State._

  /** A sequence number used for generating unique tokens */
  var count: Int = 0
  /** This actor's refob to itself */
  var selfRef: Ref = _
  /** Tracks references created, activated, and deactivated by this actor */
  val refs: mutable.ArrayBuffer[Fact] = mutable.ArrayBuffer()
  /** Tracks how many messages are sent using each reference. */
  val sentCount: mutable.HashMap[Token, Int] = mutable.HashMap()
  /** Tracks how many messages are received using each reference. */
  val recvCount: mutable.HashMap[Token, Int] = mutable.HashMap()

  override def pretty: String =
    s"""STATE:
      < refs:        ${refs.pretty}
        sent counts: ${prettifyMap(sentCount).pretty}
        recv counts: ${recvCount.pretty}
      >
      """
}