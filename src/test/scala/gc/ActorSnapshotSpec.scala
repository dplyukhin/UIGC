package gc

import java.util.concurrent.TimeUnit

import akka.actor.testkit.typed.scaladsl.{ScalaTestWithActorTestKit, TestProbe}
import akka.actor.typed.{Behavior => AkkaBehavior}
import org.scalatest.wordspec.AnyWordSpecLike

import scala.concurrent.duration.FiniteDuration


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
// a message containing a single reference, used in above scenario
case class Ref(ref: ActorRef[KnowledgeTestMessage]) extends KnowledgeTestMessage with Message {
  override def refs: Iterable[AnyActorRef] = Iterable(ref)
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
//    val gcActorA = probe.expectMessageType[Ref]
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
      assert(newKnowledge.refs == aKnowledge.refs + gcRefAToB)
      assert(newKnowledge.owners == aKnowledge.owners)
      assert(newKnowledge.releasedRefs == aKnowledge.releasedRefs)
      assert(newKnowledge.sentCounts == aKnowledge.sentCounts)
      assert(newKnowledge.recvCounts == aKnowledge.recvCounts)
      // update knowledge for later tests
      aKnowledge = newKnowledge;

      // same for C
      actorA ! InitC
      gcRefAToC = probe.expectMessageType[Ref].ref
      actorA ! RequestKnowledge
      newKnowledge = probe.expectMessageType[Knowledge].actorSnapshot
      assert(newKnowledge.refs == aKnowledge.refs + gcRefAToC)
      assert(newKnowledge.owners == aKnowledge.owners)
      assert(newKnowledge.releasedRefs == aKnowledge.releasedRefs)
      assert(newKnowledge.sentCounts == aKnowledge.sentCounts)
      assert(newKnowledge.recvCounts == aKnowledge.recvCounts)
      aKnowledge = newKnowledge
    }

    "contain knowledge of message counts" in {
      // have A create and share a reference to C with B
      actorA ! ShareCWithB
      gcRefBToC = probe.expectMessageType[Ref].ref
      actorA ! RequestKnowledge
      // A's sent message count to B should increase by 1
      var newKnowledge = probe.expectMessageType[Knowledge].actorSnapshot
      assert(newKnowledge.refs == aKnowledge.refs)
      assert(newKnowledge.owners == aKnowledge.owners)
      assert(newKnowledge.sentCounts(gcRefAToB.token.get) ==
        aKnowledge.sentCounts(gcRefAToB.token.get) + 1)
      assert(newKnowledge.recvCounts == aKnowledge.recvCounts)
      // update knowledge for later tests
      aKnowledge = newKnowledge
    }

    "lose knowledge of released references" in {
      // have A release C
      actorA ! ForgetC
      actorA ! RequestKnowledge
      // A's sent message count to C should be removed
      var newKnowledge = probe.expectMessageType[Knowledge].actorSnapshot
      assert(newKnowledge.refs == aKnowledge.refs - gcRefAToC)
      assert(newKnowledge.owners == aKnowledge.owners)
      assert(newKnowledge.sentCounts == aKnowledge.sentCounts - gcRefAToC.token.get)
      assert(newKnowledge.recvCounts == aKnowledge.recvCounts)
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