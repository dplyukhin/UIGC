package gc

import scala.collection.mutable

import akka.actor.typed

object TerminationDetector {
  /** Type alias for "any kind of akka actor ref" */
  private type Name = typed.ActorRef[Nothing]

  /** Actor names that were expected to be found in the set of snapshots  */
  private var missingOwners: Set[Name] = Set()

  def addSnapshot(): Unit = {}

  /**
   * Tests whether a ref is consistent with respect to a set of snapshots. A ref is consistent when its owner and
   * target are in the set of snapshots, the owner actively holds it, and the sent and receive counts are equal.
   * @param ref Ref to check consistency of.
   * @param snapshots Map of actor references to their snapshots.
   * @return
   */
  private def isConsistent(ref: AnyActorRef, snapshots: Map[Name, ActorSnapshot]): Boolean = {
    val A: Name = ref.owner.get
    val B: Name = ref.target
    val sent: Int = snapshots(A).sentCounts(ref.token.get)
    val recv: Int = snapshots(B).recvCounts(ref.token.get)
    (snapshots contains A) && (snapshots contains B) && (snapshots(A).refs contains ref) && (sent == recv)
  }

  /**
   * Given an actor name, it will recursively move all of the actors reachable through the given actor's unreleased
   * refobs from the unreleased map to a set.
   * @param actor An actor name.
   * @param nonterminated A set for containing all the actors reachable from A.
   * @param unreleasedRefs A map from names to their unreleased refobs.
   */
  private def collectReachable(actor: Name, nonterminated: mutable.Set[Name], unreleasedRefs: mutable.Map[Name, mutable.Set[AnyActorRef]]): Unit = {
    // TODO: use a map from names to bools representing whether something is in the non terminated set
    if (!nonterminated.contains(actor)) {
      nonterminated += actor // add this to the set
      val refs = unreleasedRefs(actor)
      for (ref <- refs) {
        // for each outgoing unreleased refob
        val B = ref.target
        // if we haven't seen it already
        if (!nonterminated.contains(B) && unreleasedRefs.contains(B)) {
          collectReachable(B, nonterminated, unreleasedRefs) // explore the refobs
        }
      }
    }
  }

  /**
   * Rearranges a map of actors and their snapshots into a map of actors and their unreleased refs.
   * @param snapshots A map of names to snapshots.
   * @return A map of names to unreleased refobs.
   */
  private def mapSnapshots(snapshots: Map[Name, ActorSnapshot]): mutable.Map[Name, mutable.Set[AnyActorRef]] = {
    val unreleased_map: mutable.Map[Name, mutable.Set[AnyActorRef]] = mutable.Map()
    val released_map: mutable.Map[Name, mutable.Set[AnyActorRef]] = mutable.Map()
    for ((name, snap) <- snapshots) {
      // gather the active refs held by this actor
      val unreleased = unreleased_map getOrElseUpdate(name, mutable.Set())
      unreleased ++= snap.refs
      // update the actors with refs that this
      for (ref <- snap.created) {
        val other_unreleased = unreleased_map getOrElseUpdate(ref.owner.get, mutable.Set())
        other_unreleased += ref
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
   * @param snapshots A map of names to snapshots.
   * @return The terminated actors.
   */
  def findTerminated(snapshots: Map[Name, ActorSnapshot]): Set[Name] = {
    // strategy: identify all the inconsistent actors and return the set without them
    val unreleased_map = mapSnapshots(snapshots)
    val reachable: mutable.Set[Name] = mutable.Set()
    // TODO: use a map from names to bools representing whether something is in the non terminated set
    // explore the unreleased graph and collect all the neighbors
    for {
      (actor, refs) <- unreleased_map
      if refs.exists(r => !isConsistent(r, snapshots))
    } collectReachable(actor, reachable, unreleased_map)
    // the actors remaining after removing every reachable inconsistent actor are terminated
    snapshots.keySet &~ reachable
  }
}