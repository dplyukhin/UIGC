package gc

import akka.actor.testkit.typed.scaladsl.{ScalaTestWithActorTestKit, TestProbe}
import akka.actor.typed.{ActorRef => AkkaActorRef, Behavior => AkkaBehavior}
import org.scalatest.wordspec.AnyWordSpecLike


sealed trait AckTestMsg extends Message

// request a snapshot from an actor
case object SnapshotAsk extends AckTestMsg with NoRefsMessage
// actor's reply containing their snapshot
case class SnapshotReply(snapshot: ActorSnapshot) extends AckTestMsg with NoRefsMessage
// message to initialize test scenario, includes an ActorRef to a probe that han
case class AckTestInit(probeRef: ActorRef[AckTestMsg]) extends AckTestMsg with Message {
  override def refs: Iterable[AnyActorRef] = Iterable(probeRef)
}
case object SelfAsk extends AckTestMsg with NoRefsMessage
case class Self(self: AkkaActorRef[GCMessage[AckTestMsg]]) extends AckTestMsg with NoRefsMessage
case object CreateRefs extends AckTestMsg with NoRefsMessage
case class CreatedRefs(refSet: Set[AnyActorRef]) extends AckTestMsg with NoRefsMessage
// invoke A to release B
case object Release extends AckTestMsg with NoRefsMessage
// invoke A to release C1 and C2
case object ReleaseMultiple extends AckTestMsg with NoRefsMessage
// message sent on termination
case object SelfTerminated extends AckTestMsg with NoRefsMessage

/**
 * Test scenario:
 * - A spawns B and C. A creates a second reference to C for itself, so A has references C1 and C2.
 * - A releases B. A's knowledge set is checked during the release process to make sure it is invariant. Then it is checked immediately after to make sure it has properly shrank.
 * - A releases C1 and C2 consecutively.
 */
class AckReleaseSpec extends ScalaTestWithActorTestKit with AnyWordSpecLike {
  // probe for receiving info like snapshots
  val probe: TestProbe[AckTestMsg] = testKit.createTestProbe[AckTestMsg]()
  // probe that intercepts GC messages
  val probe2: TestProbe[GCMessage[AckTestMsg]] = testKit.createTestProbe()

  "Actors using AckReleaseMessages" when {
//    val actorA = testKit.spawn(ActorA(), "actorA")
//    actorA ! SelfAsk
//    val akkaActorA = probe.expectMessageType[Self].self

    "releasing an owned reference" should {
      val actorA = testKit.spawn(ActorA())
      val probe2Ref: ActorRef[AckTestMsg] = ActorRef(null, actorA, probe2.ref)
      actorA ! SelfAsk
      val akkaActorA = probe.expectMessageType[Self].self

      "keep unacknowledged releases in the knowledge set" in {
        actorA ! AckTestInit(probe2Ref)
        actorA ! Release
        actorA ! SnapshotAsk
        val aKnowledge = probe.expectMessageType[SnapshotReply].snapshot

        aKnowledge.knowledgeSet should contain (probe2Ref)
      }

      "remove acknowledged releases from the knowledge set" in {
        actorA ! SnapshotAsk
        var aKnowledge = probe.expectMessageType[SnapshotReply].snapshot
        akkaActorA ! AckReleaseMsg(0)
        actorA ! SnapshotAsk
        aKnowledge = probe.expectMessageType[SnapshotReply].snapshot

        aKnowledge.knowledgeSet should not contain (probe2Ref)
      }
    }

    "releasing created references" should {
      val actorA = testKit.spawn(ActorA())
      actorA ! SelfAsk
      val akkaActorA = probe.expectMessageType[Self].self
      val probe2Ref: ActorRef[AckTestMsg] = ActorRef(null, actorA, probe2.ref)

      "keep unacknowledged references in the knowledge set" in {
        actorA ! AckTestInit(probe2Ref)

        actorA ! CreateRefs
        val refs = probe.expectMessageType[CreatedRefs].refSet
        actorA ! SnapshotAsk
        var aKnowledge = probe.expectMessageType[SnapshotReply].snapshot
        actorA ! ReleaseMultiple
        actorA ! SnapshotAsk
        aKnowledge = probe.expectMessageType[SnapshotReply].snapshot
        aKnowledge.knowledgeSet should contain (refs)
      }
    }


//    "not be able to release references more than once" ignore {
//      actorA ! SnapshotAsk
//      val aKnowledge = probe.expectMessageType[SnapshotReply]
//      actorA ! Release
//      actorA ! SnapshotAsk
//      probe.expectMessage(aKnowledge)
//    }



    // this test probably doesn't really prove anything useful
  }

  object ActorA {
    def apply(): AkkaBehavior[AckTestMsg] = Behaviors.setupReceptionist(context => new ActorA(context))
  }

  class ActorA(context: ActorContext[AckTestMsg]) extends AbstractBehavior[AckTestMsg](context) {
    var internalProbeRef: ActorRef[AckTestMsg] = _
    var createdRefs: Set[AnyActorRef] = Set()
    override def onMessage(msg: AckTestMsg): Behavior[AckTestMsg] = {
      msg match {
        case SnapshotAsk =>
          probe.ref ! SnapshotReply(context.snapshot())
          this
        case AckTestInit(probeRef) =>
          internalProbeRef = probeRef
//          actorB = context.spawn(ActorB(), "actorB")
//          actorC1 = context.spawn(ActorC(), "actorC")
//          actorC2 = context.createRef(actorC1, context.self)
          this
        case Release =>
//          context.release(Iterable(actorB))
          context.release(Iterable(internalProbeRef))
          this
        case SelfAsk =>
          probe.ref ! Self(context.self.target)
          this
        case CreateRefs =>
          for (i <- 1 to 3) {
            createdRefs += context.createRef(internalProbeRef, null)
          }
          probe.ref ! CreatedRefs(createdRefs)
          this
        case ReleaseMultiple =>
          context.release(createdRefs)
          this
        case _ =>
          this
      }
    }
  }
}
