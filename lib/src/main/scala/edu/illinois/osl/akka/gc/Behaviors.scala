package edu.illinois.osl.akka.gc

import akka.actor.typed.BehaviorInterceptor.ReceiveTarget
import akka.actor.typed.{BehaviorInterceptor, TypedActorContext, ActorRef => AkkaActorRef, Behavior => AkkaBehavior}
import akka.actor.typed.scaladsl.{Behaviors => AkkaBehaviors}

import scala.reflect.ClassTag

object Behaviors {
  def setup[T <: Message](factory: ActorContext[T] => Behavior[T]): ActorFactory[T] =
    (info: protocol.SpawnInfo) =>
      AkkaBehaviors.setup(context => factory(new ActorContext(context, info)))

  private class ReceptionistAdapter[T <: Message](implicit interceptMessageClassTag: ClassTag[T]) extends BehaviorInterceptor[T, protocol.GCMessage[T]] {
    def aroundReceive(ctx: TypedActorContext[T], msg: T, target: ReceiveTarget[protocol.GCMessage[T]]): AkkaBehavior[protocol.GCMessage[T]] =
      target.apply(ctx, protocol.rootMessage(msg))
  }

  def setupReceptionist[T <: Message](factory: ActorContext[T] => Behavior[T])(implicit classTag: ClassTag[T]): AkkaBehavior[T] = {
    val b: AkkaBehavior[protocol.GCMessage[T]] = AkkaBehaviors.setup(context =>
      factory(new ActorContext(context, protocol.rootSpawnInfo()))
    )
    AkkaBehaviors.intercept(() => new ReceptionistAdapter[T]())(b)
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
