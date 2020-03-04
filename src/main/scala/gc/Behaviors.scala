package gc

import akka.actor.typed.{ActorRef => AkkaActorRef}
import akka.actor.typed.scaladsl.{Behaviors => AkkaBehaviors}

object Behaviors {
  def setup[T <: Message](factory : ActorContext[T] => Behavior[T]) : ActorFactory[T] =
    (creator : AkkaActorRef[Nothing], token : Token) =>
      AkkaBehaviors.setup(context => factory(new ActorContext(context, creator, token)))
}
