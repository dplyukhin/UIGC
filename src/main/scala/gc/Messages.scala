package gc

/**
 * An interface that all messages sent to a garbage-collected actor must adhere to.
 */
trait Message {
  /**
   * This method must return all the references contained in the message.
   */
  def refs: Iterable[AnyActorRef]
}

sealed trait GCMessage[+T <: Message]

final case class AppMsg[+T <: Message](payload: T, token: Option[Token]) extends GCMessage[T]

final case class ReleaseMsg[+T <: Message](releasing: Iterable[AnyActorRef],
                                           created: Iterable[AnyActorRef],
                                           ) extends GCMessage[T]

/**
 * A message asking its recipient to take a snapshot.
 */
case object TakeSnapshot extends GCMessage[Nothing]

/**
 * A message sent by an actor to itself to check whether it's ready to terminate.
 */
case object SelfCheck extends GCMessage[Nothing]