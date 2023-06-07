package edu.illinois.osl.akka.gc.protocols.monotone

import akka.actor.typed.scaladsl.{AbstractBehavior, ActorContext, Behaviors, TimerScheduler}
import akka.actor.typed._

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
  def apply(): Behavior[Msg] = {
    Behaviors.withTimers(timers =>
      Behaviors.setup(ctx => new Bookkeeper(timers, ctx))
    )
  }
}

class Bookkeeper(timers: TimerScheduler[Bookkeeper.Msg], ctx: ActorContext[Bookkeeper.Msg])
extends AbstractBehavior[Bookkeeper.Msg](ctx) {
  import Bookkeeper._
  var total: Int = 0
  var MARKED: Boolean = false
  val shadows: java.util.HashMap[Name, Shadow] = new java.util.HashMap()

  println("Bookkeeper started!")
  timers.startTimerWithFixedDelay(Wakeup, Wakeup, 50.millis)

  override def onMessage(msg: Msg): Behavior[Msg] = {
    msg match {
      case Wakeup =>
        //println("Bookkeeper woke up!")
        val start = System.currentTimeMillis()
        val queue = ActorGC(ctx.system).Queue
        var count = 0
        var entry: Entry = queue.poll()
        while (entry != null) {
          count += 1
          shadows.put(entry.self, entry.shadow)
          entry.shadow.isLocal = true
          entry.shadow.isBusy = entry.isBusy
          entry.shadow.ref = entry.self
          GC.processEntry(entry)
          // Put back the entry
          entry.clean()
          Monotone.EntryPool.add(entry)
          // Try and get another one
          entry = queue.poll()
        }
        val end = System.currentTimeMillis()
        total += count
        // println(s"Bookeeper read $count entries in ${(end - start)}ms.")

        GC.trace(shadows, MARKED)
        MARKED = !MARKED

        Behaviors.same
    }
  }

  override def onSignal: PartialFunction[Signal, Behavior[Msg]] = {
    case PostStop =>
      println(s"Bookkeeper stopped! Read $total entries in total.")
      timers.cancel(Wakeup)
      this
  }
}

