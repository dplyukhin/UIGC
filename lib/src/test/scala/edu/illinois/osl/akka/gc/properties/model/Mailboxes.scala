package edu.illinois.osl.akka.gc.properties.model

import scala.collection.mutable

trait Mailbox[T] {
  def add(message: T, sender: Name): Unit
  def nonEmpty: Boolean
  def isEmpty: Boolean
  def toIterable: Iterable[T]
  /** The set of messages that can be delivered next */
  def next: Iterable[T]
}

/**
 * A collection of undelivered messages sent to some actor. These messages
 * have FIFO (rather than causal) semantics.
 */
class FIFOMailbox[T] extends Mailbox[T] {

  private var messagesFrom: Map[Name, mutable.Queue[T]] = Map()

  /** The collection of actors from which there are undelivered messages */
  def senders: Iterable[Name] = messagesFrom.keys

  /** Returns true iff there are any undelivered messages */
  def nonEmpty: Boolean = messagesFrom.nonEmpty

  /** Returns true iff there are no undelivered messages */
  def isEmpty: Boolean = messagesFrom.isEmpty

  def toIterable: Iterable[T] = messagesFrom.values.flatMap(_.toIterable)

  def next: Iterable[T] =
    for {
        sender <- messagesFrom.keys;
        if messagesFrom(sender).nonEmpty
    } yield messagesFrom(sender).front

  def add(message: T, sender: Name): Unit = {
    val queue = messagesFrom.getOrElse(sender, mutable.Queue())
    queue.enqueue(message)
    messagesFrom += (sender -> queue)
  }

  def deliverFrom(sender: Name): T = {
    val mailbox = messagesFrom(sender)
    val msg = mailbox.dequeue()
    if (mailbox.isEmpty)
      messagesFrom -= sender
    msg
  }

  override def toString: String = messagesFrom.values.flatten.toString
}
