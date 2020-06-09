package gc

import akka.actor.testkit.typed.scaladsl.{ScalaTestWithActorTestKit, TestProbe}
import org.scalatest.wordspec.AnyWordSpecLike
import akka.actor.typed.{Behavior => AkkaBehavior}

sealed trait ReleaseSpecMsg extends Message
case class SendRefs(x: ActorRef[ReleaseSpecMsg], y: ActorRef[ReleaseSpecMsg]) extends ReleaseSpecMsg with Message {
  override def refs: Iterable[AnyActorRef] = Seq(x, y)
}
case class RefInfo(ref: ActorRef[ReleaseSpecMsg]) extends ReleaseSpecMsg with NoRefsMessage
case object Create extends ReleaseSpecMsg with NoRefsMessage
case class State(snapshot: Option[ActorSnapshot]) extends ReleaseSpecMsg with NoRefsMessage

class ReleaseSpec extends ScalaTestWithActorTestKit with AnyWordSpecLike {
  val probe: TestProbe[ReleaseSpecMsg] = testKit.createTestProbe[ReleaseSpecMsg]()

  "Release protocol" must {
    val A = testKit.spawn(ActorA(), "A")
    "be correct" in {
      val B = probe.expectMessageType[RefInfo].ref
      A ! Create
      val x = probe.expectMessageType[RefInfo].ref
      Thread.sleep(10)

      B ! Create
      val w = probe.expectMessageType[RefInfo].ref
      B ! State(None)
      val bState = probe.expectMessageType[State].snapshot.get
      bState.refs shouldNot contain(x, w)
    }
  }


  // receptionist
  object ActorA {
    def apply(): AkkaBehavior[ReleaseSpecMsg] = Behaviors.setupReceptionist(context => new ActorA(context))
  }
  class ActorA(context: ActorContext[ReleaseSpecMsg]) extends AbstractBehavior[ReleaseSpecMsg](context) {
    val B: ActorRef[ReleaseSpecMsg] = context.spawn(ActorB(), "B")
    val C: ActorRef[ReleaseSpecMsg] = context.spawn(ActorO(), "C")
    probe.ref ! RefInfo(B)
    override def onMessage(msg: ReleaseSpecMsg): Behavior[ReleaseSpecMsg] = {
      msg match {
        case Create =>
          val (x, y) = (context.createRef(B, C), context.createRef(B, C))
          probe.ref ! RefInfo(x)
          B ! SendRefs(x, y)
          this
        case _ =>
          this
      }
    }
  }

  object ActorB {
    def apply(): ActorFactory[ReleaseSpecMsg] = {
      Behaviors.setup(context => new ActorB(context))
    }
  }
  class ActorB(context: ActorContext[ReleaseSpecMsg]) extends AbstractBehavior[ReleaseSpecMsg](context) {
    var D: ActorRef[ReleaseSpecMsg] = _
    var x: ActorRef[ReleaseSpecMsg] = _ // points to C
    var y: ActorRef[ReleaseSpecMsg] = _ // points to C
    override def onMessage(msg: ReleaseSpecMsg): Behavior[ReleaseSpecMsg] = {
      msg match {
        case SendRefs(x, y) =>
          this.x = x
          this.y = y
          this
        case Create =>
          D = context.spawn(ActorO(), "D")
          val w = context.createRef(x, D)
          probe.ref ! RefInfo(w)
          val z = context.createRef(y, D)
          context.release(x)
          this
        case State(_) =>
          probe.ref ! State(Some(context.snapshot()))
          this
        case _ =>
          this
      }
    }
  }

  // doesn't need to actually do anything
  object ActorO {
    def apply(): ActorFactory[ReleaseSpecMsg] = {
      Behaviors.setup(context => new ActorO(context))
    }
  }
  class ActorO(context: ActorContext[ReleaseSpecMsg]) extends AbstractBehavior[ReleaseSpecMsg](context) {
    override def onMessage(msg: ReleaseSpecMsg): Behavior[ReleaseSpecMsg] = this
  }
}
