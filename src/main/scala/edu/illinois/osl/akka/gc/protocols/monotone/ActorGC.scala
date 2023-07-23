package edu.illinois.osl.akka.gc.protocols.monotone

import akka.actor.typed.scaladsl.{AbstractBehavior, ActorContext, Behaviors, TimerScheduler}
import akka.actor.typed._
import akka.actor.typed.receptionist.{Receptionist, ServiceKey}
import edu.illinois.osl.akka.gc.interfaces.CborSerializable

import java.util.concurrent.ConcurrentLinkedQueue
import scala.concurrent.duration.DurationInt

object ActorGC extends ExtensionId[ActorGC] {
  def createExtension(system: ActorSystem[_]): ActorGC =
    new ActorGC(system)

  def get(system: ActorSystem[_]): ActorGC = apply(system)
}

class ActorGC(system: ActorSystem[_]) extends Extension {
  // This could be split into multiple queues if contention becomes high
  val Queue: ConcurrentLinkedQueue[Entry] = new ConcurrentLinkedQueue[Entry]()

  val bookkeeper: ActorRef[Bookkeeper.Msg] =
    system.systemActorOf(Bookkeeper(), "Bookkeeper",
      DispatcherSelector.fromConfig("my-pinned-dispatcher"))
}

object Bookkeeper {
  trait Msg
  private case class DeltaMsg(id: Int, graph: DeltaGraph, replyTo: ActorRef[Msg]) extends Msg with Serializable
  private case object Wakeup extends Msg
  private case object StartWave extends Msg
  private case class ReceptionistListing[T](listing: Receptionist.Listing) extends Msg
  private val BKServiceKey = ServiceKey[Msg]("Bookkeeper")

  def apply(): Behavior[Msg] = {
    Behaviors.withTimers(timers =>
      Behaviors.setup(ctx => new Bookkeeper(timers, ctx))
    )
  }
}

class Bookkeeper(timers: TimerScheduler[Bookkeeper.Msg], ctx: ActorContext[Bookkeeper.Msg])
extends AbstractBehavior[Bookkeeper.Msg](ctx) {
  import Bookkeeper._
  private var totalEntries: Int = 0
  private var stopCount: Int = 0
  private val shadowGraph = new ShadowGraph()
  //private val testGraph = new ShadowGraph()

  private var deltaGraphID: Int = 0
  private var deltaGraph = new DeltaGraph()

  private var remoteGCs: Set[ActorRef[Msg]] = Set()
  private val numNodes = Monotone.config.getInt("gc.crgc.num-nodes")
  private val waveFrequency: Int = Monotone.config.getInt("gc.crgc.wave-frequency")

  if (numNodes == 1) {
    start()
  }
  else {
    ctx.system.receptionist ! Receptionist.Register(BKServiceKey, ctx.self)
    val adapter = ctx.messageAdapter[Receptionist.Listing](ReceptionistListing.apply)
    ctx.system.receptionist ! Receptionist.Subscribe(BKServiceKey, adapter)
    println("Waiting for other bookkeepers to join...")
  }

  private def start(): Unit = {
    timers.startTimerWithFixedDelay(Wakeup, Wakeup, 50.millis)
    if (Monotone.collectionStyle == Monotone.Wave) {
      timers.startTimerWithFixedDelay(StartWave, StartWave, waveFrequency.millis)
    }
    println("Bookkeeper started!")
  }

  private def finalizeDeltaGraph(): Unit = {
    for (gc <- remoteGCs) {
      gc ! DeltaMsg(deltaGraphID, deltaGraph, ctx.self)
    }
    deltaGraphID += 1
    deltaGraph = new DeltaGraph()
  }

  override def onMessage(msg: Msg): Behavior[Msg] = {
    msg match {
      case ReceptionistListing(BKServiceKey.Listing(listing)) =>
        remoteGCs = remoteGCs ++ listing.filter(_ != ctx.self)
        if (remoteGCs.size + 1 == numNodes)
          start()
        this

      case DeltaMsg(id, delta, replyTo) =>
        //println(s"Got ${id} deltas from $replyTo")
        shadowGraph.mergeDelta(delta)
        //var i = 0
        //while (i < delta.entries.size()) {
        //  testGraph.mergeRemoteEntry(delta.entries.get(i))
        //  i += 1;
        //}
        //shadowGraph.assertEquals(testGraph)
        this

      case Wakeup =>
        //println("Bookkeeper woke up!")
        //var start = System.currentTimeMillis()
        val queue = ActorGC(ctx.system).Queue
        var count = 0
        var deltaCount = 0
        var entry: Entry = queue.poll()
        while (entry != null) {
          count += 1
          shadowGraph.mergeEntry(entry)
          //testGraph.mergeEntry(entry)
          //shadowGraph.assertEquals(testGraph)

          if (numNodes > 1) {
            val isFull = deltaGraph.mergeEntry(entry)
            if (isFull) {
              deltaCount += 1
              finalizeDeltaGraph()
            }
          }

          // Put back the entry
          entry.clean()
          Monotone.EntryPool.add(entry)
          // Try and get another one
          entry = queue.poll()
        }

        if (numNodes > 1 && deltaGraph.nonEmpty()) {
          deltaCount += 1
          finalizeDeltaGraph()
        }

        //var end = System.currentTimeMillis()
        //println(s"Scanned $count entries and $deltaCount delta-graphs in ${end - start}ms.")
        totalEntries += count

        //start = System.currentTimeMillis()
        count = shadowGraph.trace(true)
        //count = testGraph.trace(false)
        //shadowGraph.assertEquals(testGraph)
        //end = System.currentTimeMillis()
        //println(s"Found $count garbage actors in ${end - start}ms.")

        stopCount += count

        //println(s"Found $stopCount garbage actors so far.")

        Behaviors.same

      case StartWave =>
        shadowGraph.startWave()
        Behaviors.same
    }
  }

  override def onSignal: PartialFunction[Signal, Behavior[Msg]] = {
    case PostStop =>
      println(s"Bookkeeper stopped! Read $totalEntries entries, produced $deltaGraphID delta-graphs, " +
        s"and stopped $stopCount (of ${shadowGraph.totalActorsSeen}) actors.")
      //shadowGraph.investigateLiveSet()
      timers.cancel(Wakeup)
      this
  }
}

