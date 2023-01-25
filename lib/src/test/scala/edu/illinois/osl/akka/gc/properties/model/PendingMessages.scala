package edu.illinois.osl.akka.gc.properties.model

import scala.collection.mutable

/**
 * A collection of undelivered messages sent to some actor. These messages
 * have FIFO (rather than causal) semantics.
 */
class PendingMessages {

  private var messagesFrom: Map[DummyName, mutable.Queue[ExecMessage]] = Map()

  /** The collection of actors from which there are undelivered messages */
  def senders: Iterable[DummyName] = messagesFrom.keys

  /** Returns true iff there are any undelivered messages */
  def nonEmpty: Boolean = messagesFrom.nonEmpty

  /** Returns true iff there are no undelivered messages */
  def isEmpty: Boolean = messagesFrom.isEmpty

  def toIterable: Iterable[ExecMessage] = messagesFrom.values.flatMap(_.toIterable)

  def add(message: ExecMessage, sender: DummyName): Unit = {
    val queue = messagesFrom.getOrElse(sender, mutable.Queue())
    queue.enqueue(message)
    messagesFrom += (sender -> queue)
  }

  def deliverFrom(sender: DummyName): ExecMessage = {
    val mailbox = messagesFrom(sender)
    val msg = mailbox.dequeue()
    if (mailbox.isEmpty)
      messagesFrom -= sender
    msg
  }

  override def toString: String = messagesFrom.values.flatten.toString
}
