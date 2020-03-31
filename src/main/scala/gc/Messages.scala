package gc

import akka.actor.typed.{ActorRef => AkkaActorRef}

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

final case class AppMsg[+T <: Message](payload : T) extends GCMessage[T]

final case class ReleaseMsg[+T <: Message](from: AkkaActorRef[GCMessage[Nothing]], releasing : Iterable[AnyActorRef], created : Iterable[AnyActorRef], sequenceNum: Int) extends GCMessage[T]
final case class AckReleaseMsg[+T <: Message](sequenceNum: Int) extends GCMessage[T]
