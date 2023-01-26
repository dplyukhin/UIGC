package randomgraphs

import akka.actor.typed.{Behavior => AkkaBehavior, ActorSystem}
import edu.illinois.osl.akka.gc._
import edu.illinois.osl.akka.gc.protocol._
import edu.illinois.osl.akka.gc.interfaces.{Message, NoRefs}
import common.Benchmark
import scala.concurrent.Await
import scala.concurrent.duration.Duration
import com.typesafe.config.ConfigFactory
import akka.actor.typed.{Signal, PostStop}

object RandomGraphsAkkaGCActorBenchmark extends App with Benchmark {

  override val name: String = "random-graphs-gc"
  var stats: Statistics = _
  var system: ActorSystem[BenchmarkActor.Msg] = _

  Benchmark.runBenchmark(this)

  override def init(): Unit = {
    stats = new Statistics
    if (RandomGraphsConfig.IsSequential) {
      val conf = ConfigFactory.parseString("""
        akka.actor.default-dispatcher.fork-join-executor.parallelism-min = 1
        akka.actor.default-dispatcher.fork-join-executor.parallelism-max = 1
      """)
      system = ActorSystem(BenchmarkActor.createRoot(stats), name, ConfigFactory.load(conf))
    }
    else {
      system = ActorSystem(BenchmarkActor.createRoot(stats), name)
    }
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
    final case class Link(ref: Refob[Msg]) extends Msg {
      def refs = Seq(ref)
    }
    final case class Ping() extends Msg {
      def refs = Seq()
    }

    def apply(statistics: Statistics): ActorFactory[Msg] = {
      Behaviors.setup(context => new BenchmarkActor(context, statistics))
    }

    def createRoot(statistics: Statistics): AkkaBehavior[Msg] = {
      Behaviors.setupRoot(context => {
        if (RandomGraphsConfig.ShouldLog) 
          println("\nSpawned root actor\n")
        new BenchmarkActor(context, statistics)
      })
    }
  }

  private class BenchmarkActor(context: ActorContext[BenchmarkActor.Msg], stats: Statistics)
    extends AbstractBehavior[BenchmarkActor.Msg](context) 
    with RandomGraphsActor[Refob[BenchmarkActor.Msg]] {

    import BenchmarkActor._


    override val statistics: Statistics = stats

    override def spawn(): Refob[Msg] = {
      val child = context.spawnAnonymous(BenchmarkActor(stats))
      if (RandomGraphsConfig.ShouldLog) 
        println(s"${context.name} spawned ${child.rawActorRef}")
      child
    }

    override def linkActors(owner: Refob[Msg], target: Refob[Msg]): Unit = {
      val ref = context.createRef(target, owner)
      owner ! Link(ref)
      if (RandomGraphsConfig.ShouldLog) 
        println(s"${context.name} sent Link($ref) to ${owner.rawActorRef}")
      super.linkActors(owner, target)
    }

    override def forgetActor(ref: Refob[Msg]): Unit = {
      context.release(ref)
      if (RandomGraphsConfig.ShouldLog) 
        println(s"${context.name} released ${ref.rawActorRef}")
      super.forgetActor(ref)
    }

    override def ping(ref: Refob[Msg]): Unit = {
      ref ! Ping()
      if (RandomGraphsConfig.ShouldLog) 
        println(s"${context.name} pinging ${ref.rawActorRef}")
      super.ping(ref)
    }

    override def uponMessage(msg: Msg): Behavior[Msg] = {
      if (RandomGraphsConfig.ShouldLog) 
        println(s"${context.name} got message: $msg")

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

    override def uponSignal: PartialFunction[Signal,Behavior[Msg]] = {
      case PostStop =>
        if (RandomGraphsConfig.LogStats) 
          statistics.terminatedCount.incrementAndGet()
        this
    }
  }
}
