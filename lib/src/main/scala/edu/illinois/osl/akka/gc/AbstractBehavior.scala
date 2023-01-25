package edu.illinois.osl.akka.gc

import akka.actor.typed.{PostStop, Terminated, Signal}

abstract class AbstractBehavior[T <: Message](context: ActorContext[T])
  extends raw.AbstractBehavior[protocol.GCMessage[T]](context.rawContext) {

  def onMessage(msg: T): Behavior[T]

  final def onMessage(msg: protocol.GCMessage[T]): Behavior[T] = {
    val decision = 
      protocol.onMessage[T, Behavior[T]](msg, this.onMessage, context.state, context.proxyContext)
    decision match {
      case _: Protocol.ShouldStop.type => raw.Behaviors.stopped
      case _: Protocol.ShouldContinue.type => raw.Behaviors.same
      case Protocol.ContinueWith(b) => b
    }
  }

  def uponSignal: PartialFunction[Signal, Behavior[T]] = Map.empty

  final override def onSignal: PartialFunction[Signal, Behavior[T]] = {
    case signal => 
      val decision = 
        protocol.onSignal(signal, this.uponSignal, context.state, context.proxyContext)
      decision match {
        case _: Protocol.ShouldStop.type => raw.Behaviors.stopped
        case _: Protocol.ShouldContinue.type => raw.Behaviors.same
        case Protocol.ContinueWith(b) => b
      }
  }
}
