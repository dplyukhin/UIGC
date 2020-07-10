package gc

import akka.actor.testkit.typed.scaladsl.{ScalaTestWithActorTestKit, TestProbe}
import akka.actor.typed.{Behavior => AkkaBehavior}
import org.scalatest.wordspec.AnyWordSpecLike


sealed trait KnowledgeTestMessage extends Message

// sent by tester to an actor to get Knowledge from
case object RequestKnowledge extends KnowledgeTestMessage with NoRefsMessage
// response message with an actor's knowledge set
case class Knowledge(actorSnapshot: ActorSnapshot) extends KnowledgeTestMessage with NoRefsMessage
// sent by tester to tell actor A to create B and C respectively
case object InitB extends KnowledgeTestMessage with NoRefsMessage
case object InitC extends KnowledgeTestMessage with NoRefsMessage
// sent by tester to have A share a reference to C with B
case object ShareCWithB extends KnowledgeTestMessage with NoRefsMessage
case class TellB(msg: KnowledgeTestMessage) extends KnowledgeTestMessage with NoRefsMessage
case class TellC(msg: KnowledgeTestMessage) extends KnowledgeTestMessage with NoRefsMessage
// a message containing a single reference, used in above scenario
case class Ref(ref: ActorRef[KnowledgeTestMessage]) extends KnowledgeTestMessage with Message {
  override def refs: Iterable[AnyRefOb] = Iterable(ref)
}
// sent by tester to tell A to release C
case object ForgetC extends KnowledgeTestMessage with NoRefsMessage

class ActorSnapshotSpec extends ScalaTestWithActorTestKit with AnyWordSpecLike {
  val probe: TestProbe[KnowledgeTestMessage] = testKit.createTestProbe[KnowledgeTestMessage]()
  var aKnowledge: ActorSnapshot = _ // A's knowledge set, gets updated as the test progresses
  var gcRefAToC: ActorRef[KnowledgeTestMessage] = _ // A's reference to C
  var gcRefAToB: ActorRef[KnowledgeTestMessage] = _ // A's reference to B
  var gcRefBToC: ActorRef[KnowledgeTestMessage] = _ // B's reference to C

  "Knowledge sets" must {
    val actorA = testKit.spawn(ActorA(), "actorA")
    // get A's initial knowledge
    actorA ! RequestKnowledge
    aKnowledge = probe.expectMessageType[Knowledge].actorSnapshot

    "expand when actors are spawned" in {
      // have A spawn B and get that reference
      actorA ! InitB
      gcRefAToB = probe.expectMessageType[Ref].ref
      // check that the knowledge set has only gained that reference
      actorA ! RequestKnowledge
      var newKnowledge = probe.expectMessageType[Knowledge].actorSnapshot
      newKnowledge.refs shouldEqual aKnowledge.refs + gcRefAToB
      newKnowledge.owners shouldEqual aKnowledge.owners
      newKnowledge.releasedRefs shouldEqual aKnowledge.releasedRefs
      newKnowledge.sentCounts shouldEqual aKnowledge.sentCounts
      newKnowledge.recvCounts shouldEqual aKnowledge.recvCounts
      // update knowledge for later tests
      aKnowledge = newKnowledge;

      // same for C
      actorA ! InitC
      gcRefAToC = probe.expectMessageType[Ref].ref
      actorA ! RequestKnowledge
      newKnowledge = probe.expectMessageType[Knowledge].actorSnapshot
      newKnowledge.refs shouldEqual aKnowledge.refs + gcRefAToC
      newKnowledge.owners shouldEqual aKnowledge.owners
      newKnowledge.releasedRefs shouldEqual aKnowledge.releasedRefs
      newKnowledge.sentCounts shouldEqual aKnowledge.sentCounts
      newKnowledge.recvCounts shouldEqual aKnowledge.recvCounts
      aKnowledge = newKnowledge
    }

    "have knowledge of creations" in {
      // have A create and share a reference to C with B
      actorA ! ShareCWithB
      // A's created map now has gcRefAToC -> Seq(gcRefBtoC)
      // A's sent count for gcRefAToB is now 1, B's receive count is 1
      gcRefBToC = probe.expectMessageType[Ref].ref
      actorA ! RequestKnowledge
      val newKnowledge = probe.expectMessageType[Knowledge].actorSnapshot
      newKnowledge.created should contain (gcRefBToC)
      newKnowledge.refs shouldEqual aKnowledge.refs
      newKnowledge.owners shouldEqual aKnowledge.owners
      // A's sent message count to B should increase by 1
      // update knowledge for later tests
      aKnowledge = newKnowledge
    }

    "have knowledge of message counts" in {
      actorA ! TellB(RequestKnowledge)
      // A's sent count on gcRefAToB is now 2, B's receive count is 2
      val bKnowledge = probe.expectMessageType[Knowledge].actorSnapshot
      bKnowledge.recvCounts(gcRefAToB.token.get) shouldEqual 2

      actorA ! RequestKnowledge
      val newKnowledge = probe.expectMessageType[Knowledge].actorSnapshot
      // A's sent message count to B should increase by 1 from what it was previously
      newKnowledge.sentCounts(gcRefAToB.token.get) shouldEqual
        aKnowledge.sentCounts(gcRefAToB.token.get) + 1
      newKnowledge.recvCounts shouldEqual aKnowledge.recvCounts
      // update knowledge for later tests
      aKnowledge = newKnowledge
    }

    "have knowledge of released owners" in {
      // B releases C, and since C didn't know about B, it gets added to released_owners
      actorA ! TellB(ForgetC)
      // now get C's knowledge set
      Thread.sleep(10) // don't want to take the snapshot too soon or it will be wrong!
      actorA ! TellC(RequestKnowledge)

      val cKnowledge = probe.expectMessageType[Knowledge].actorSnapshot
      // B to C should be in the released owners set
      cKnowledge.releasedRefs should contain (gcRefBToC)
      // B to C should have been removed from/not in the receive count
      cKnowledge.recvCounts shouldNot contain (gcRefBToC.token.get)

      // update A's knowledge since message counts changed
      actorA ! RequestKnowledge
      aKnowledge = probe.expectMessageType[Knowledge].actorSnapshot
    }

    "lose knowledge of released references" in {
      // have A release C
      actorA ! ForgetC
      actorA ! RequestKnowledge

      var newKnowledge = probe.expectMessageType[Knowledge].actorSnapshot
      newKnowledge.refs shouldNot contain (gcRefAToC)
      newKnowledge.owners shouldEqual aKnowledge.owners
      // A's sent message count to C should be removed
      newKnowledge.sentCounts shouldNot contain (gcRefAToC.token.get)
      newKnowledge.recvCounts shouldEqual aKnowledge.recvCounts
      // update knowledge for later tests
      aKnowledge = newKnowledge
    }
  }

