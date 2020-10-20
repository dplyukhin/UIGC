package gc

import akka.actor.typed.BehaviorInterceptor.ReceiveTarget
import akka.actor.typed.{BehaviorInterceptor, TypedActorContext, ActorRef => AkkaActorRef, Behavior => AkkaBehavior}
import akka.actor.typed.scaladsl.{Behaviors => AkkaBehaviors}

import scala.reflect.ClassTag

object Behaviors {
  def setup[T <: Message](factory: ActorContext[T] => Behavior[T]): ActorFactory[T] =
    (creator: AkkaActorRef[Nothing], token: Token) =>
      AkkaBehaviors.withTimers { timers =>
        AkkaBehaviors.setup { context =>
          factory(new ActorContext(context, Some(timers), Some(creator), Some(token)))
        }
      }

  private class ReceptionistAdapter[T <: Message](implicit interceptMessageClassTag: ClassTag[T]) extends BehaviorInterceptor[T, GCMessage[T]] {
    def aroundReceive(ctx: TypedActorContext[T], msg: T, target: ReceiveTarget[GCMessage[T]]): AkkaBehavior[GCMessage[T]] =
      target.apply(ctx, AppMsg(msg, None))
  }

  def setupReceptionist[T <: Message](factory: ActorContext[T] => Behavior[T])(implicit classTag: ClassTag[T]): AkkaBehavior[T] = {
    val b: AkkaBehavior[GCMessage[T]] = AkkaBehaviors.setup(context =>
      factory(new ActorContext(context, None, None, None))
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
