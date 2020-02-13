package example

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.{AbstractBehavior, ActorContext, Behaviors}
import Reference._

object ActorAugmented {
  // message protocol
  sealed trait GCommand
  final case class Release(releasing: Set[Reference], creations: Set[Reference])
  final case class Share(ref: Reference)

  def apply(creator: ActorRef[Any], token: Token): Behavior[Any] = {
    Behaviors.setup(context => new ActorAugmented(context, creator, token))
  }
}

class ActorAugmented(context: ActorContext[Any], creator: ActorRef[Any], token: Token)
  extends AbstractBehavior[Any](context) {

  private var refs: Set[Reference] = Set()
  private var created: Set[Reference] = Set()
  private var owners: Set[Reference] = Set()
  private var released_owners: Set[Reference] = Set()

  override def onMessage(msg: Any): Behavior[Any] = {
    this
  }
}