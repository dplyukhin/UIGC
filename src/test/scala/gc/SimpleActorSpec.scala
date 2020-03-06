package gc

import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import akka.actor.typed.{PostStop, Signal, Behavior => AkkaBehavior}
import org.scalatest.wordspec.AnyWordSpecLike

trait NoRefsMessage extends Message {
  override def refs: Iterable[ActorRef[Nothing]] = Seq()
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
  override def refs: Iterable[ActorRef[Nothing]] = Iterable()
  Iterable(ref)
}

class SimpleActorSpec extends ScalaTestWithActorTestKit with AnyWordSpecLike {
  val probe = testKit.createTestProbe[testMessage]()
  // TESTS HERE
  "GC Actors" must {
    val actorA = testKit.spawn(ActorA(), "actorA")
    "spawn" in {
      actorA ! Init
      probe.expectMessage(Spawned)
      probe.expectMessage(Spawned)
    }
    "send app messages" in {
      actorA ! SendC(Hello)
      probe.expectMessage(Hello)
    }
    "share references" in {
      actorA ! TellBAboutC
      actorA ! SendB(SendC(Hello))
      probe.expectMessage(Hello)
    }
    "release references without terminating" in {
      actorA ! ReleaseC
      probe.expectNoMessage()
    }
    "references gets maintained" in {
      actorA ! SendB(SendC(Hello))
      probe.expectMessage(Hello)
    }
    "eventually releasing all references terminates" in {
      actorA ! SendB(ReleaseC)
      probe.expectMessage(Terminated)
    }
    "simply releasing a reference terminates" in {
      actorA ! ReleaseB
      probe.expectMessage(Terminated)
    }
  }

  object ActorA {
    def apply(): AkkaBehavior[testMessage] = Behaviors.setupReceptionist(context => new ActorA(context))
  }
  object ActorB {
    def apply() : ActorFactory[testMessage] = {
      Behaviors.setup((context => new ActorB(context)))
    }
  }
  object ActorC {
    def apply(): ActorFactory[testMessage] = {
      Behaviors.setup((context => new ActorC(context)))
    }
  }

  class ActorA(context: ActorContext[testMessage]) extends AbstractBehavior[testMessage](context) {
    var actorB : ActorRef[testMessage] = null
    var actorC : ActorRef[testMessage]= null
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
      }
    }
  }
  class ActorB(context: ActorContext[testMessage]) extends AbstractBehavior[testMessage](context) {
    var actorC : ActorRef[testMessage]= null
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
      }
    }
    override def onSignal: PartialFunction[Signal, AkkaBehavior[GCMessage[testMessage]]] = {
      case PostStop =>
        probe.ref ! Terminated
        this
    }
  }
}