package gc.executions

/**
 * A unique address to an actor.
 * @param n Discriminator value.
 */
case class Address(n: Int)

object Address {
  // just use an internal counter to make unique addresses
  private var count: Int = 0
  def apply(): Address = {
    val name = new Address(count)
    count += 1
    name
  }
}
