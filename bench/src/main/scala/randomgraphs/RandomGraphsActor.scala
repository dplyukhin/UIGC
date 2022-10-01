package randomgraphs

import scala.util.Random

trait RandomGraphsActor[T] {

    val statistics: Statistics
    val debug: Boolean

    /** a list of references to other actors */
    var acquaintances: Set[T] = Set()

    def spawn(): T

    final def spawnActor(): Unit = {
        val child = spawn()
        if (debug) statistics.latch.countDown()
        acquaintances += child
    }

    def forgetActor(ref: T): Unit = {
        if (debug) statistics.releaseCount.incrementAndGet()
        acquaintances -= ref
    }

    def linkActors(owner: T, target: T): Unit = {
        if (debug) statistics.linkCount.incrementAndGet()
    }

    def ping(ref: T): Unit = {
        if (debug) statistics.pingCount.incrementAndGet()
    }

    final def noop(): Unit = {
        if (debug) statistics.noopCount.incrementAndGet()
    }

    final def doSomeActions(): Unit = {
        import RandomGraphsConfig._
        if (statistics.latch.getCount() == 0) {
            return ()
        }

        /** generates a list size of M of random doubles between 0.0 to 1.0 */
        val probabilities = List.fill(NumberOfActions)(scala.util.Random.nextDouble())

        for (r <- probabilities) {
            if (r < ProbabilityToSpawn) {
                spawnActor()
            }
            else if (r < ProbabilityToSpawn + ProbabilityToSendRef && acquaintances.nonEmpty) {
                linkActors(randomItem(acquaintances), randomItem(acquaintances))
            }
            else if (r < ProbabilityToSpawn + ProbabilityToSendRef + ProbabilityToReleaseRef && acquaintances.nonEmpty) {
                forgetActor(randomItem(acquaintances))
            }
            else if (r < ProbabilityToSpawn + ProbabilityToSendRef + ProbabilityToReleaseRef + ProbabilityToPing && acquaintances.nonEmpty) {
                ping(randomItem(acquaintances))
            }
            else {
                noop()
            }
        }
    }

    /** Pick a random item from the set, assuming the set is nonempty. */
    private def randomItem(items: Set[T]): T = {
        val i = Random.nextInt(items.size)
        items.view.slice(i, i + 1).head
    }

}