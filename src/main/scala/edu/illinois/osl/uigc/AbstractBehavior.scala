package edu.illinois.osl.uigc

import akka.actor.typed.{PostStop, Terminated, Signal, ExtensibleBehavior, TypedActorContext}
import akka.actor.typed.scaladsl
import edu.illinois.osl.uigc.engines.Engine

abstract class AbstractBehavior[T](context: ActorContext[T])
  extends ExtensibleBehavior[protocol.GCMessage[T]] {

  implicit val _context: ActorContext[T] = context

  // User API
  def onMessage(msg: T): Behavior[T]
  def onSignal: PartialFunction[Signal, Behavior[T]] = PartialFunction.empty

  override final def receive(ctx: TypedActorContext[protocol.GCMessage[T]], msg: protocol.GCMessage[T]): Behavior[T] = {
    val appMsg = protocol.onMessage(msg, context.state, context.rawContext)

    val result = appMsg match {
      case Some(msg) => onMessage(msg)
      case None => scaladsl.Behaviors.same[protocol.GCMessage[T]]
    }

    protocol.onIdle(msg, context.state, context.rawContext) match {
      case _: Engine.ShouldStop.type => scaladsl.Behaviors.stopped
      case _: Engine.ShouldContinue.type => result
    }
  }

  override final def receiveSignal(ctx: TypedActorContext[protocol.GCMessage[T]], msg: Signal): Behavior[T] = {
    protocol.preSignal(msg, context.state, context.rawContext)

    val result =
      onSignal.applyOrElse(msg, { case _ => scaladsl.Behaviors.unhandled }: PartialFunction[Signal, Behavior[T]]) 

    protocol.postSignal(msg, context.state, context.rawContext) match {
      case _: Engine.Unhandled.type => result
      case _: Engine.ShouldStop.type => scaladsl.Behaviors.stopped
      case _: Engine.ShouldContinue.type =>
        if (result == scaladsl.Behaviors.unhandled)
          scaladsl.Behaviors.same
        else 
          result
    }
  }

}
