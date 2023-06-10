package edu.illinois.osl.akka.gc.properties.model

import edu.illinois.osl.akka.gc.interfaces.Pretty

sealed trait Event extends Pretty

/** An actor spawns a child. */
case class Spawn(parent: Name) extends Event {
  override def pretty: String = {
    s"SPAWN: ${parent.pretty} spawns an actor"
  }
}

/** An actor sends an application-level message. */
case class Send(sender: Name,
                recipientRef: Ref,
                targetRefs: Iterable[Ref]) extends Event {
  override def pretty: String = {
    s"SEND: ${sender.pretty} sends a message along ${recipientRef.pretty} containing refs to ${targetRefs.toList.pretty}"
  }
}

/** An actor receives an application-level message */
case class Receive(recipient: Name, msg: Msg) extends Event {
  override def pretty: String = s"RECV: ${recipient.pretty} receives ${msg.pretty}"
}

/** An actor goes idle. */
case class BecomeIdle(actor: Name) extends Event {
  override def pretty: String = s"IDLE: ${actor.pretty} goes idle"
}

/** An actor deactivates references. */
case class Deactivate(actor: Name, ref: Ref) extends Event {
  override def pretty: String = s"DEACTIVATE: ${actor.pretty} deactivates ${ref.pretty}"
}

/** An actor takes a snapshot. */
case class Snapshot(actor: Name) extends Event {
  override def pretty: String = s"SNAPSHOT: ${actor.pretty} takes a snapshot"
}

/**
 * Drop the next message from `sender` to `recipient`.
 */
case class DroppedMessage(recipient: Name, msg: Msg) extends Event {
  override def pretty: String = s"DROPPED MESSAGE: message ${msg.pretty}, sent to ${recipient.pretty}, dropped"
}