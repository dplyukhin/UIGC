package gc.aggregator

import akka.actor.typed.{ActorSystem, Extension, ExtensionId}

class SnapshotAggregator(system: ActorSystem[_]) extends Extension {

}

object SnapshotAggregator extends ExtensionId[SnapshotAggregator] {
  def createExtension(system: ActorSystem[_]): SnapshotAggregator =
    new SnapshotAggregator(system)

  def get(system: ActorSystem[_]): SnapshotAggregator =
    apply(system)
}
