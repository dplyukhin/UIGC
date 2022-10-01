package randomgraphs

import akka.actor.typed.scaladsl.{AbstractBehavior, ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior, ActorSystem}
import common.Benchmark
import scala.concurrent.Await
import scala.concurrent.duration.Duration

object RandomGraphsAkkaBenchmark extends App with Benchmark {

  override val name: String = "random-graphs"
  var stats: Statistics = _
  var system: ActorSystem[BenchmarkActor.Msg] = _

  Benchmark.runBenchmark(this)

  override def init(): Unit = {
    stats = new Statistics
    system = ActorSystem(BenchmarkActor(stats), name)
  }

  def cleanup(): Unit = {
    system.terminate()
    Await.ready(system.whenTerminated, Duration.Inf)
  }

  def run(): Unit = {
    for (_ <- 1 to RandomGraphsConfig.NumberOfPingsSent) {
      system ! BenchmarkActor.Ping()
    }
    try {
      stats.latch.await()
      println(stats)
    } catch {
      case ex: InterruptedException =>
        ex.printStackTrace()
    }
  }

  object BenchmarkActor {
    sealed trait Msg
    final case class Link(ref: ActorRef[Msg]) extends Msg
    final case class Ping() extends Msg

    def apply(statistics: Statistics): Behavior[Msg] = {
      Behaviors.setup(context => new BenchmarkActor(context, statistics))
    }
  }

  private class BenchmarkActor(context: ActorContext[BenchmarkActor.Msg], stats: Statistics)
    extends AbstractBehavior[BenchmarkActor.Msg](context)
        with RandomGraphsActor[ActorRef[BenchmarkActor.Msg]] {

    import BenchmarkActor._

    override val statistics: Statistics = stats
    override val debug: Boolean = true

    override def spawn(): ActorRef[Msg] =
      context.spawnAnonymous(BenchmarkActor(stats))

    override def linkActors(owner: ActorRef[Msg], target: ActorRef[Msg]): Unit = {
      owner ! Link(target)
      super.linkActors(owner, target)
    }

    override def ping(ref: ActorRef[Msg]): Unit = {
      ref ! Ping()
      super.ping(ref)
    }

    override def onMessage(msg: Msg): Behavior[Msg] = {
      msg match {
        case Link(ref) =>
          acquaintances += ref
          doSomeActions()
          Behaviors.same

        case Ping() =>
          doSomeActions()
          Behaviors.same
      }
    }
  }
}
