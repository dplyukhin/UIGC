package edu.illinois.osl.akka.gc

import akka.actor.typed.BehaviorInterceptor.ReceiveTarget
import akka.actor.typed.{BehaviorInterceptor, TypedActorContext}

import scala.reflect.ClassTag

object Behaviors {
  /** 
   * Sets up a garbage-collected actor. The [[ActorFactory]] instance
   * produced by this method can only be used by *GC-aware* actors.
   */
  def setup[T <: Message](factory: ActorContext[T] => Behavior[T]): ActorFactory[T] =
    (info: protocol.SpawnInfo) =>
      raw.Behaviors.setup(context => factory(new ActorContext(context, info)))

  private class RootAdapter[T <: Message](
    implicit interceptMessageClassTag: ClassTag[T]
  ) extends BehaviorInterceptor[T, protocol.GCMessage[T]] {
    def aroundReceive(
      ctx: TypedActorContext[T], 
      msg: T, 
      target: ReceiveTarget[protocol.GCMessage[T]]
    ): raw.Behavior[protocol.GCMessage[T]] =
      target.apply(ctx, protocol.rootMessage(msg))
  }

  /**
   * Sets up a root actor. Root actors are GC-aware actors that act as "entry
   * points" to a garbage-collected subsystem. Although root actors themselves
   * must be terminated manually, their descendants will all be terminated
   * automatically---as long as those descendants only interact with other
   * GC-aware actors.  
   */
  def setupRoot[T <: Message](
    factory: ActorContext[T] => Behavior[T]
  )(implicit classTag: ClassTag[T]): raw.Behavior[T] = {

    val b: raw.Behavior[protocol.GCMessage[T]] = raw.Behaviors.setup(context =>
      factory(new ActorContext(context, protocol.rootSpawnInfo()))
    )

    raw.Behaviors.intercept(() => new RootAdapter[T]())(b)
  }

  private class Stopped[T <: Message](context: ActorContext[T]) extends AbstractBehavior[T](context) {
    context.releaseEverything()
    override def onMessage(msg: T): Behavior[T] = {
      context.release(msg.refs)
      this
    }
  }

  /**
   * Returns a behavior that releases all its references, ignores all messages,
   * and waits to be terminated by the garbage collector.
   */
  def stopped[T <: Message](context: ActorContext[T]): Behavior[T] = new Stopped(context)
}
