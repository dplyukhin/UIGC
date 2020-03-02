package example
import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import org.scalatest.WordSpecLike
import gc._

object SimpleActor {
  trait SimpleMessage extends Message {
    override def refs: Seq[ActorRef[Nothing]] = Seq()
  }
  sealed trait Msg
  final case class Init extends Msg with SimpleMessage
  final case class Release extends Msg with SimpleMessage
  final case class Share(ref : ActorRef[Msg]) extends Msg with Message {
    override def refs: Seq[ActorRef[Nothing]] = Seq(ref)
  }

  def apply(): ActorFactory[SimpleActor.StringMsg] = {
    Behaviors.setup(context => new SimpleActor(context))
  }
}

class SimpleActor(context: ActorContext[SimpleActor.StringMsg]) extends AbstractBehavior[SimpleActor.StringMsg](context) {
  import SimpleActor._
  private var childB = None: Option[ActorRef[StringMsg]]
  private var childC = None: Option[ActorRef[StringMsg]]

  override def onMessage(msg: StringMsg): Behavior[StringMsg] = {
    msg.str match {
      case "init" => // spawn B and C
        childB = Some(context.spawn(SimpleActor(), "B"))
        childC = Some(context.spawn(SimpleActor(), "C"))
        this
      case "share" => // A shares C with B
        val refToShare = context.createRef(childC.get, childB.get)
        childC.get ! StringMsg("here O_O", Seq(refToShare))
        this
      case "release" => // A release C
        context.release(Seq(childC.get))
        this
    }
  }
}

class MyActorSpec extends ScalaTestWithActorTestKit with WordSpecLike {
  import SimpleActor._
  "SimpleActor must" must {
    val probe = testKit.createTestProbe[GCMessage[String]]()
    val actorA = testKit.spawn(SimpleActor()(probe.ref, Token(probe.ref, 0)))
    "create children" in {
      actorA ! AppMsg(StringMsg("init", Seq()))
    }
  }

}
