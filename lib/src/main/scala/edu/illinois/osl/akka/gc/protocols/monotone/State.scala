package edu.illinois.osl.akka.gc.protocols.monotone

import akka.actor.typed.{PostStop, Terminated, Signal}
import scala.collection.mutable
import edu.illinois.osl.akka.gc.protocols.Protocol
import edu.illinois.osl.akka.gc.interfaces.Pretty
import edu.illinois.osl.akka.gc.interfaces.Pretty._

class State extends Pretty {

  /** A sequence number used for generating unique tokens */
  var count: Int = 0
  /** This actor's refob to itself */
  var selfRef: Ref = _
  /** Tracks references created by this actor */
  val created: mutable.ArrayBuffer[Ref] = mutable.ArrayBuffer()
  /** Tracks all the refs that have been active during this entry period */
  val refs: mutable.HashSet[Ref] = mutable.HashSet()
  /** Tracks how many messages are received using each reference. */
  val recvCount: mutable.HashMap[Token, Int] = mutable.HashMap()

  override def pretty: String =
    s"""STATE:
      < count:        $count
        created refs: ${created.pretty}
        refs:         ${refs.pretty}
        recv counts:  ${recvCount.pretty}
      >
      """
}