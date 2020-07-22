package gc.executions

trait Message {
  def refs: Iterable[DummyRef]
}

sealed trait ExecMessage[+T <: Message]

final case class AppMessage[+T <: Message](payload: T, travelToken: Option[DummyToken]) extends ExecMessage[T]

final case class ReleaseMessage[+T <: Message](releasing: Iterable[DummyRef], created: Iterable[DummyRef]) extends ExecMessage[T]
