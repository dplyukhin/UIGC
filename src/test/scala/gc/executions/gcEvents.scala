package gc.executions

sealed trait Event

/** An actor spawns a child. */
case class Spawn(parent: DummyName, child: DummyName) extends Event {
  override def toString: String = {
    s"SPAWN: $parent spawns $child"
  }
}

/** An actor sends an application-level message. */
case class Send(sender: DummyName,
                recipient: DummyName,
                message: AppMessage) extends Event {
  override def toString: String = {
    s"SEND: $sender sends $recipient a message along token ${message.travelToken} with refs ${message.refs}"
  }
}

/** An actor receives an application-level message */
case class Receive(recipient: DummyName) extends Event {
  override def toString: String = s"RECV: $recipient receives a message"
}

/** An actor creates a reference to the actor targeted by [[refToTarget]] for the actor targeted by [[refToOwner]]. */
case class CreateRef(actor: DummyName,
                     refToOwner: DummyRef,
                     refToTarget: DummyRef,
                     newToken: DummyToken) extends Event {
  override def toString: String = s"CREATEREF: $actor creates a ref for $refToOwner pointing to $refToOwner with token $newToken"
}

/** An actor goes idle. */
case class Idle(actor: DummyName) extends Event {
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