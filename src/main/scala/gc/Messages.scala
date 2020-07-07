package gc

import akka.actor.typed.{ActorRef => AkkaActorRef}

/**
 * An interface that all messages sent to a garbage-collected actor must adhere to.
 */
trait Message {
  /**
   * This method must return all the references contained in the message.
   */
  def refs: Iterable[AnyRefOb]
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
final case class AppMsg[+T <: Message](payload: T, token: Option[Token]) extends GCMessage[T]

/**
 *
 * @param from
 * @param releasing
 * @param created
 * @tparam T
 */
final case class ReleaseMsg[+T <: Message](from: AkkaActorRef[GCMessage[Nothing]],
                                           releasing: Iterable[AnyRefOb],
                                           created: Iterable[AnyRefOb],
                                           ) extends GCMessage[T]

/**
 * A message sent by an actor to itself to check whether it's ready to terminate.
 */
final case class SelfCheck[+T <: Message]() extends GCMessage[T]