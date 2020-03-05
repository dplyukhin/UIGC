package gc

import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import org.scalatest.wordspec.AnyWordSpecLike

object SimpleActor {

  // A message that has no refs in it.
  trait NoRefsMessage extends Message {
    override def refs: Iterable[ActorRef[Nothing]] = Seq()
  }
  // SimpleActor message protocol.
  sealed trait Msg extends Message
  final object Init extends Msg with NoRefsMessage
  final object ShareCWithB extends Msg with NoRefsMessage
  final object Release extends Msg with NoRefsMessage
  final case class Share(ref: ActorRef[Msg]) extends Msg with Message {
    override def refs: Iterable[ActorRef[Nothing]] = Seq(ref)
  }

  def apply(): ActorFactory[Msg] = {
    Behaviors.setup(context => new SimpleActor(context))
  }
}

class SimpleActor(context: ActorContext[SimpleActor.Msg]) extends AbstractBehavior[SimpleActor.Msg](context) {
  import SimpleActor._
  var childB: Option[ActorRef[Msg]] = None
  var childC: Option[ActorRef[Msg]] = None
  private val creator = context.creator

  override def onMessage(msg: Msg): Behavior[Msg] = {
    msg match {
      case Init => // spawn B and C
        childB = Some(context.spawn(SimpleActor(), "B"))
        childC = Some(context.spawn(SimpleActor(), "C"))
        this
      case ShareCWithB => // A shares C with B
        val refToShare = context.createRef(childC.get, childB.get)
        context.context.log.info(s"Sharing ref C ${childC.get} with B ${childB.get}")
        childB.get ! Share(refToShare)
        this
      case Share(ref) => // receiving a message with refs in it
        context.context.log.info(s"Received ref $ref")
        this
      case Release => // A release C
        context.release(Set(childC.get))
        this
    }
  }
}

class SimpleActorSpec extends ScalaTestWithActorTestKit with AnyWordSpecLike {
  import SimpleActor._
  "A SimpleActor" can {
    val probe = testKit.createTestProbe[Message]()
    val actorA = testKit.spawn(SimpleActor()(probe.ref, Token(probe.ref, 0)))
    "create children" in {
      actorA ! AppMsg(Init) // this creates B and C
    }
    "share references" in {
      actorA ! AppMsg(ShareCWithB)
    }

  }
}
