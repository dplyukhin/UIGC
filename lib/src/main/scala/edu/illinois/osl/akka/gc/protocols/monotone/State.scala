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
  var created: mutable.ArrayBuffer[Ref] = mutable.ArrayBuffer()
  /** Tracks all the refs that have been active during this entry period */
  val refs: mutable.HashSet[Ref] = mutable.HashSet()
  /** Tracks how many messages are received using each reference. */
  var recvCount: mutable.HashMap[Token, Int] = mutable.HashMap()

  def finalizeEntry(): Unit = {
    val _created = this.created 
    this.created = mutable.ArrayBuffer()
    val _recvCount = this.recvCount
    this.recvCount = mutable.HashMap()

    // Get the RefobStatus of every ref
    val refInfos = new Array[Short](refs.size)
    var i = 0;
    val it = refs.iterator
    while (it.hasNext) {
      val ref = it.next()
      val info = ref.info
      refInfos(i) = info
      i += 1
      if (!RefobInfo.isActive(info)) {
        refs.remove(ref)
      }
    }
  }

  override def pretty: String =
    s"""STATE:
      < count:        $count
        created refs: ${created.pretty}
        refs:         ${refs.pretty}
        recv counts:  ${recvCount.pretty}
      >
      """
}