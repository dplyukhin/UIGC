package gc.aggregator

import akka.actor.typed
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import gc.{ActorName, ActorRef, ActorSnapshot, Kill, Token}
import gc.detector.SimpleQuiescenceDetector

import scala.concurrent.duration.FiniteDuration

object Reaper {
  case object Harvest

  def apply(delay: FiniteDuration): Behavior[Harvest.type] =
    Behaviors.withTimers[Harvest.type] { timers =>
      timers.startTimerWithFixedDelay(Harvest, delay)

      Behaviors.receive { (context, _) =>
        println("Harvesting!")
        var snapshots = Map[ActorName, ActorSnapshot] ()
        SnapshotAggregator(context.system).snapshots.forEach { (name, snapshot) =>
          snapshots = snapshots + (name -> snapshot)
        }

        val garbage = new SimpleQuiescenceDetector[ActorName, Token, ActorRef[Nothing], ActorSnapshot].findGarbage(snapshots)
        println(s"Killing ${garbage.size} of ${snapshots.size} actors")
        for (actor <- garbage)
          actor ! Kill

        Behaviors.same
      }
    }
}
