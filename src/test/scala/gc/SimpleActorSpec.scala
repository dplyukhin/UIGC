package gc

import akka.actor.testkit.typed.scaladsl.{ScalaTestWithActorTestKit, TestProbe}
import akka.actor.typed.{PostStop, Signal, Behavior => AkkaBehavior}
import org.scalatest.wordspec.AnyWordSpecLike

trait NoRefsMessage extends Message {
  override def refs: Iterable[AnyActorRef] = Seq()
}

sealed trait testMessage extends Message
case object Init extends testMessage with NoRefsMessage
case class SendC(msg: testMessage) extends testMessage with NoRefsMessage
case class SendB(msg: testMessage) extends testMessage with NoRefsMessage
case object TellBAboutC extends testMessage with NoRefsMessage
case object ReleaseC extends testMessage with NoRefsMessage
case object ReleaseB extends testMessage with NoRefsMessage
case object Hello extends testMessage with NoRefsMessage
case object Spawned extends testMessage with NoRefsMessage
case object Terminated extends testMessage with NoRefsMessage
case class GetRef(ref: ActorRef[testMessage]) extends testMessage with Message {
  override def refs: Iterable[AnyActorRef] = Iterable(ref)
}

class SimpleActorSpec extends ScalaTestWithActorTestKit with AnyWordSpecLike {
  val probe: TestProbe[testMessage] = testKit.createTestProbe[testMessage]()
  "GC Actors" must {
    val actorA = testKit.spawn(ActorA(), "actorA")
    "this test fails" in {
      throw new Exception("Ah!")
    }
    "be able to spawn actors" in {
      actorA ! Init
      probe.expectMessage(Spawned)
      probe.expectMessage(Spawned)
    }
    "be able to send messages" in {
      actorA ! SendC(Hello)
      probe.expectMessage(Hello)
    }
    "be able to share references" in {
      actorA ! TellBAboutC
      actorA ! SendB(SendC(Hello))
      probe.expectMessage(Hello)
    }
    "not terminate when some owners still exist" in {
      actorA ! ReleaseC
      probe.expectNoMessage()
    }
    "be able to send messages after other owners have released" in {
      actorA ! SendB(SendC(Hello))
      probe.expectMessage(Hello)
    }
    "terminate after all references have been released" in {
      actorA ! SendB(ReleaseC)
      probe.expectMessage(Terminated)
    }
    "terminate after the only reference has been released" in {
      actorA ! ReleaseB
      probe.expectMessage(Terminated)
    }
  }

  object ActorA {
    def apply(): AkkaBehavior[testMessage] = Behaviors.setupReceptionist(context => new ActorA(context))
  }
  object ActorB {
    def apply(): ActorFactory[testMessage] = {
      Behaviors.setup(context => new ActorB(context))
    }
  }
  object ActorC {
    def apply(): ActorFactory[testMessage] = {
      Behaviors.setup(context => new ActorC(context))
    }
  }

  class ActorA(context: ActorContext[testMessage]) extends AbstractBehavior[testMessage](context) {
    var actorB: ActorRef[testMessage] = _
    var actorC: ActorRef[testMessage] = _
    override def onMessage(msg: testMessage): Behavior[testMessage] = {
      msg match {
        case Init =>
          actorB = context.spawn(ActorB(), "actorB")
          actorC = context.spawn(ActorC(), "actorC")
          this
        case SendC(msg) =>
          actorC ! msg
          this
        case SendB(msg) =>
          actorB ! msg
          this
        case TellBAboutC =>
          val refToShare = context.createRef(actorC, actorB)
          actorB ! GetRef(refToShare)
          this
        case ReleaseC =>
          context.release(Iterable(actorC))
          this
        case ReleaseB =>
          context.release(Iterable(actorB))
          this
        case _ => this
      }
    }
  }
  class ActorB(context: ActorContext[testMessage]) extends AbstractBehavior[testMessage](context) {
    var actorC: ActorRef[testMessage]= _
    probe.ref ! Spawned
    override def onMessage(msg: testMessage): Behavior[testMessage] = {
      msg match {
        case GetRef(ref) =>
          actorC = ref
          this
        case SendC(msg) =>
          actorC ! msg
          this
        case ReleaseC =>
          context.release(Iterable(actorC))
          this
        case _ => this
      }
    }
    override def onSignal: PartialFunction[Signal, AkkaBehavior[GCMessage[testMessage]]] = {
      case PostStop =>
        probe.ref ! Terminated
        this
    }
  }
  class ActorC(context: ActorContext[testMessage]) extends AbstractBehavior[testMessage](context) {
    probe.ref ! Spawned
    override def onMessage(msg: testMessage): Behavior[testMessage] = {
      msg match {
        case Hello =>
          probe.ref ! Hello
          this
        case _ => this
      }
    }
    override def onSignal: PartialFunction[Signal, AkkaBehavior[GCMessage[testMessage]]] = {
      case PostStop =>
        probe.ref ! Terminated
        this
    }
  }
}