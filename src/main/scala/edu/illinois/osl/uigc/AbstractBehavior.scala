package edu.illinois.osl.uigc

import akka.actor.typed.{ExtensibleBehavior, Signal, TypedActorContext, scaladsl}
import edu.illinois.osl.uigc.engines.Engine
import edu.illinois.osl.uigc.interfaces._

abstract class AbstractBehavior[T](context: ActorContext[T])
    extends ExtensibleBehavior[GCMessage[T]] {

  implicit val _context: ActorContext[T] = context

  // User API
  def onMessage(msg: T): Behavior[T]
  def onSignal: PartialFunction[Signal, Behavior[T]] = PartialFunction.empty

  override final def receive(
      ctx: TypedActorContext[GCMessage[T]],
      msg: GCMessage[T]
  ): Behavior[T] = {
    val appMsg = context.engine.onMessage(msg, context.state, context.typedContext)

    val result = appMsg match {
      case Some(msg) => onMessage(msg)
      case None      => scaladsl.Behaviors.same[GCMessage[T]]
    }

    context.engine.onIdle(msg, context.state, context.typedContext) match {
      case _: Engine.ShouldStop.type     => scaladsl.Behaviors.stopped
      case _: Engine.ShouldContinue.type => result
    }
  }

  override final def receiveSignal(
      ctx: TypedActorContext[GCMessage[T]],
      msg: Signal
  ): Behavior[T] = {
    context.engine.preSignal(msg, context.state, context.typedContext)

    val result =
      onSignal.applyOrElse(
        msg,
        { case _ => scaladsl.Behaviors.unhandled }: PartialFunction[Signal, Behavior[T]]
      )

    context.engine.postSignal(msg, context.state, context.typedContext) match {
      case _: Engine.Unhandled.type  => result
      case _: Engine.ShouldStop.type => scaladsl.Behaviors.stopped
      case _: Engine.ShouldContinue.type =>
        if (result == scaladsl.Behaviors.unhandled)
          scaladsl.Behaviors.same
        else
          result
    }
  }

}
