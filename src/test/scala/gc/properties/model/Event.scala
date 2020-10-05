package gc.properties.model

sealed trait Event

/** An actor spawns a child. */
case class Spawn(parent: DummyName, child: DummyName, creatorRef: DummyRef, selfRef: DummyRef) extends Event {
  override def toString: String = {
    s"SPAWN: $parent spawns $child using $creatorRef"
  }
}

/** An actor sends an application-level message. */
case class Send(sender: DummyName,
                recipientRef: DummyRef,
                createdRefs: Iterable[DummyRef],
                createdUsingRefs: Iterable[DummyRef]) extends Event {
  override def toString: String = {
    s"SEND: $sender sends a message along $recipientRef containing $createdRefs"
  }
}

/** An actor receives an application-level message */
case class Receive(recipient: DummyName, sender: DummyName) extends Event {
  override def toString: String = s"RECV: $recipient receives a message from $sender"
}

/** An actor goes idle. */
case class BecomeIdle(actor: DummyName) extends Event {
  override def toString: String = s"IDLE: $actor goes idle"
}

/** An actor deactivates references and sends a release message. */
case class Deactivate(actor: DummyName, ref: DummyRef) extends Event {
  override def toString: String = s"DEACTIVATE: $actor deactivates $ref"
}

/** An actor takes a snapshot. */
case class Snapshot(actor: DummyName) extends Event {
  override def toString: String = s"SNAPSHOT: $actor takes a snapshot"
}