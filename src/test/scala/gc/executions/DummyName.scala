package gc.executions

import gc.Name

/**
 * A unique address to an actor.
 * @param n Discriminator value.
 */
case class DummyName(n: Int) extends Name

object DummyName {
  // just use an internal counter to make unique addresses
  private var count: Int = 0
  def apply(): DummyName = {
    val name = new DummyName(count)
    count += 1
    name
  }
}
