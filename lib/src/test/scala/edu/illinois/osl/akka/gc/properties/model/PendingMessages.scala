package edu.illinois.osl.akka.gc.properties.model

import scala.collection.mutable

/**
 * A collection of undelivered messages sent to some actor. These messages
 * have FIFO (rather than causal) semantics.
 */
class FIFOMailbox {

  private var messagesFrom: Map[Name, mutable.Queue[Msg]] = Map()

  /** The collection of actors from which there are undelivered messages */
  def senders: Iterable[Name] = messagesFrom.keys

  /** Returns true iff there are any undelivered messages */
  def nonEmpty: Boolean = messagesFrom.nonEmpty

  /** Returns true iff there are no undelivered messages */
  def isEmpty: Boolean = messagesFrom.isEmpty

  def toIterable: Iterable[Msg] = messagesFrom.values.flatMap(_.toIterable)

  def add(message: Msg, sender: Name): Unit = {
    val queue = messagesFrom.getOrElse(sender, mutable.Queue())
    queue.enqueue(message)
    messagesFrom += (sender -> queue)
  }

  def deliverFrom(sender: Name): Msg = {
    val mailbox = messagesFrom(sender)
    val msg = mailbox.dequeue()
    if (mailbox.isEmpty)
      messagesFrom -= sender
    msg
  }

  override def toString: String = messagesFrom.values.flatten.toString
}
