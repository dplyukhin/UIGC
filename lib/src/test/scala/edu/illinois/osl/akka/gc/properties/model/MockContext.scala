package edu.illinois.osl.akka.gc.properties.model

import edu.illinois.osl.akka.gc.interfaces._

case class MockContext[T](self: MockRef[T]) extends ContextLike[T] {
  override def children: Iterable[MockRef[Nothing]] = ???
  override def watch[U](other: RefLike[U]): Unit = ???
}