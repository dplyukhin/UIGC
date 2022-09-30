package gc

import akka.actor.testkit.typed.scaladsl.{ScalaTestWithActorTestKit, TestProbe}
import akka.actor.typed.{PostStop, Signal, Behavior => AkkaBehavior}
import org.scalatest.wordspec.AnyWordSpecLike


object SelfMessagingSpec {
  trait NoRefsMessage extends Message {
    override def refs: Iterable[AnyActorRef] = Seq()
  }

  sealed trait SelfRefMsg extends Message

  final case class Countdown(n: Int) extends SelfRefMsg with NoRefsMessage
  final case class SelfRefTestInit(n: Int) extends SelfRefMsg with NoRefsMessage
  final case class SelfRefTerminated(n: Int) extends SelfRefMsg with NoRefsMessage
}

class SelfMessagingSpec extends ScalaTestWithActorTestKit with AnyWordSpecLike {
  import SelfMessagingSpec._

  val probe: TestProbe[SelfRefMsg] = testKit.createTestProbe[SelfRefMsg]()

  // In this test, the receptionist actor A spawns an actor B, tells it to
  // count down from a large number `n`, and releases it. It's very likely
  // that at some point, B will have no inverse acquaintances but it will
  // still have a nonempty mailqueue containing messages to itself. That
  // actor shouldn't terminate until its mail queue is empty.
  "Isolated actors" must {
    val actorA = testKit.spawn(ActorA(), "actorA")
    "not self-terminate when self-messages are in transit" in {
      val n = 10000
      actorA ! SelfRefTestInit(n)
      probe.expectMessage(SelfRefTerminated(n))
    }
  }


  object ActorA {
    def apply(): AkkaBehavior[SelfRefMsg] = Behaviors.setupReceptionist(context => new ActorA(context))
  }
  class ActorA(context: ActorContext[SelfRefMsg]) extends AbstractBehavior[SelfRefMsg](context) {
    val actorB: ActorRef[SelfRefMsg] = context.spawn(ActorB(), "actorB")

    override def onMessage(msg: SelfRefMsg): Behavior[SelfRefMsg] = {
      msg match {
        case SelfRefTestInit(n) =>
          actorB ! Countdown(n)
          context.release(actorB)
          this
        case _ =>
          this
      }
    }
  }

  object ActorB {
    def apply(): ActorFactory[SelfRefMsg] = {
      Behaviors.setup(context => new ActorB(context))
    }
  }
  class ActorB(context: ActorContext[SelfRefMsg]) extends AbstractBehavior[SelfRefMsg](context) {
    private var count = 0
    override def onMessage(msg: SelfRefMsg): Behavior[SelfRefMsg] = {
      msg match {
        case Countdown(n) =>
          if (n > 0) {
            context.self ! Countdown(n - 1)
            count += 1
          }
          this
        case _ =>
          this
      }
    }
    override def onSignal: PartialFunction[Signal, AkkaBehavior[GCMessage[SelfRefMsg]]] = {
      case PostStop =>
        probe.ref ! SelfRefTerminated(count)
        this
    }
  }
}
