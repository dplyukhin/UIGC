package edu.illinois.osl.akka.gc.properties.model

sealed trait ExecMessage

final case class AppMessage(refs: Iterable[DummyRef], travelToken: Option[DummyToken]) extends ExecMessage

final case class ReleaseMessage(releasing: Iterable[DummyRef], created: Iterable[DummyRef]) extends ExecMessage

case object SelfCheck extends ExecMessage