package edu.illinois.osl.akka.gc.interfaces

trait ContextLike[T] {
  def self: RefLike[T]
  def children: Iterable[RefLike[Nothing]]
  def watch[U](other: RefLike[U]): Unit
}