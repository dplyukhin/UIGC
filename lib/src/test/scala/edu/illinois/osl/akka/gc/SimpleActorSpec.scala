package edu.illinois.osl.akka.gc

import akka.actor.testkit.typed.scaladsl.{ScalaTestWithActorTestKit, TestProbe}
import akka.actor.typed.{PostStop, Signal, ActorRef => AkkaActorRef, Behavior => AkkaBehavior}
import org.scalatest.wordspec.AnyWordSpecLike


class SimpleActorSpec extends ScalaTestWithActorTestKit with AnyWordSpecLike {


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
  case class Spawned(name: AkkaActorRef[Nothing]) extends testMessage with NoRefsMessage
  case object Terminated extends testMessage with NoRefsMessage
  case class GetRef(ref: protocol.ActorRef[testMessage]) extends testMessage with Message {
    override def refs: Iterable[AnyActorRef] = Iterable(ref)
  }


  val probe: TestProbe[testMessage] = testKit.createTestProbe[testMessage]()

  "GC Actors" must {
    val actorA = testKit.spawn(ActorA(), "actorA")
    var children: Set[AkkaActorRef[Nothing]] = Set()

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
    var actorB: protocol.ActorRef[testMessage] = _
    var actorC: protocol.ActorRef[testMessage] = _
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
    var actorC: protocol.ActorRef[testMessage]= _
    probe.ref ! Spawned(context.rawActorRef)
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
    override def uponSignal: PartialFunction[Signal, Behavior[testMessage]] = {
      case PostStop =>
        probe.ref ! Terminated
        this
    }
  }
  class ActorC(context: ActorContext[testMessage]) extends AbstractBehavior[testMessage](context) {
    probe.ref ! Spawned(context.rawActorRef)
    override def onMessage(msg: testMessage): Behavior[testMessage] = {
      msg match {
        case Hello =>
          probe.ref ! Hello
          this
        case _ => this
      }
    }
    override def uponSignal: PartialFunction[Signal, Behavior[testMessage]] = {
      case PostStop =>
        probe.ref ! Terminated
        this
    }
  }
}