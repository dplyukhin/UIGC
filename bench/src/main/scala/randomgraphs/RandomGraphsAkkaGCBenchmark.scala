package randomgraphs

import akka.actor.typed.{Behavior => AkkaBehavior, ActorSystem}
import gc._
import common.Benchmark
import scala.concurrent.Await
import scala.concurrent.duration.Duration

object RandomGraphsAkkaGCActorBenchmark extends App with Benchmark {

  override val name: String = "random-graphs-gc"
  var stats: Statistics = _
  var system: ActorSystem[BenchmarkActor.Msg] = _

  Benchmark.runBenchmark(this)

  override def init(): Unit = {
    stats = new Statistics
    system = ActorSystem(BenchmarkActor.createRoot(stats), name)
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
    sealed trait Msg extends Message
    final case class Link(ref: ActorRef[Msg]) extends Msg {
      def refs = Seq(ref)
    }
    final case class Ping() extends Msg {
      def refs = Seq()
    }

    def apply(statistics: Statistics): ActorFactory[Msg] = {
      Behaviors.setup(context => new BenchmarkActor(context, statistics))
    }

    def createRoot(statistics: Statistics): AkkaBehavior[Msg] = {
      Behaviors.setupReceptionist(context => new BenchmarkActor(context, statistics))
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
      val ref = context.createRef(target, owner)
      owner ! Link(ref)
      super.linkActors(owner, target)
    }

    override def forgetActor(ref: ActorRef[Msg]): Unit = {
      context.release(ref)
      super.forgetActor(ref)
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
          this

        case Ping() =>
          doSomeActions()
          this
      }
    }
  }
}
