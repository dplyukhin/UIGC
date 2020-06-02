package gc

import akka.actor.typed.scaladsl.{AbstractBehavior => AkkaAbstractBehavior}
import akka.actor.typed.{Behavior => AkkaBehavior}


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
      case ReleaseMsg(from, releasing, created) =>
        context.handleRelease(releasing, created)
        context.tryTerminate()
      case AppMsg(payload, token) =>
        context.handleMessage(payload.refs, token)
        onMessage(payload)
      case SelfCheck() =>
        context.tryTerminate()
    }
}
