package edu.illinois.osl.akka.gc.protocols.monotone

import akka.actor.typed.scaladsl.{AbstractBehavior, ActorContext, Behaviors, TimerScheduler}
import akka.actor.typed._
import edu.illinois.osl.akka.gc.interfaces.RefLike

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
  case object Wakeup extends Msg
  case object StartWave extends Msg
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
  private val gc = new GC()


  println("Bookkeeper started!")
  timers.startTimerWithFixedDelay(Wakeup, Wakeup, 50.millis)
  if (Monotone.collectionStyle == Monotone.Wave) {
    val waveFrequency: Int = Monotone.config.getInt("gc.crgc.wave-frequency")
    timers.startTimerWithFixedDelay(StartWave, StartWave, waveFrequency.millis)
  }

  override def onMessage(msg: Msg): Behavior[Msg] = {
    msg match {
      case Wakeup =>
        //println("Bookkeeper woke up!")
        //var start = System.currentTimeMillis()
        val queue = ActorGC(ctx.system).Queue
        var count = 0
        var entry: Entry = queue.poll()
        while (entry != null) {
          count += 1
          gc.processEntry(entry)
          // Put back the entry
          entry.clean()
          Monotone.EntryPool.add(entry)
          // Try and get another one
          entry = queue.poll()
        }
        //var end = System.currentTimeMillis()
        //println(s"Scanned $count entries in ${end - start}ms.")
        totalEntries += count

        //start = System.currentTimeMillis()
        count = gc.trace()
        //end = System.currentTimeMillis()
        //println(s"Found $count garbage actors in ${end - start}ms.")

        stopCount += count

        //println(s"Found $stopCount garbage actors so far.")

        Behaviors.same

      case StartWave =>
        gc.startWave()
        Behaviors.same
    }
  }

  override def onSignal: PartialFunction[Signal, Behavior[Msg]] = {
    case PostStop =>
      println(s"Bookkeeper stopped! Read $totalEntries entries and stopped $stopCount actors.")
      timers.cancel(Wakeup)
      this
  }
}

