package gc.executions

/**
 * A unique address to an actor.
 *
 * @param n Discriminator value.
 */
case class DummyName(n: Int)

object DummyName {
  // just use an internal counter to make unique addresses
  var count: Int = 0
  def apply(): DummyName = {
    val name = new DummyName(count)
    count += 1
    name
  }
}
