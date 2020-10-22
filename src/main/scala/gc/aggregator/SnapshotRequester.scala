package gc.aggregator

import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import gc.TakeSnapshot

import scala.concurrent.duration.FiniteDuration


object SnapshotRequester {
  case object RequestSnapshots

  def apply(delay: FiniteDuration): Behavior[RequestSnapshots.type] =
    Behaviors.withTimers[RequestSnapshots.type] { timers =>
      timers.startTimerWithFixedDelay(RequestSnapshots, delay)

      Behaviors.receive { (context, _) =>
        SnapshotAggregator(context.system).generation.forEach { actor =>
          actor ! TakeSnapshot
        }
        Behaviors.same
      }
    }
}
