package edu.illinois.osl.akka.gc.proxies

import akka.actor.typed.ActorRef
import scala.annotation.unchecked.uncheckedVariance
import edu.illinois.osl.akka.gc.interfaces.RefLike

case class AkkaRef[-T](ref: ActorRef[T]) extends RefLike[T] {
  override def !(msg: T): Unit = ref ! msg
}