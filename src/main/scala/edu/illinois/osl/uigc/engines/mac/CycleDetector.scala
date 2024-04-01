package edu.illinois.osl.uigc.engines.mac

import akka.actor.{Actor, ActorRef, ActorSelection, Address, RootActorPath, Timers}
import akka.cluster.ClusterEvent.{CurrentClusterState, MemberRemoved, MemberUp}
import akka.cluster.{Cluster, Member, MemberStatus}
import edu.illinois.osl.uigc.UIGC

import scala.concurrent.duration.DurationInt
import scala.jdk.CollectionConverters.IterableHasAsJava

object CycleDetector {
  /**
   * Message produced by a timer, asking the garbage collector to scan its queue of incoming
   * entries.
   */
  private case object Wakeup

  trait CycleDetectionProtocol

  /**
   * Message sent by a garbage-collected actor to the cycle detector, indicating that the actor
   * has blocked.
   * @param sender the actor that has blocked
   */
  case class BLK(sender: ActorRef) extends CycleDetectionProtocol

  /**
   * If an actor receives [[MAC.CNF]] and it hasn't received any messages since sending out a BLK
   * message, it sends this message to the cycle detector.
   *
   * @param sender the actor that received the CNF message
   * @param token the token received in the CNF message
   */
  case class ACK(sender: ActorRef, token: Int) extends CycleDetectionProtocol

}

class CycleDetector extends Actor with Timers {
  import CycleDetector._

  private val engine = UIGC(context.system).asInstanceOf[MAC]
  private var totalEntries: Int = 0

  timers.startTimerWithFixedDelay(Wakeup, Wakeup, 50.millis)
  println("Cycle detector started!")

  override def receive: PartialFunction[Any, Unit] = {

    case Wakeup =>
      // println("Cycle detector woke up!")

      val queue = engine.Queue
      var count = 0
      var deltaCount = 0
      var msg: CycleDetectionProtocol = queue.poll()
      while (msg != null) {
        count += 1

        // Try and get another one
        msg = queue.poll()
      }

      totalEntries += count

  }

  override def postStop(): Unit = {
    println(
      s"Cycle detector stopped! Read $totalEntries entries."
    )
    timers.cancel(Wakeup)
  }
}
