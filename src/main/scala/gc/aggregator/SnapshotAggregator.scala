package gc.aggregator

import java.util.concurrent.ConcurrentHashMap

import akka.actor.typed
import akka.actor.typed.{ActorSystem, Extension, ExtensionId}
import gc.{ActorSnapshot, GCMessage}

import scala.concurrent.duration.DurationInt

class SnapshotAggregator(system: ActorSystem[_]) extends Extension {
  import SnapshotAggregator._

  val generation: Generation = ConcurrentHashMap.newKeySet()
  val snapshots: ConcurrentHashMap[ActorName, ActorSnapshot] = new ConcurrentHashMap()

  system.systemActorOf(SnapshotRequester(1.second), "SnapshotRequester")

  def register(actor: ActorName): Unit = {
    generation.add(actor)
  }
  def put(actor: ActorName, snapshot: ActorSnapshot): Unit = {
    snapshots.put(actor, snapshot)
  }
  def unregister(actor: ActorName): Unit = {
    generation.remove(actor)
    snapshots.remove(actor)
  }
}

object SnapshotAggregator extends ExtensionId[SnapshotAggregator] {
  type ActorName = typed.ActorRef[GCMessage[Nothing]]
  /** A concurrent set of actor refs */
  type Generation = ConcurrentHashMap.KeySetView[ActorName, java.lang.Boolean]

  def createExtension(system: ActorSystem[_]): SnapshotAggregator =
    new SnapshotAggregator(system)

  def get(system: ActorSystem[_]): SnapshotAggregator =
    apply(system)
}
