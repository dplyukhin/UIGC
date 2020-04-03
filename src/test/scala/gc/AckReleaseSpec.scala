package gc

import akka.actor.testkit.typed.scaladsl.{ScalaTestWithActorTestKit, TestProbe}
import akka.actor.typed.{ActorRef => AkkaActorRef, Behavior => AkkaBehavior}
import org.scalatest.wordspec.AnyWordSpecLike


sealed trait AckTestMsg extends Message
// protocol to receive a snapshot from an actor
case object SnapshotAsk extends AckTestMsg with NoRefsMessage
case class SnapshotReply(snapshot: ActorSnapshot) extends AckTestMsg with NoRefsMessage
// message to initialize test scenario, includes an ActorRef to a probe that will intercept GCMessages
case class AckTestInit(probeRef: ActorRef[AckTestMsg]) extends AckTestMsg with Message {
  override def refs: Iterable[AnyActorRef] = Iterable(probeRef)
}
// protocol to extract the internal AkkaActorRef from an actor
case object SelfAsk extends AckTestMsg with NoRefsMessage
case class Self(self: AkkaActorRef[GCMessage[AckTestMsg]]) extends AckTestMsg with NoRefsMessage
// protocol to invoke the test actor to create some references and return them
case object CreateRefs extends AckTestMsg with NoRefsMessage
case class CreatedRefs(refSet: Set[ActorRef[AckTestMsg]]) extends AckTestMsg with NoRefsMessage

// invoke A to release its probe2 reference
case object Release extends AckTestMsg with NoRefsMessage
// invoke A to release its set of probe2 references
case object ReleaseMultiple extends AckTestMsg with NoRefsMessage

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
    "releasing an owned reference" should {
      val actorA = testKit.spawn(ActorA()) // spawn actor
      val probe2Ref: ActorRef[AckTestMsg] = ActorRef(null, actorA, probe2.ref) // create reference to probe 2
      // acquire the internal reference for A
      actorA ! SelfAsk
      val akkaActorA = probe.expectMessageType[Self].self

      "keep unacknowledged releases in the knowledge set" in {
        actorA ! AckTestInit(probe2Ref) // send it the probe2 reference
        actorA ! Release
        actorA ! SnapshotAsk
        val aKnowledge = probe.expectMessageType[SnapshotReply].snapshot

        aKnowledge.knowledgeSet should contain (probe2Ref)
      }

      "not be able to release a reference twice" in {
        actorA ! SnapshotAsk
        val aKnowledge = probe.expectMessageType[SnapshotReply]
        actorA ! Release
        actorA ! SnapshotAsk
        probe.expectMessage(aKnowledge)
      }

      "remove acknowledged releases from the knowledge set" in {
        akkaActorA ! AckReleaseMsg(0)
        actorA ! SnapshotAsk
        val aKnowledge = probe.expectMessageType[SnapshotReply].snapshot

        aKnowledge.knowledgeSet should not contain (probe2Ref)
      }
    }

    "releasing created references" should {
      val actorA = testKit.spawn(ActorA())
      actorA ! SelfAsk
      val akkaActorA = probe.expectMessageType[Self].self
      val probe2Ref: ActorRef[AckTestMsg] = ActorRef(null, actorA, probe2.ref)
      var refs: Set[ActorRef[AckTestMsg]] = Set()

      "keep unacknowledged references in the knowledge set" in {
        actorA ! AckTestInit(probe2Ref)

        actorA ! CreateRefs
        refs = probe.expectMessageType[CreatedRefs].refSet

        actorA ! SnapshotAsk
        var aKnowledge = probe.expectMessageType[SnapshotReply].snapshot

        actorA ! Release  // release 0
        actorA ! ReleaseMultiple // release 1

        actorA ! SnapshotAsk
        aKnowledge = probe.expectMessageType[SnapshotReply].snapshot
        aKnowledge.knowledgeSet should contain (probe2Ref)
        refs.foreach(ref => {
          aKnowledge.knowledgeSet should contain (ref)
        })
      }
      "handle acknowledgement in sequence" in {
        akkaActorA ! AckReleaseMsg(1) // intentionally acknowledge its second release
        actorA ! SnapshotAsk
        var aKnowledge = probe.expectMessageType[SnapshotReply].snapshot
        // verify that set of references wasn't forgotten
        aKnowledge.knowledgeSet should contain (probe2Ref)
        refs.foreach(ref => {
          aKnowledge.knowledgeSet should contain (ref)
        })
        akkaActorA ! AckReleaseMsg(0) // acknowledge the first release
        actorA ! SnapshotAsk
        aKnowledge = probe.expectMessageType[SnapshotReply].snapshot
        // verify all those references got removed
        aKnowledge.knowledgeSet should not contain (probe2Ref)
        refs.foreach(ref => {
          aKnowledge.knowledgeSet should not contain (ref)
        })
      }
    }
  }

  object ActorA {
    def apply(): AkkaBehavior[AckTestMsg] = Behaviors.setupReceptionist(context => new ActorA(context))
  }

  class ActorA(context: ActorContext[AckTestMsg]) extends AbstractBehavior[AckTestMsg](context) {
    var internalProbeRef: ActorRef[AckTestMsg] = _
    var createdRefs: Set[ActorRef[AckTestMsg]] = Set()
    override def onMessage(msg: AckTestMsg): Behavior[AckTestMsg] = {
      msg match {
        case SnapshotAsk =>
          probe.ref ! SnapshotReply(context.snapshot())
          this
        case AckTestInit(probeRef) =>
          internalProbeRef = probeRef
          this
        case Release =>
          context.release(internalProbeRef)
          this
        case SelfAsk =>
          probe.ref ! Self(context.self.target)
          this
        case CreateRefs =>
          // create 3 refs to probe 2, using a dummy owner
          for (i <- 1 to 3) {
            createdRefs += context.createRef(internalProbeRef, ActorRef(null, null, null))
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
