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

/**
 *
 * @tparam T
 */
sealed trait GCMessage[+T <: Message]

/**
 *
 * @param payload
 * @param token
 * @tparam T
 */
final case class AppMsg[+T <: Message](payload : T, token : Token) extends GCMessage[T]

/**
 *
 * @param from
 * @param releasing
 * @param created
 * @param sequenceNum
 * @tparam T
 */
final case class ReleaseMsg[+T <: Message](from: AkkaActorRef[GCMessage[Nothing]], releasing : Iterable[AnyActorRef], created : Iterable[AnyActorRef], sequenceNum: Int) extends GCMessage[T]

/**
 *
 * @param sequenceNum
 * @tparam T
 */
final case class AckReleaseMsg[+T <: Message](sequenceNum: Int) extends GCMessage[T]
