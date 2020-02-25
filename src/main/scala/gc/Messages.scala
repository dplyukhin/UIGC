package gc

/**
 * An interface that all messages sent to a garbage-collected actor must adhere to.
 */
trait Message {
  /**
   * This method must return all the references contained in the message.
   */
  def refs: Seq[ActorRef[Nothing]]
}


sealed trait GCMessage[T <: Message]

final case class AppMsg[T <: Message](payload : T) extends GCMessage[T]

final case class ReleaseMsg[T <: Message](releasing : Seq[ActorRef[T]], created : Seq[ActorRef[T]]) extends GCMessage[T]
