package gc.aggregator

import java.util.concurrent.ConcurrentHashMap

import akka.actor.typed.{ActorSystem, Extension, ExtensionId, ActorRef}

class SnapshotAggregator(system: ActorSystem[_]) extends Extension {
  import SnapshotAggregator._

  val generation: Generation = ConcurrentHashMap.newKeySet[ActorRef[_]]()
}

object SnapshotAggregator extends ExtensionId[SnapshotAggregator] {
  /** A concurrent set of actor refs */
  type Generation = ConcurrentHashMap.KeySetView[ActorRef[Nothing], java.lang.Boolean]

  def createExtension(system: ActorSystem[_]): SnapshotAggregator =
    new SnapshotAggregator(system)

  def get(system: ActorSystem[_]): SnapshotAggregator =
    apply(system)
}
