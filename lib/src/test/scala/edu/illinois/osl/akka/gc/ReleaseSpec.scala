package gc

import akka.actor.testkit.typed.scaladsl.{ScalaTestWithActorTestKit, TestProbe}
import org.scalatest.wordspec.AnyWordSpecLike
import akka.actor.typed.{Behavior => AkkaBehavior}


class ReleaseSpec extends ScalaTestWithActorTestKit with AnyWordSpecLike {


  trait NoRefsMessage extends Message {
    override def refs: Iterable[AnyActorRef] = Seq()
  }

  sealed trait ReleaseSpecMsg extends Message
  case class SendRefs(x: ActorRef[ReleaseSpecMsg], y: ActorRef[ReleaseSpecMsg]) extends ReleaseSpecMsg with Message {
    override def refs: Iterable[AnyActorRef] = Seq(x, y)
  }
  case class RefInfo(ref: ActorRef[ReleaseSpecMsg]) extends ReleaseSpecMsg with NoRefsMessage
  case object Create extends ReleaseSpecMsg with NoRefsMessage
  case class State(snapshot: Option[ActorSnapshot]) extends ReleaseSpecMsg with NoRefsMessage

  val probe: TestProbe[ReleaseSpecMsg] = testKit.createTestProbe[ReleaseSpecMsg]()

  "Release protocol" must {
    val A = testKit.spawn(ActorA(), "A") // A is created and immediately spawns B and C, sending B's ref to the probe
    "use the proper references" in {
      val B = probe.expectMessageType[RefInfo].ref // receive B's ref
      A ! Create // A creates refs x and y to C for B, and sends x to the probe
      val x = probe.expectMessageType[RefInfo].ref // receive x
      Thread.sleep(10) // wait for messages to proliferate

      B ! Create // B spawns D and creates refs w and z from x and y respectively, but then releases x
      val w = probe.expectMessageType[RefInfo].ref // the probe receives w
      B ! State(None) // ask for B's state
      val bState = probe.expectMessageType[State].snapshot.get // get B's state
      bState.refs shouldNot contain(x, w) // having released x, w should have been released as well
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
          context.createRef(y, D)
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
