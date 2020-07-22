package gc.executions

sealed trait Event

/** An actor spawns a child. */
case class Spawn(parent: DummyName) extends Event

/** An actor sends an application-level message. */
case class Send[T <: Message](sender: DummyName,
                              recipient: DummyName,
                              message: AppMessage[T]) extends Event

/** An actor receives an application-level message */
case class Receive(recipient: DummyName) extends Event

/** An actor goes idle. */
case class Idle(actor: DummyName) extends Event

/** An actor deactivates references and sends a release message. */
case class SendRelease(actor: DummyName, refs: Iterable[DummyRef]) extends Event

/** An actor receives and handles a release message. */
case class Release(actor: DummyName,
                   releasing: Iterable[DummyRef],
                   created: Iterable[DummyRef]) extends Event

/** An actor takes a snapshot. */
case class Snapshot(actor: DummyName) extends Event