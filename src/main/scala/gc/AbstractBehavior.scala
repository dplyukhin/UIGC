package gc

import akka.actor.typed.{Behavior => AkkaBehavior}
import akka.actor.typed.scaladsl.{AbstractBehavior => AkkaAbstractBehavior, Behaviors => AkkaBehaviors}


/**
 * Parent class for behaviors that implement the GC message protocol.
 *
 * Unlike [[AkkaAbstractBehavior]], child classes of [[AbstractBehavior]] must implement
 * [[processMessage]].
 */
abstract class AbstractBehavior[T <: Message](context: ActorContext[T])
  extends AkkaAbstractBehavior[GCMessage[T]](context.context) {

  def onMessage(msg : T) : Behavior[T]

  final def onMessage(msg : GCMessage[T]) : AkkaBehavior[GCMessage[T]] =
    msg match {
      case ReleaseMsg(from, releasing, created, sequenceNum) =>
        context.handleRelease(releasing, created)
        from ! AckReleaseMsg(sequenceNum)
//        if (readyToTerminate) {
//          AkkaBehaviors.stopped
//        }
//        else {
          AkkaBehaviors.same
//        }
      case AckReleaseMsg(sequenceNum) =>
        context.finishRelease(sequenceNum)
        AkkaBehaviors.same
      case AppMsg(payload) =>
        context.addRefs(payload.refs)
        onMessage(payload)
    }
}
