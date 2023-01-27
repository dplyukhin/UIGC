package edu.illinois.osl.akka.gc.interfaces

trait ContextLike[T] {
  def self: RefLike[T]
  def anyChildren: Boolean
  def watch[U](other: RefLike[U]): Unit
}