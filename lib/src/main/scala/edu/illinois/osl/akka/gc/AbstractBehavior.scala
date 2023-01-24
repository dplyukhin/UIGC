package edu.illinois.osl.akka.gc

import akka.actor.typed.scaladsl.{AbstractBehavior => AkkaAbstractBehavior, Behaviors => AkkaBehaviors}
import akka.actor.typed.{PostStop, Terminated, Signal, Behavior => AkkaBehavior}


/**
 * Parent class for behaviors that implement the GC message protocol.
 */
abstract class AbstractBehavior[T <: Message](context: ActorContext[T])
  extends AkkaAbstractBehavior[protocol.GCMessage[T]](context.context) {

  // private val snapshotAggregator: SnapshotAggregator =
  //   SnapshotAggregator(context.context.system)

  // snapshotAggregator.register(context.self.target)

  def onMessage(msg: T): Behavior[T]

  final def onMessage(msg: protocol.GCMessage[T]): AkkaBehavior[protocol.GCMessage[T]] =
    protocol.onMessage(msg, this.onMessage, context.state, context.context)

  def uponSignal: PartialFunction[Signal, Behavior[T]] = Map.empty

  final override def onSignal: PartialFunction[Signal, Behavior[T]] = {
    case signal => 
      protocol.onSignal(signal, this.uponSignal, context.state, context.context)
  }
}
