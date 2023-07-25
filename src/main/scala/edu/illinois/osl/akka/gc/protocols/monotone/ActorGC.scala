package edu.illinois.osl.akka.gc.protocols.monotone

import akka.actor.{Actor, ActorIdentity, ActorRef, ActorSelection, ActorSystem, Address, ClassicActorSystemProvider, ExtendedActorSystem, Extension, ExtensionId, ExtensionIdProvider, Identify, Props, RootActorPath, Timers}
import akka.actor.typed.scaladsl.{AbstractBehavior, ActorContext, Behaviors, TimerScheduler}
import akka.actor.typed.receptionist.{Receptionist, ServiceKey}
import akka.cluster.{Cluster, MemberStatus}
import akka.cluster.ClusterEvent.{CurrentClusterState, MemberUp}
import edu.illinois.osl.akka.gc.interfaces.CborSerializable

import java.util.concurrent.ConcurrentLinkedQueue
import scala.concurrent.Await
import scala.concurrent.duration.{Duration, DurationInt}

object ActorGC extends ExtensionId[ActorGCImpl] with ExtensionIdProvider {
  override def lookup: ActorGC.type = ActorGC

  def createExtension(system: ExtendedActorSystem): ActorGCImpl =
    new ActorGCImpl(system)

  override def get(system: ActorSystem): ActorGCImpl = super.get(system)

  override def get(system: ClassicActorSystemProvider): ActorGCImpl = super.get(system)
}

class ActorGCImpl(system: ExtendedActorSystem) extends Extension {
  // This could be split into multiple queues if contention becomes high
  val Queue: ConcurrentLinkedQueue[Entry] = new ConcurrentLinkedQueue[Entry]()

  val bookkeeper: ActorRef =
    system.systemActorOf(
      Props[Bookkeeper]().withDispatcher("my-pinned-dispatcher"),
      "Bookkeeper")
}

object Bookkeeper {
  trait Msg

  /** Message produced by a timer, asking the garbage collector to scan its queue of incoming entries. */
  private case object Wakeup extends Msg

  /** Message produced by a timer, asking the garbage collector to start a wave. */
  private case object StartWave extends Msg

  /** Message sent from the garbage collector [[sender]], summarizing the entries it received recently. */
  private case class DeltaMsg(seqnum: Int, graph: DeltaGraph, sender: ActorRef) extends Msg with Serializable

  /** Message sent to a garbage collector, asking it to forward the given [[msg]] to the egress actor
   * at the given location. */
  case class ForwardToEgress(location: (Address, Address), msg: Gateway.Msg) extends Msg

  /** Message an ingress actor sends to its local GC when it finalizes an entry. */
  case class LocalIngressEntry(adjacentAddress: Address, entry: IngressEntry) extends Msg

  /** Message in which a garbage collector broadcasts local ingress entries to all other collectors. */
  private case class RemoteIngressEntry(location: (Address, Address), msg: IngressEntry) extends Msg

}

class Bookkeeper extends Actor with Timers {
  import Bookkeeper._
  private var totalEntries: Int = 0
  private var stopCount: Int = 0
  private val shadowGraph = new ShadowGraph()
  //private val testGraph = new ShadowGraph()
  private val undoLog = new UndoLog()

  private var deltaGraphID: Int = 0
  private var deltaGraph = new DeltaGraph()

  private val thisAddress: Address = Cluster(context.system).selfMember.address
  private var remoteGCs: Map[Address, ActorSelection] = Map()
  private val numNodes = Monotone.config.getInt("gc.crgc.num-nodes")
  private val waveFrequency: Int = Monotone.config.getInt("gc.crgc.wave-frequency")

  if (numNodes == 1) {
    start()
  }
  else {
    Cluster(context.system).subscribe(self, classOf[MemberUp])
    println("Waiting for other bookkeepers to join...")
  }

  private def start(): Unit = {
    // Start processing entries
    timers.startTimerWithFixedDelay(Wakeup, Wakeup, 50.millis)
    // Start triggering GC waves
    if (Monotone.collectionStyle == Monotone.Wave) {
      timers.startTimerWithFixedDelay(StartWave, StartWave, waveFrequency.millis)
    }
    // Start asking egress actors to finalize entries
    for ((addr, _) <- remoteGCs) {
      timers.startTimerWithFixedDelay(
        Egress.FinalizeEgressEntry,
        ForwardToEgress((thisAddress, addr), Egress.FinalizeEgressEntry),
        10.millis
      )
    }
    println("Bookkeeper started!")
  }

  private def finalizeDeltaGraph(): Unit = {
    for (gc <- remoteGCs.values) {
      gc ! DeltaMsg(deltaGraphID, deltaGraph, context.self)
    }
    deltaGraphID += 1
    deltaGraph = new DeltaGraph()
  }

  override def receive = {
      case state: CurrentClusterState =>
        state.members.filter(_.status == MemberStatus.Up).foreach(member => {
          if (member != Cluster(context.system).selfMember) {
            val addr = member.address
            val gc = context.actorSelection(RootActorPath(addr) / "system" / "Bookkeeper")
            println(s"${context.self} connected to $gc on ${addr}")
            remoteGCs = remoteGCs + (addr -> gc)
            if (remoteGCs.size + 1 == numNodes) {
              start()
            }
          }
        })

      case ForwardToEgress((sender, receiver), msg) =>
        if (sender == thisAddress) {
          println(s"GC sending $msg to ${remoteGCs(receiver)} at $receiver")
          remoteGCs(receiver) ! msg
        }
        else {
          remoteGCs(sender) ! ForwardToEgress((sender, receiver), msg)
        }

      case LocalIngressEntry(adjacentAddress, entry) =>
        println(s"GC got local ingress entry ${entry.id}")
        for ((addr, gc) <- remoteGCs; if addr != adjacentAddress) {
          gc ! RemoteIngressEntry((adjacentAddress, thisAddress), entry)
        }

      case RemoteIngressEntry(location, entry) =>
        println(s"GC got remote ingress entry $location - ${entry.id}")

      case DeltaMsg(id, delta, replyTo) =>
        println(s"GC ${id} deltas from $replyTo")
        shadowGraph.mergeDelta(delta)
        //var i = 0
        //while (i < delta.entries.size()) {
        //  testGraph.mergeRemoteEntry(delta.entries.get(i))
        //  i += 1;
        //}
        //shadowGraph.assertEquals(testGraph)

      case Wakeup =>
        //println("Bookkeeper woke up!")
        //var start = System.currentTimeMillis()
        val queue = ActorGC(context.system).Queue
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

      case StartWave =>
        shadowGraph.startWave()
  }

  override def postStop(): Unit = {
      println(s"Bookkeeper stopped! Read $totalEntries entries, produced $deltaGraphID delta-graphs, " +
        s"and stopped $stopCount (of ${shadowGraph.totalActorsSeen}) actors.")
      //shadowGraph.investigateLiveSet()
      timers.cancel(Wakeup)
  }
}

