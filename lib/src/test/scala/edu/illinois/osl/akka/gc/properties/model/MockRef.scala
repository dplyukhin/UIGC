package edu.illinois.osl.akka.gc.properties.model

import edu.illinois.osl.akka.gc.interfaces._
import scala.annotation.unchecked.uncheckedVariance

case class MockRef[-T](n: Int) extends RefLike[T] {
  var messages: List[T @uncheckedVariance] = Nil
  override def !(msg: T): Unit = {
    this.messages = this.messages :+ msg
  }
}