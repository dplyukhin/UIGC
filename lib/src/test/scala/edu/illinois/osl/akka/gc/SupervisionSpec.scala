package edu.illinois.osl.akka.gc

import akka.actor.testkit.typed.scaladsl.{ScalaTestWithActorTestKit, TestProbe}
import akka.actor.typed.{PostStop, Signal, ActorRef => AkkaActorRef, Behavior => AkkaBehavior}
import org.scalatest.wordspec.AnyWordSpecLike
import scala.concurrent.duration._
import edu.illinois.osl.akka.gc.interfaces.{Message, NoRefs}


/** 
 * Addresses Github issue #15: Actors should not be garbage collected before
 * their children (which they supervise). In Akka, stopping a parent actor
 * causes all its descendents to stop.
 */ 
class SupervisionSpec extends ScalaTestWithActorTestKit with AnyWordSpecLike {

  sealed trait TestMessage extends Message
  case object Init extends TestMessage with NoRefs
  case object Initialized extends TestMessage with NoRefs
  case object ReleaseChild2 extends TestMessage with NoRefs
  case object ReleaseChild1 extends TestMessage with NoRefs
  case object ReleaseParent extends TestMessage with NoRefs
  case class Spawned(name: ActorName) extends TestMessage with NoRefs
  case class Terminated(name: ActorName) extends TestMessage with NoRefs
  case class GetRef(ref: ActorRef[TestMessage]) extends TestMessage with Message {
    override def refs: Iterable[ActorRef[Nothing]] = Iterable(ref)
  }


  val probe: TestProbe[TestMessage] = testKit.createTestProbe[TestMessage]()

  "GC Actors" must {
    val root = testKit.spawn(RootActor(), "root")
    var parent: ActorName = null
    var child1: ActorName = null
    var child2: ActorName = null

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
    def apply(): AkkaBehavior[TestMessage] = 
      Behaviors.setupRoot(context => new RootActor(context))
  }
  object Parent {
    def apply(): ActorFactory[TestMessage] = 
      Behaviors.setup(context => new Parent(context))
  }
  object Child {
    def apply(): ActorFactory[TestMessage] = {
      Behaviors.setup(context => new Child(context))
    }
  }

  class RootActor(context: ActorContext[TestMessage]) extends AbstractBehavior[TestMessage](context) {
    var actorA: ActorRef[TestMessage] = _
    var actorB: ActorRef[TestMessage] = _
    var actorC: ActorRef[TestMessage] = _
    override def onMessage(msg: TestMessage): Behavior[TestMessage] = {
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
  class Parent(context: ActorContext[TestMessage]) extends AbstractBehavior[TestMessage](context) {
    probe.ref ! Spawned(context.name)
    var actorB: ActorRef[TestMessage] = context.spawn(Child(), "child1")
    var actorC: ActorRef[TestMessage] = context.spawn(Child(), "child2")
    probe.ref ! Spawned(actorB.rawActorRef)
    probe.ref ! Spawned(actorC.rawActorRef)
    override def onMessage(msg: TestMessage): Behavior[TestMessage] = {
      msg match {
        case GetRef(root) =>
          root ! GetRef(context.createRef(actorB, root))
          root ! GetRef(context.createRef(actorC, root))
          context.release(Iterable(actorB, actorC))
          this
        case _ => this
      }
    }
    override def onSignal: PartialFunction[Signal, Behavior[TestMessage]] = {
      case PostStop =>
        probe.ref ! Terminated(context.name)
        this
    }
  }
  class Child(context: ActorContext[TestMessage]) extends AbstractBehavior[TestMessage](context) {
    override def onMessage(msg: TestMessage): Behavior[TestMessage] = {
      msg match {
        case _ => this
      }
    }
    override def onSignal: PartialFunction[Signal, Behavior[TestMessage]] = {
      case PostStop =>
        probe.ref ! Terminated(context.name)
        this
    }
  }
}