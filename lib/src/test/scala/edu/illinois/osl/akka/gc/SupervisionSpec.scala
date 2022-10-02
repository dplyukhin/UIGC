package edu.illinois.osl.akka.gc

import akka.actor.testkit.typed.scaladsl.{ScalaTestWithActorTestKit, TestProbe}
import akka.actor.typed.{PostStop, Signal, ActorRef => AkkaActorRef, Behavior => AkkaBehavior}
import edu.illinois.osl.akka.gc.aggregator.SnapshotAggregator
import org.scalatest.wordspec.AnyWordSpecLike
import scala.concurrent.duration._


/** 
 * Addresses Github issue #15: Actors should not be garbage collected before
 * their children (which they supervise). In Akka, stopping a parent actor
 * causes all its descendents to stop.
 */ 
class SupervisionSpec extends ScalaTestWithActorTestKit with AnyWordSpecLike {


  trait NoRefsMessage extends Message {
    override def refs: Iterable[AnyActorRef] = Seq()
  }

  sealed trait testMessage extends Message
  case object Init extends testMessage with NoRefsMessage
  case object Initialized extends testMessage with NoRefsMessage
  case object ReleaseChild2 extends testMessage with NoRefsMessage
  case object ReleaseChild1 extends testMessage with NoRefsMessage
  case object ReleaseParent extends testMessage with NoRefsMessage
  case class Spawned(name: AkkaActorRef[Nothing]) extends testMessage with NoRefsMessage
  case class Terminated(name: AkkaActorRef[Nothing]) extends testMessage with NoRefsMessage
  case class GetRef(ref: ActorRef[testMessage]) extends testMessage with Message {
    override def refs: Iterable[AnyActorRef] = Iterable(ref)
  }


  val probe: TestProbe[testMessage] = testKit.createTestProbe[testMessage]()

  "GC Actors" must {
    val root = testKit.spawn(RootActor(), "root")
    var parent: AkkaActorRef[Nothing] = null
    var child1: AkkaActorRef[Nothing] = null
    var child2: AkkaActorRef[Nothing] = null

    root ! Init
    parent = probe.expectMessageType[Spawned].name
    child1 = probe.expectMessageType[Spawned].name
    child2 = probe.expectMessageType[Spawned].name
    probe.expectMessage(Initialized)

    "not be garbage collected before their children" in {
      root ! ReleaseParent
      probe.expectNoMessage()
    }
    "not be garbage collected until *all* their children are stopped" in {
      root ! ReleaseChild1
      probe.expectMessage(Terminated(child1))
    }
    "be garbage collected once all their children are stopped" in {
      root ! ReleaseChild2
      probe.expectMessage(Terminated(child2))
      probe.expectMessage(Terminated(parent))
    }
  }

  object RootActor {
    def apply(): AkkaBehavior[testMessage] = 
      Behaviors.setupReceptionist(context => new RootActor(context))
  }
  object Parent {
    def apply(): ActorFactory[testMessage] = 
      Behaviors.setup(context => new Parent(context))
  }
  object Child {
    def apply(): ActorFactory[testMessage] = {
      Behaviors.setup(context => new Child(context))
    }
  }

  class RootActor(context: ActorContext[testMessage]) extends AbstractBehavior[testMessage](context) {
    var actorA: ActorRef[testMessage] = _
    var actorB: ActorRef[testMessage] = null
    var actorC: ActorRef[testMessage] = null
    override def onMessage(msg: testMessage): Behavior[testMessage] = {
      msg match {
        case Init =>
          actorA = context.spawn(Parent(), "parent")
          actorA ! GetRef(context.createRef(context.self, actorA))
          this
        case GetRef(child) =>
          if (actorB == null) {
            actorB = child
          }
          else {
            actorC = child
            probe.ref ! Initialized
          }
          this
        case ReleaseParent =>
          context.release(Iterable(actorA))
          this
        case ReleaseChild1 =>
          context.release(Iterable(actorB))
          this
        case ReleaseChild2 =>
          context.release(Iterable(actorC))
          this
        case _ => this
      }
    }
  }
  class Parent(context: ActorContext[testMessage]) extends AbstractBehavior[testMessage](context) {
    probe.ref ! Spawned(context.self.target)
    var actorB: ActorRef[testMessage] = context.spawn(Child(), "child1")
    var actorC: ActorRef[testMessage] = context.spawn(Child(), "child2")
    probe.ref ! Spawned(actorB.target)
    probe.ref ! Spawned(actorC.target)
    override def onMessage(msg: testMessage): Behavior[testMessage] = {
      msg match {
        case GetRef(root) =>
          root ! GetRef(context.createRef(actorB, root))
          root ! GetRef(context.createRef(actorC, root))
          context.release(Iterable(actorB, actorC))
          this
        case _ => this
      }
    }
    override def onSignal: PartialFunction[Signal, AkkaBehavior[GCMessage[testMessage]]] = {
      case PostStop =>
        probe.ref ! Terminated(context.name)
        super.onSignal(PostStop)
      case signal =>
        super.onSignal(signal)
    }
  }
  class Child(context: ActorContext[testMessage]) extends AbstractBehavior[testMessage](context) {
    override def onMessage(msg: testMessage): Behavior[testMessage] = {
      msg match {
        case _ => this
      }
    }
    override def onSignal: PartialFunction[Signal, AkkaBehavior[GCMessage[testMessage]]] = {
      case PostStop =>
        probe.ref ! Terminated(context.name)
        super.onSignal(PostStop)
      case signal =>
        super.onSignal(signal)
    }
  }
}