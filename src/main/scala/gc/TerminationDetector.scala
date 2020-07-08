package gc

import akka.actor.typed.{ActorRef => AkkaActorRef}

import scala.collection.mutable


object TerminationDetector {
  /** Actor names that were expected to be found in the set of snapshots  */
  private var missingOwners: Set[AkkaActorRef[Nothing]] = Set()

  def addSnapshot(): Unit = {}

  /**
   * Tests whether a refob is consistent with respect to a set of snapshots. A refob is consistent when its owner and
   * target are in the set of snapshots, the owner actively holds it, and the sent and receive counts are equal.
   * @param x Refob to check consistency of.
   * @param Q Map of actor references to their snapshots.
   * @return
   */
  private def isConsistent(x: AnyRefOb, Q: Map[AnyName, ActorSnapshot]): Boolean = {
    val A: AnyName = x.owner.get
    val B: AnyName = x.target
    val sent: Int = Q(A).sentCounts(x.token.get)
    val recv: Int = Q(B).recvCounts(x.token.get)
    (Q contains A) && (Q contains B) && (Q(A).refs contains x) && (sent == recv)
  }

  /**
   * Given an actor name, it will recursively move all of the actors reachable through the given actor's unreleased
   * refobs from the unreleased map to a set.
   * @param A An actor name.
   * @param S A set for containing all the actors reachable from A.
   * @param U A map from names to their unreleased refobs.
   */
  private def collectReachable(A: AnyName, S: mutable.Set[AnyName], U: mutable.Map[AnyName, mutable.Set[AnyRefOb]]): Unit = {
    S += A // add this to the set
    val neighbors = U(A)
    U -= A // remove it from the map
    for (refob <- neighbors) {
      // for each outgoing unreleased refob
      val B = refob.target
      if (!S.contains(B)) {
        collectReachable(B, S, U) // explore the neighbors
      }
    }
  }

  /**
   * Rearranges a map of actors and their snapshots into a map of actors and their unreleased refobs.
   * @param Q A map of names to snapshots.
   * @return A map of names to unreleased refobs.
   */
  private def mapSnapshots(Q: Map[AnyName, ActorSnapshot]): mutable.Map[AnyName, mutable.Set[AnyRefOb]] = {
    val unreleased_map: mutable.Map[AnyName, mutable.Set[AnyRefOb]] = mutable.Map()
    val released_map: mutable.Map[AnyName, mutable.Set[AnyRefOb]] = mutable.Map()
    for ((name, snap) <- Q) {
      val unreleased = unreleased_map getOrElseUpdate(name, mutable.Set())
      unreleased ++= snap.owners
      for (refob <- snap.created) {
        val other_unreleased = unreleased_map getOrElseUpdate(refob.target, mutable.Set())
        other_unreleased += refob
      }
      val released = released_map getOrElseUpdate(name, mutable.Set())
      released ++= snap.releasedRefs
    }
    // remove the references we've learned are released
    for ((name, unreleased) <- unreleased_map) {
      unreleased --= released_map(name)
    }
    unreleased_map
  }

  /**
   * Identifies the actors that have terminated in a given map of names to snapshots.
   * @param Q A map of names to snapshots.
   * @return The terminated actors.
   */
  def findTerminated(Q: Map[AnyName, ActorSnapshot]): Set[AnyName] = {
    // strategy: identify all the inconsistent actors and return the set without them
    val unreleased_map = mapSnapshots(Q)
    val reachable: mutable.Set[AnyName] = mutable.Set()
    // explore the unreleased graph and collect all the neighbors
    for {
      (actor, refobs) <- unreleased_map
      if refobs.exists(r => !isConsistent(r, Q))
    } collectReachable(actor, reachable, unreleased_map)
    // the actors remaining after removing every reachable inconsistent actor are terminated
    Q.keySet &~ reachable
  }
}
