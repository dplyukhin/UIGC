package randomgraphs

import scala.util.Random
import RandomGraphsConfig._

trait RandomGraphsActor[T] {

    val statistics: Statistics

    val someArray: Array[Byte] = Array.tabulate(SizeOfActor)(n => 0)

    /** a list of references to other actors */
    var acquaintances: Set[T] = Set()

    def spawn(): T

    final def spawnActor(): Unit = {
        val child = spawn()
        statistics.latch.countDown()
        acquaintances += child
    }

    def forgetActor(ref: T): Unit = {
        if (LogStats) statistics.releaseCount.incrementAndGet()
        acquaintances -= ref
    }

    def linkActors(owner: T, target: T): Unit = {
        if (LogStats) statistics.linkCount.incrementAndGet()
    }

    def ping(ref: T): Unit = {
        if (LogStats) statistics.pingCount.incrementAndGet()
    }

    final def noop(): Unit = {
        if (LogStats) statistics.noopCount.incrementAndGet()
    }

    final def doSomeActions(): Unit = {
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