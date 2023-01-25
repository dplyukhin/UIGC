package edu.illinois.osl.akka.gc.properties.model

sealed trait Event

/** An actor spawns a child. */
case class Spawn(parent: Name, child: Name, creatorRef: Ref, selfRef: Ref) extends Event {
  override def toString: String = {
    s"SPAWN: $parent spawns $child using $creatorRef"
  }
}

/** An actor sends an application-level message. */
case class Send(sender: Name,
                recipientRef: Ref,
                createdRefs: Iterable[Ref],
                createdUsingRefs: Iterable[Ref]) extends Event {
  override def toString: String = {
    s"SEND: $sender sends a message along $recipientRef containing $createdRefs"
  }
}

/** An actor receives an application-level message */
case class Receive(recipient: Name, sender: Name) extends Event {
  override def toString: String = s"RECV: $recipient receives a message from $sender"
}

/** An actor goes idle. */
case class BecomeIdle(actor: Name) extends Event {
  override def toString: String = s"IDLE: $actor goes idle"
}

/** An actor deactivates references and sends a release message. */
case class Deactivate(actor: Name, ref: Ref) extends Event {
  override def toString: String = s"DEACTIVATE: $actor deactivates $ref"
}

/** An actor takes a snapshot. */
case class Snapshot(actor: Name) extends Event {
  override def toString: String = s"SNAPSHOT: $actor takes a snapshot"
}

/**
 * Drop the next message from `sender` to `recipient`.
 */
case class DroppedMessage(recipient: Name, sender: Name) extends Event {
  override def toString: String = s"DROPPED MESSAGE: next message from $sender to $recipient dropped"
}