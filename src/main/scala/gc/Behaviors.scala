package gc

import akka.actor.typed.BehaviorInterceptor.ReceiveTarget
import akka.actor.typed.{BehaviorInterceptor, TypedActorContext, ActorRef => AkkaActorRef, Behavior => AkkaBehavior}
import akka.actor.typed.scaladsl.{Behaviors => AkkaBehaviors}

import scala.reflect.ClassTag

object Behaviors {
  def setup[T <: Message](factory : ActorContext[T] => Behavior[T]) : ActorFactory[T] =
    (creator : AkkaActorRef[Nothing], token : Token) =>
      AkkaBehaviors.setup(context => factory(new ActorContext(context, creator, token)))

  private class ReceptionistAdapter[T <: Message](implicit interceptMessageClassTag: ClassTag[T]) extends BehaviorInterceptor[T, GCMessage[T]] {
    def aroundReceive(ctx : TypedActorContext[T], msg : T, target : ReceiveTarget[GCMessage[T]]) : AkkaBehavior[GCMessage[T]] =
      target.apply(ctx, AppMsg(msg))
  }

  def setupReceptionist[T <: Message](factory : ActorContext[T] => Behavior[T])(implicit classTag: ClassTag[T]) : AkkaBehavior[T] = {
    val b : AkkaBehavior[GCMessage[T]] = AkkaBehaviors.setup(context =>
      factory(new ActorContext(context, null, null))
    )
    AkkaBehaviors.intercept(() => new ReceptionistAdapter[T]())(b)
  }
}
