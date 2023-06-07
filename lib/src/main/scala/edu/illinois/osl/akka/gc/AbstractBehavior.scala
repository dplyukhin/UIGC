package edu.illinois.osl.akka.gc

import akka.actor.typed.{PostStop, Terminated, Signal, ExtensibleBehavior, TypedActorContext}
import akka.actor.typed.scaladsl
import edu.illinois.osl.akka.gc.protocols.Protocol

abstract class AbstractBehavior[T](context: ActorContext[T])
  extends ExtensibleBehavior[protocol.GCMessage[T]] {

  implicit val _context: ActorContext[T] = context

  // User API
  def onMessage(msg: T): Behavior[T]
  def onSignal: PartialFunction[Signal, Behavior[T]] = PartialFunction.empty

  override final def receive(ctx: TypedActorContext[protocol.GCMessage[T]], msg: protocol.GCMessage[T]): Behavior[T] = {
    val appMsg = protocol.onMessage(msg, context.state, context.proxyContext)

    val result = appMsg match {
      case Some(msg) => onMessage(msg)
      case None => scaladsl.Behaviors.same[protocol.GCMessage[T]]
    }

    protocol.onIdle(msg, context.state, context.proxyContext) match {
      case _: Protocol.ShouldStop.type => scaladsl.Behaviors.stopped
      case _: Protocol.ShouldContinue.type => result
    }
  }

  override final def receiveSignal(ctx: TypedActorContext[protocol.GCMessage[T]], msg: Signal): Behavior[T] = {
    protocol.preSignal(msg, context.state, context.proxyContext)

    val result =
      onSignal.applyOrElse(msg, { case _ => scaladsl.Behaviors.unhandled }: PartialFunction[Signal, Behavior[T]]) 

    protocol.postSignal(msg, context.state, context.proxyContext) match {
      case _: Protocol.Unhandled.type => result
      case _: Protocol.ShouldStop.type => scaladsl.Behaviors.stopped
      case _: Protocol.ShouldContinue.type => 
        if (result == scaladsl.Behaviors.unhandled)
          scaladsl.Behaviors.same
        else 
          result
    }
  }

}
