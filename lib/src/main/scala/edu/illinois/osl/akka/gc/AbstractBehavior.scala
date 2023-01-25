package edu.illinois.osl.akka.gc

import akka.actor.typed.{PostStop, Terminated, Signal}

abstract class AbstractBehavior[T <: Message](context: ActorContext[T])
  extends raw.AbstractBehavior[protocol.GCMessage[T]](context.rawContext) {

  def onMessage(msg: T): Behavior[T]

  final def onMessage(msg: protocol.GCMessage[T]): Behavior[T] =
    protocol.onMessage(msg, this.onMessage, context.state, context.rawContext)

  def uponSignal: PartialFunction[Signal, Behavior[T]] = Map.empty

  final override def onSignal: PartialFunction[Signal, Behavior[T]] = {
    case signal => 
      protocol.onSignal(signal, this.uponSignal, context.state, context.rawContext)
  }
}
