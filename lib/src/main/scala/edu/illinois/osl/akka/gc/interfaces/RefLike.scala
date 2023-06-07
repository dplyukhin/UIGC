package edu.illinois.osl.akka.gc.interfaces

import scala.annotation.unchecked.uncheckedVariance

trait RefLike[-T] extends Pretty {
  def !(msg: T): Unit
  def narrow[U <: T]: RefLike[U] =
    this
  def unsafeUpcast[U >: T @uncheckedVariance]: RefLike[U] =
    this.asInstanceOf[RefLike[U]]
}