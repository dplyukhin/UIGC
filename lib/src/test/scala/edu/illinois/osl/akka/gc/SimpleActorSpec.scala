package edu.illinois.osl.akka.gc

import akka.actor.testkit.typed.scaladsl.{ScalaTestWithActorTestKit, TestProbe}
import akka.actor.typed.{PostStop, Signal}
import org.scalatest.wordspec.AnyWordSpecLike
import edu.illinois.osl.akka.gc.interfaces.{Message, NoRefs}

class SimpleActorSpec extends ScalaTestWithActorTestKit with AnyWordSpecLike {

  sealed trait TestMessage extends Message
  case object Init extends TestMessage with NoRefs
  case class SendC(msg: TestMessage) extends TestMessage with NoRefs
  case class SendB(msg: TestMessage) extends TestMessage with NoRefs
  case object TellBAboutC extends TestMessage with NoRefs
  case object ReleaseC extends TestMessage with NoRefs
  case object ReleaseB extends TestMessage with NoRefs
  case object Hello extends TestMessage with NoRefs
  case class Spawned(name: ActorName) extends TestMessage with NoRefs
  case object Terminated extends TestMessage with NoRefs
  case class GetRef(ref: ActorRef[TestMessage]) extends TestMessage with Message {
    override def refs: Iterable[ActorRef[Nothing]] = Iterable(ref)
  }

  val probe: TestProbe[TestMessage] = testKit.createTestProbe[TestMessage]()

  "GC Actors" must {
    val actorA = testKit.spawn(ActorA(), "actorA")
    var children: Set[ActorName] = Set()

    "be able to spawn actors" in {
      actorA ! Init
      children += probe.expectMessageType[Spawned].name
      children += probe.expectMessageType[Spawned].name
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
    def apply(): unmanaged.Behavior[TestMessage] = 
      Behaviors.setupRoot(context => new ActorA(context))
  }
  object ActorB {
    def apply(): ActorFactory[TestMessage] = {
      Behaviors.setup(context => new ActorB(context))
    }
  }
  object ActorC {
    def apply(): ActorFactory[TestMessage] = {
      Behaviors.setup(context => new ActorC(context))
    }
  }

  class ActorA(context: ActorContext[TestMessage]) extends AbstractBehavior[TestMessage](context) {
    var actorB: ActorRef[TestMessage] = _
    var actorC: ActorRef[TestMessage] = _
    override def onMessage(msg: TestMessage): Behavior[TestMessage] = {
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
  class ActorB(context: ActorContext[TestMessage]) extends AbstractBehavior[TestMessage](context) {
    var actorC: ActorRef[TestMessage]= _
    probe.ref ! Spawned(context.name)
    override def onMessage(msg: TestMessage): Behavior[TestMessage] = {
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
    override def uponSignal: PartialFunction[Signal, Behavior[TestMessage]] = {
      case PostStop =>
        probe.ref ! Terminated
        this
    }
  }
  class ActorC(context: ActorContext[TestMessage]) extends AbstractBehavior[TestMessage](context) {
    probe.ref ! Spawned(context.name)
    override def onMessage(msg: TestMessage): Behavior[TestMessage] = {
      msg match {
        case Hello =>
          probe.ref ! Hello
          this
        case _ => this
      }
    }
    override def uponSignal: PartialFunction[Signal, Behavior[TestMessage]] = {
      case PostStop =>
        probe.ref ! Terminated
        this
    }
  }
}