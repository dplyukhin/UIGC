package example

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.{AbstractBehavior, ActorContext, Behaviors}
import Reference._

object ActorAugmented {
  // message protocol
  sealed trait GCommand
  final case class Release(releasing: Set[Reference], creations: Set[Reference]) extends GCommand
  final case class Share(ref: Reference) extends GCommand

  sealed trait TestCommand
  final case class Spawn() extends TestCommand
  final case class TestShare(to: Reference, target: Reference) extends TestCommand
  final case class TestRelease() extends TestCommand

  def apply(creator: ActorRef[Any], token: Token): Behavior[Any] = {
    Behaviors.setup(context => new ActorAugmented(context, creator, token))
  }
}

class ActorAugmented(context: ActorContext[Any], creator: ActorRef[Any], token: Token)
  extends AbstractBehavior[Any](context) {

  private var refs: Set[Reference] = Set()
  private var created: Set[Reference] = Set()
  private var owners: Set[Reference] = Set(Reference(token, creator, context.self))
  private var released_owners: Set[Reference] = Set()
  private var tokenCount: Int = 0;

  def createToken(): Token = {
    val token = Token(context.self, tokenCount)
    tokenCount += 1
    token
  }

  override def onMessage(msg: Any): Behavior[Any] = {
    import ActorAugmented._
    msg match{
      case Spawn() =>
        val token = createToken()
        val childRef = context.spawn(ActorAugmented(context, context.self, token), "child")
        refs += Reference(token, context.self, childRef)
        Behaviors.same
      case TestShare() =>
        val token1 = createToken()

    }
  }
}