package edu.illinois.osl.akka.gc

import akka.actor.typed.scaladsl.{AbstractBehavior => AkkaAbstractBehavior, Behaviors => AkkaBehaviors}
import akka.actor.typed.{PostStop, Signal, Behavior => AkkaBehavior}
import aggregator.SnapshotAggregator
import akka.actor.typed.Terminated


/**
 * Parent class for behaviors that implement the GC message protocol.
 */
abstract class AbstractBehavior[T <: Message](context: ActorContext[T])
  extends AkkaAbstractBehavior[GCMessage[T]](context.context) {

  // private val snapshotAggregator: SnapshotAggregator =
  //   SnapshotAggregator(context.context.system)

  // snapshotAggregator.register(context.self.target)

  def onMessage(msg: T): Behavior[T]

  final def onMessage(msg: GCMessage[T]): AkkaBehavior[GCMessage[T]] =
    msg match {
      case ReleaseMsg(releasing, created) =>
        context.handleRelease(releasing, created)
        if (context.tryTerminate())
          AkkaBehaviors.stopped
        else 
          AkkaBehaviors.same
      case AppMsg(payload, token) =>
        context.handleMessage(payload.refs, token)
        onMessage(payload)
      case SelfCheck =>
        context.handleSelfCheck()
        if (context.tryTerminate())
          AkkaBehaviors.stopped
        else 
          AkkaBehaviors.same
      case TakeSnapshot =>
        // snapshotAggregator.put(context.self.target, context.snapshot())
        context.snapshot()
        this
      case Kill =>
        AkkaBehaviors.stopped
    }

  def uponSignal: PartialFunction[Signal, Behavior[T]] = Map.empty

  final override def onSignal: PartialFunction[Signal, Behavior[T]] = {
    case PostStop =>
      // snapshotAggregator.unregister(context.self.target)
      // Forward the signal to the user level if there's a handler; else do nothing.
      this.uponSignal.applyOrElse[Signal, Behavior[T]](PostStop, _ => AkkaBehaviors.same)

    case signal: Terminated =>
      // Try handling the termination signal first
      val result = this.uponSignal.applyOrElse[Signal, Behavior[T]](signal, _ => AkkaBehaviors.same)
      // Now see if we can terminate
      if (context.tryTerminate())  
        AkkaBehaviors.stopped
      else
        result
    case signal =>
      this.uponSignal.applyOrElse[Signal, Behavior[T]](signal, _ => AkkaBehaviors.same)
  }
}
