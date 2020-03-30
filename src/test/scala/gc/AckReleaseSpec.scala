package gc

import akka.actor.testkit.typed.scaladsl.{ScalaTestWithActorTestKit, TestProbe}
import akka.actor.typed.{PostStop, Signal, Behavior => AkkaBehavior}
import org.scalatest.wordspec.AnyWordSpecLike

sealed trait AckTestMsg extends Message

// request a snapshot from an actor
case object SnapshotAsk extends AckTestMsg with NoRefsMessage
// actor's reply containing their snapshot
case class SnapshotReply(snapshot: ActorSnapshot) extends AckTestMsg with NoRefsMessage
// message to initialize test scenario
case object AckTestInit extends AckTestMsg with NoRefsMessage
// invoke A to release B
case object Release extends AckTestMsg with NoRefsMessage
// invoke A to release C1 and C2
case object DoubleRelease extends AckTestMsg with NoRefsMessage
// message sent on termination
case object SelfTerminated extends AckTestMsg with NoRefsMessage

/**
 * Test scenario:
 * - A spawns B and C. A creates a second reference to C for itself, so A has references C1 and C2.
 * - A releases B. A's knowledge set is checked during the release process to make sure it is invariant. Then it is checked immediately after to make sure it has properly shrank.
 * - A releases C1 and C2 consecutively.
 */
class AckReleaseSpec extends ScalaTestWithActorTestKit with AnyWordSpecLike {
  val probe: TestProbe[AckTestMsg] = testKit.createTestProbe[AckTestMsg]()

  "Actors using AckReleaseMessages" must {
    val actorA = testKit.spawn(ActorA(), "actorA")
    actorA ! AckTestInit

    "keep unacknowledged releases in the knowledge set" in {
      actorA ! SnapshotAsk
      val aKnowledge = probe.expectMessageType[SnapshotReply]
      actorA ! Release
      // the delay here is short enough for us to get the knowledge set before the AckReleaseMessage lands
      actorA ! SnapshotAsk
      probe.expectMessage(aKnowledge)
      probe.expectMessage(SelfTerminated)
      // if we ask again, the knowledge set should be smaller
      actorA ! SnapshotAsk
      val aKnowledgeWithoutB = probe.expectMessageType[SnapshotReply]
      assert(aKnowledge.snapshot.knowledgeSet.size > aKnowledgeWithoutB.snapshot.knowledgeSet.size)
    }

    // this test probably doesn't really prove anything useful
    "not be able to release references more than once" in {
      actorA ! SnapshotAsk
      val aKnowledge = probe.expectMessageType[SnapshotReply]
      actorA ! Release
      actorA ! SnapshotAsk
      probe.expectMessage(aKnowledge)
    }

    // TODO: actually find a way to figure out how to test order??
    "handle acknowledgement in the original release order" in {
      actorA ! SnapshotAsk
      val aKnowledge = probe.expectMessageType[SnapshotReply]
      actorA ! DoubleRelease
      actorA ! SnapshotAsk
      probe.expectMessage(aKnowledge) // before acknowledgement messages land
      probe.expectMessage(SelfTerminated)
      actorA ! SnapshotAsk
      val aKnowledgeWithoutC =probe.expectMessageType[SnapshotReply]
      assert(aKnowledge.snapshot.knowledgeSet.size > aKnowledgeWithoutC.snapshot.knowledgeSet.size)
    }
  }

  object ActorA {
    def apply(): AkkaBehavior[AckTestMsg] = Behaviors.setupReceptionist(context => new ActorA(context))
  }
  object ActorB {
    def apply() : ActorFactory[AckTestMsg] = {
      Behaviors.setup(context => new ActorB(context))
    }
  }
  object ActorC {
    def apply() : ActorFactory[AckTestMsg] = {
      Behaviors.setup(context => new ActorC(context))
    }
  }

  class ActorA(context: ActorContext[AckTestMsg]) extends AbstractBehavior[AckTestMsg](context) {
    var actorB: ActorRef[AckTestMsg] = _
    var actorC1: ActorRef[AckTestMsg] = _
    var actorC2: ActorRef[AckTestMsg] = _
    override def onMessage(msg: AckTestMsg): Behavior[AckTestMsg] = {
      msg match {
        case SnapshotAsk =>
          probe.ref ! SnapshotReply(context.snapshot())
          this
        case AckTestInit =>
          actorB = context.spawn(ActorB(), "actorB")
          actorC1 = context.spawn(ActorC(), "actorC")
          actorC2 = context.createRef(actorC1, context.self)
          this
        case Release =>
          context.release(Iterable(actorB))
          this
        case DoubleRelease =>
          context.release((Iterable(actorC1)))
          context.release((Iterable(actorC2)))
          this
        case _ =>
          this
      }
    }
  }

  class ActorB(context: ActorContext[AckTestMsg]) extends AbstractBehavior[AckTestMsg](context) {
    override def onMessage(msg: AckTestMsg): Behavior[AckTestMsg] = {
      msg match {
        case SnapshotAsk =>
          probe.ref ! SnapshotReply(context.snapshot())
          this
        case _ =>
          this
      }
    }
    override def onSignal: PartialFunction[Signal, AkkaBehavior[GCMessage[AckTestMsg]]] = {
      case PostStop =>
        probe.ref ! SelfTerminated
        this
    }
  }

  class ActorC(context: ActorContext[AckTestMsg]) extends AbstractBehavior[AckTestMsg](context) {
    override def onMessage(msg: AckTestMsg): Behavior[AckTestMsg] = {
      msg match {
        case SnapshotAsk =>
          probe.ref ! SnapshotReply(context.snapshot())
          this
        case _ =>
          this
      }
    }
    override def onSignal: PartialFunction[Signal, AkkaBehavior[GCMessage[AckTestMsg]]] = {
      case PostStop =>
        probe.ref ! SelfTerminated
        this
    }
  }
}
