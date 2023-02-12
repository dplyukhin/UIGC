package edu.illinois.osl.akka.gc.protocols.monotone

import akka.actor.typed.{ActorSystem, Extension, ExtensionId}
import scala.concurrent.ExecutionContext.Implicits.global
import java.util.concurrent.ConcurrentLinkedQueue

object Bookkeeper extends ExtensionId[Bookkeeper] {
  def createExtension(system: ActorSystem[_]): Bookkeeper =
    new Bookkeeper(system)

  def get(system: ActorSystem[_]): Bookkeeper = apply(system)
}

class Bookkeeper(system: ActorSystem[_]) extends Extension {
  println("Bookkeeper started!")
  val Queue: ConcurrentLinkedQueue[Entry] = new ConcurrentLinkedQueue[Entry]()
  // TODO Start a thread that periodically wakes up and scans the queue
  // TODO When the system is terminated, stop the thread
  for (_ <- system.whenTerminated) {
    println("Bookkeeper stopped!")
  }
}
