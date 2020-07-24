package gc.executions

sealed trait ExecMessage

final case class AppMessage(refs: Iterable[DummyRef], travelToken: Option[DummyToken]) extends ExecMessage

final case class ReleaseMessage(releasing: Iterable[DummyRef], created: Iterable[DummyRef]) extends ExecMessage