  object ActorA {
    def apply(): AkkaBehavior[KnowledgeTestMessage] = Behaviors.setupReceptionist(context => new ActorA(context))
  }
  object ActorB {
    def apply(): ActorFactory[KnowledgeTestMessage] = {
      Behaviors.setup(context => new ActorB(context))
    }
  }
  object ActorC {
    def apply(): ActorFactory[KnowledgeTestMessage] = {
      Behaviors.setup(context => new ActorC(context))
    }
  }

  class ActorA(context: ActorContext[KnowledgeTestMessage]) extends AbstractBehavior[KnowledgeTestMessage](context) {
    var actorB: ActorRef[KnowledgeTestMessage] = _
    var actorC: ActorRef[KnowledgeTestMessage] = _

    override def onMessage(msg: KnowledgeTestMessage): Behavior[KnowledgeTestMessage] = {
      msg match {
        case InitB =>
          actorB = context.spawn(ActorB(), "actorB")
          probe.ref ! Ref(actorB)
          this
        case InitC =>
          actorC = context.spawn(ActorC(), "actorC")
          probe.ref ! Ref(actorC)
          this
        case ShareCWithB =>
          val refToShare = context.createRef(actorC, actorB)
          actorB ! Ref(refToShare)
          probe.ref ! Ref(refToShare)
          this
        case TellB(msg) =>
          actorB ! msg
          this
        case TellC(msg) =>
          actorC ! msg
          this
        case ForgetC =>
          context.release(Iterable(actorC))
          this
        case RequestKnowledge =>
          probe.ref ! Knowledge(context.snapshot())
          this
        case _ => this
      }
    }
  }
  class ActorB(context: ActorContext[KnowledgeTestMessage]) extends AbstractBehavior[KnowledgeTestMessage](context) {
    var actorC: ActorRef[KnowledgeTestMessage]= _

    override def onMessage(msg: KnowledgeTestMessage): Behavior[KnowledgeTestMessage] = {
      msg match {
        case Ref(ref) =>
          actorC = ref
          this
        case RequestKnowledge =>
          probe.ref ! Knowledge(context.snapshot())
          this
        case ForgetC =>
          context.release(Iterable(actorC))
          this
        case _ => this
      }
    }
  }
  class ActorC(context: ActorContext[KnowledgeTestMessage]) extends AbstractBehavior[KnowledgeTestMessage](context) {
    override def onMessage(msg: KnowledgeTestMessage): Behavior[KnowledgeTestMessage] = {
      msg match {
        case RequestKnowledge =>
          probe.ref ! Knowledge(context.snapshot())
          this
        case _ => this
      }
    }
  }
}