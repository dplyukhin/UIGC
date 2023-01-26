package edu.illinois.osl.akka.gc.interfaces

import scala.annotation.unchecked.uncheckedVariance

trait RefLike[-T] {
  def !(msg: T): Unit
  def narrow[U <: T]: RefLike[U]
  def unsafeUpcast[U >: T @uncheckedVariance]: RefLike[U]
}