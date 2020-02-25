package gc

import akka.actor.typed.{Behavior => AkkaBehavior}
import akka.actor.typed.scaladsl.{AbstractBehavior => AkkaAbstractBehavior, Behaviors => AkkaBehaviors}

/**
 * Parent class for behaviors that implement the GC message protocol.
 *
 * Unlike [[AkkaAbstractBehavior]], child classes of [[AbstractBehavior]] must implement
 * [[processMessage]].
 */
abstract class AbstractBehavior[T](context: ActorContext[T])
  extends AkkaAbstractBehavior[GCMessage[T]](context.context) {

  private def addRefs(payload : Seq[ActorRef[Nothing]]) : Unit

  private def handleRelease(releasing : Seq[ActorRef[T]], created : Seq[ActorRef[T]]) : Unit

  def onMessage(msg : T) : Behavior[T]

  final def onMessage(msg : GCMessage[T]) : AkkaBehavior[GCMessage[T]] =
    msg match {
      case ReleaseMsg(releasing, created) =>
        handleRelease(releasing, created)
        AkkaBehaviors.same
      case AppMsg(payload : Message) =>
        addRefs(payload.refs)
        onMessage(payload)
    }
}
