package edu.illinois.osl.akka.gc

import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import akka.actor.typed.{PostStop, Signal}
import akka.actor.typed.scaladsl.TimerScheduler
import edu.illinois.osl.akka.gc.interfaces.Message
import org.scalatest.wordspec.AnyWordSpecLike

import java.util.concurrent.{CountDownLatch, TimeUnit}
import java.util.concurrent.atomic.AtomicInteger
import scala.concurrent.duration.{DurationInt, FiniteDuration}
import scala.util.Random

/** This spec spawns around 10 actors in a random configuration and then waits until they have all
  * been collected. If the GC is unsound, it is likely to throw an exception. If it is incomplete,
  * the test will time out.
  */
class RandomSpec extends ScalaTestWithActorTestKit with AnyWordSpecLike {
  val MAX_ACTORS = 1000
  val PING_FREQUENCY: FiniteDuration = 1.millis
  val SpawnCounter: AtomicInteger = new AtomicInteger()
  val TerminateCounter: CountDownLatch = new CountDownLatch(MAX_ACTORS)

  sealed trait Msg extends Message

  final case class Link(ref: ActorRef[Msg]) extends Msg {
    def refs: Seq[ActorRef[Msg]] = Seq(ref)
  }

  final case class Ping() extends Msg {
    def refs: Seq[Nothing] = Seq()
  }

  private class RandomActor(
      context: ActorContext[Msg],
      timers: TimerScheduler[Msg]
  ) extends AbstractBehavior[Msg](context) {

    private var acquaintances: Set[ActorRef[Msg]] = Set()

    override def onMessage(msg: Msg): Behavior[Msg] =
      msg match {
        case Link(ref) =>
          acquaintances += ref
          doSomeActions()
          this

        case Ping() =>
          doSomeActions()
          this
      }

    private def doSomeActions(): Unit = {
      if (SpawnCounter.get() >= MAX_ACTORS) {
        if (timers != null) {
          // Root actor stops the timer and releases all acquaintances, allowing them to become garbage
          // once they stop receiving messages.
          println(s"Spawned $MAX_ACTORS actors. Releasing all acquaintances...")
          context.release(acquaintances)
          acquaintances = Set()
          timers.cancelAll()
        }
        return
      }
      doSomething()
      doSomething()
    }

    private def doSomething(): Unit = {
      val p = Random.nextDouble()
      if (p < 0.2) {
        val count = SpawnCounter.incrementAndGet()
        if (count <= MAX_ACTORS) {
          acquaintances += context.spawnAnonymous(RandomActor())
        }
      } else if (p < 0.4 && acquaintances.nonEmpty) {
        val owner = randomItem(acquaintances)
        val target = randomItem(acquaintances)
        owner ! Link(context.createRef(target, owner))
      } else if (p < 0.6 && acquaintances.nonEmpty) {
        val actor = randomItem(acquaintances)
        acquaintances = acquaintances - actor
        context.release(actor)
      } else if (p < 0.8 && acquaintances.nonEmpty) {
        randomItem(acquaintances) ! Ping()
      }
    }

    /** Pick a random item from the set, assuming the set is nonempty. */
    private def randomItem[T](items: Set[T]): T = {
      val i = Random.nextInt(items.size)
      items.view.slice(i, i + 1).head
    }

    override def onSignal: PartialFunction[Signal, Behavior[Msg]] = {
      case PostStop =>
        TerminateCounter.countDown()
        this
    }
  }

  object RandomActor {
    def createRoot(): unmanaged.Behavior[Msg] =
      Behaviors.withTimers[Msg] { timers =>
        Behaviors.setupRoot { context =>
          timers.startTimerAtFixedRate((), Ping(), PING_FREQUENCY)
          new RandomActor(context, timers)
        }
      }

    def apply(): ActorFactory[Msg] =
      Behaviors.setup(context => new RandomActor(context, null))
  }

  // Here's the test!
  "GC" must {
    testKit.spawn(RandomActor.createRoot(), "root")

    "eventually detect all garbage" in {
      TerminateCounter.await()
    }
  }

}
