package gc.executions

sealed trait Event

case class Spawn(parent: Address, child: Address) extends Event
case class Send() extends Event
case class Receive() extends Event
case class Idle() extends Event
case class SendInfo() extends Event
case class Info() extends Event
case class SendRelease() extends Event
case class Release() extends Event