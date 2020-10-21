package gc

import akka.actor.typed.scaladsl.{AbstractBehavior => AkkaAbstractBehavior}
import akka.actor.typed.{Behavior => AkkaBehavior}

import scala.concurrent.duration.{DurationDouble, DurationInt, FiniteDuration}

case object SnapshotTimer

/**
 * Parent class for behaviors that implement the GC message protocol.
 */
abstract class AbstractBehavior[T <: Message](context: ActorContext[T])
  extends AkkaAbstractBehavior[GCMessage[T]](context.context) {

  startSnapshotTimer()

  /** The number of snapshots this actor has taken so far */
  private var snapshotCount = 0

  /** True iff this actor has not received a message since taking a snapshot */
  private var hasBeenInactive = false

  private def startSnapshotTimer(): Unit = {
    for (timers <- context.timers) {
      timers.startSingleTimer(SnapshotTimer, TakeSnapshot, snapshotDelay(snapshotCount))
    }
  }

  /**
   * This method is used to determine how long an actor should wait after taking
   * a snapshot before it takes another one. The timer will only be initiated
   * after this actor receives a message; actors will not take snapshots if their
   * local state has not changed.
   *
   * @param snapshotCount The number of snapshots this actor has taken so far.
   * @return The mimimum amount of time this actor should wait before taking another snapshot.
   */
  def snapshotDelay(snapshotCount: Int): FiniteDuration = {
    // The maximum delay
    val maxDuration = 2.0
    // How quickly an actor reaches the maximum delay
    val steepness = 1.5
    // This determines the initial delay and when the maximum delay is reached;
    // a large negative value means a small initial delay
    val shift = -3

    // Use a logistic function to gradually increase the snapshot delay,
    // up to a certain maximum
    (maxDuration / (1.0 + scala.math.pow(2, steepness * (snapshotCount - shift)))).seconds
  }

  def onMessage(msg: T): Behavior[T]

  // Our implementation of `onMessage`, which forwards any application-level
  // messages to the `onMessage(msg: T)` method, implemented by the subclass.
  final def onMessage(msg: GCMessage[T]): AkkaBehavior[GCMessage[T]] = {

    if (hasBeenInactive) {
      hasBeenInactive = false
      startSnapshotTimer()
    }

    msg match {
      case ReleaseMsg(releasing, created) =>
        context.handleRelease(releasing, created)
        context.tryTerminate()
      case AppMsg(payload, token) =>
        context.handleMessage(payload.refs, token)
        onMessage(payload)
      case SelfCheck =>
        context.handleSelfCheck()
        context.tryTerminate()
      case TakeSnapshot =>
        // println(s"${context.self} Taking a snapshot: ${context.snapshot}")
        snapshotCount += 1
        // do not restart the timer yet - wait until we have received another message,
        // and therefore possibly updated the snapshot's contents
        hasBeenInactive = true
        this
    }
  }
}
