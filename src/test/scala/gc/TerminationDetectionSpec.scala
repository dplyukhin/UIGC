package gc

import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef => AkkaActorRef, Behavior => AkkaBehavior}
import org.scalatest.wordspec.AnyWordSpecLike

class TerminationDetectionSpec extends ScalaTestWithActorTestKit with AnyWordSpecLike {
  val dummyActor: AkkaBehavior[Any] = Behaviors.receive { (_, _) => Behaviors.same }
  // starring...
  val A: AkkaActorRef[Any] = testKit.spawn(dummyActor, "A")
  val B: AkkaActorRef[Any] = testKit.spawn(dummyActor, "B")
  val C: AkkaActorRef[Any] = testKit.spawn(dummyActor, "C")
  // for convenience
  val aToken: Token = Token(A, 0)
  val bToken: Token = Token(B, 0)
  val cToken: Token = Token(C, 0)
  val selfA: ActorRef[Message] = ActorRef(Some(aToken), Some(A), A)
  val selfB: ActorRef[Message] = ActorRef(Some(bToken), Some(B), B)

  "Basic cycles" should {
    // A has ref x:A->B and B has ref y:B->A.
    val xToken = Token(A, 1)
    val yToken = Token(B, 1)
    val x = ActorRef(Some(xToken), Some(A), B) // x: a->b
    val y = ActorRef(Some(yToken), Some(B), A) // y: b->a
    "be detected when they appear blocked" in {
      // A has sent 1 message along x and B has sent 2 along y. The message counts agree.
      val dummyA: ActorSnapshot = ActorSnapshot(
        refs = Set(selfA, x),
        owners = Set(selfA, y),
        created = Seq(),
        releasedRefs = Set(),
        sentCounts = Map(aToken -> 0, xToken -> 1),
        recvCounts = Map(aToken -> 0, yToken -> 2)
      )
      val dummyB: ActorSnapshot = ActorSnapshot(
        refs = Set(selfB, y),
        owners = Set(selfB, x),
        created = Seq(),
        releasedRefs = Set(),
        sentCounts = Map(bToken -> 0, yToken -> 2),
        recvCounts = Map(bToken -> 0, xToken -> 1)
      )

      val terminated = TerminationDetector.findTerminated(Map(A -> dummyA, B -> dummyB))
      terminated should contain only(A, B)
    }
    "be ignored when they don't appear blocked" in {
      // A has sent 1 message along x and B has sent 2 along y. A has yet to receive one of those two, so it should not be terminated.
      val dummyA: ActorSnapshot = ActorSnapshot(
        refs = Set(selfA, x),
        owners = Set(selfA, y),
        created = Seq(),
        releasedRefs = Set(),
        sentCounts = Map(aToken -> 0, xToken -> 1),
        recvCounts = Map(aToken -> 0, yToken -> 1)
      )
      val dummyB: ActorSnapshot = ActorSnapshot(
        refs = Set(selfB, y),
        owners = Set(selfB, x),
        created = Seq(),
        releasedRefs = Set(),
        sentCounts = Map(bToken -> 0, yToken -> 2),
        recvCounts = Map(bToken -> 0, xToken -> 1)
      )

      val terminated = TerminationDetector.findTerminated(Map(A -> dummyA, B -> dummyB))
//      terminated should contain only(B)
      terminated shouldBe empty
    }
  }
  // TODO: tests for more complex configurations, such as
}
