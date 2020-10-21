package gc

import akka.actor.typed.scaladsl.{AbstractBehavior => AkkaAbstractBehavior}
import akka.actor.typed.{PostStop, Signal, Behavior => AkkaBehavior}
import gc.aggregator.SnapshotAggregator


/**
 * Parent class for behaviors that implement the GC message protocol.
 */
abstract class AbstractBehavior[T <: Message](context: ActorContext[T])
  extends AkkaAbstractBehavior[GCMessage[T]](context.context) {

  private val snapshotAggregator: SnapshotAggregator =
    SnapshotAggregator(context.context.system)

  snapshotAggregator.generation.add(context.self.target)

  def onMessage(msg: T): Behavior[T]

  final def onMessage(msg: GCMessage[T]): AkkaBehavior[GCMessage[T]] =
    msg match {
      case ReleaseMsg(releasing, created) =>
        context.handleRelease(releasing, created)
        context.tryTerminate()
      case AppMsg(payload, token) =>
        context.handleMessage(payload.refs, token)
        onMessage(payload)
      case SelfCheck =>
        context.handleSelfCheck()
        context.tryTerminate()
      case TakeSnapshot =>
        println(s"${context.self} Taking a snapshot")
        context.snapshot()
        this
    }

  override def onSignal: PartialFunction[Signal, AkkaBehavior[GCMessage[T]]] = {
    case PostStop =>
      snapshotAggregator.generation.remove(context.self.target)
      this

    case signal =>
      super.onSignal(signal)
  }
}
