package gc

import akka.actor.typed

/**
 *
 */
trait Name

case class ActorName(r: typed.ActorRef[Nothing]) extends Name

object ActorName {
  /**
   * Implicit type converter for Akka's ActorRef to ActorName.
   * @param r
   * @return
   */
  implicit def akkaActorRefWrapper(r: typed.ActorRef[Nothing]): ActorName = {
    ActorName(r)
  }
}