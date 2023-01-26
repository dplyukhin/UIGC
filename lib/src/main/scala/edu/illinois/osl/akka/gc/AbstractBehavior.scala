package edu.illinois.osl.akka.gc

import akka.actor.typed.{PostStop, Terminated, Signal}
import akka.actor.typed.scaladsl
import edu.illinois.osl.akka.gc.protocols.Protocol

abstract class AbstractBehavior[T](context: ActorContext[T])
  extends scaladsl.AbstractBehavior[protocol.GCMessage[T]](context.rawContext) {

  def uponMessage(msg: T): Behavior[T]

  final def onMessage(msg: protocol.GCMessage[T]): Behavior[T] = {
    val decision = 
      protocol.onMessage(msg, this.uponMessage, context.state, context.proxyContext)
    decision match {
      case _: Protocol.ShouldStop.type => scaladsl.Behaviors.stopped
      case _: Protocol.ShouldContinue.type => scaladsl.Behaviors.same
      case Protocol.ContinueWith(b) => b
    }
  }

  def uponSignal: PartialFunction[Signal, Behavior[T]] = Map.empty

  final override def onSignal: PartialFunction[Signal, Behavior[T]] = {
    case signal => 
      val handler = (signal: Signal) =>
        this.uponSignal.applyOrElse[Signal, Behavior[T]](signal, _ => scaladsl.Behaviors.same)
      val decision = 
        protocol.onSignal(signal, handler, context.state, context.proxyContext)
      decision match {
        case _: Protocol.ShouldStop.type => scaladsl.Behaviors.stopped
        case _: Protocol.ShouldContinue.type => scaladsl.Behaviors.same
        case Protocol.ContinueWith(b) => b
      }
  }
}
