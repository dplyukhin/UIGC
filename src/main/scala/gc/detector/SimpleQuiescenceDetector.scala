package gc.detector

import akka.actor.typed
import gc.{ActorRef, ActorSnapshot}

import scala.collection.mutable

/**
 * This quiescence detector is (theoretically) fast, but not "complete":
 * A stale snapshot from a non-garbage actor can prevent a garbage actor
 * from being detected.
 */
class SimpleQuiescenceDetector [
  Name,
  Token,
  Ref <: AbstractRef[Name, Token],
  Snapshot <: AbstractSnapshot[Name, Token, Ref]
] extends QuiescenceDetector[Name, Token, Ref, Snapshot] {

  /**
   * Given an actor name, it will recursively move all of the actors reachable through the given actor's unreleased
   * refobs from the unreleased map to a set.
   * @param actor An actor name.
   * @param nonterminated A set for containing all the actors reachable from A.
   * @param unreleasedRefs A map from names to their unreleased refobs.
   */
  private def collectReachable(actor: Name,
                               nonterminated: mutable.Set[Name],
                               unreleasedRefs: mutable.Map[Name, mutable.Set[Ref]]
                              ): Unit = {

    if (!nonterminated.contains(actor)) {
      nonterminated += actor

      // add all of this actor's potential acquaintances to `nonterminated` as well;
      // this set might not be initialized if the actor hasn't taken a snapshot yet
      if (unreleasedRefs contains actor) {
        for (ref <- unreleasedRefs(actor)) {
          collectReachable(ref.target, nonterminated, unreleasedRefs)
        }
      }
    }
  }

  /**
   * Returns a pair:
   * 1. A map that associates each actor to the set of unreleased refobs it owns.
   * 2. The set of actors in `snapshots` that are owned by external actors.
   */
  private def mapSnapshots(snapshots: Map[Name, Snapshot]): (mutable.Map[Name, mutable.Set[Ref]], mutable.Set[Name]) = {
    // this maps actors to the set of refobs they own that have been *created* -- but may have been released
    val createdRefs: mutable.Map[Name, mutable.Set[Ref]] = mutable.Map()
    // this maps actors to the set of refobs they own that have already been released
    val releasedRefs: mutable.Map[Name, mutable.Set[Ref]] = mutable.Map()
    // the set of actors that have external actors as potential inverse acquaintances
    val receptionists: mutable.Set[Name] = mutable.Set()

    for ((actor, snapshot) <- snapshots) {
      // each refob (x: B -o C) created by this actor must be added to createdRefs(B)
      // each unreleased refob (x: B -o A) pointing to this actor must be added to createdRefs(B)
      for (ref <- snapshot.created ++ snapshot.owners) {
        ref.owner match {
          case Some(owner) =>
            val created = createdRefs getOrElseUpdate(owner, mutable.Set())
            created += ref

          // ref.owner is None when it is owned by an external actor.
          // This means the target is a "receptionist", i.e. it can never be garbage collected.
          case None =>
            receptionists += ref.target
        }
      }

      // each released refob (x: B -o A) pointing to this actor must be added to releasedRefs(B)
      for (ref <- snapshot.releasedRefs) {
        val released = releasedRefs getOrElseUpdate(ref.owner.get, mutable.Set())
        released += ref
      }

    }

    // remove the references we've learned are released
    for ((name, created) <- createdRefs) {
      created --= releasedRefs.getOrElse(name, Set())
    }

    (createdRefs, receptionists)
  }

  override def findGarbage(snapshots: Map[Name, Snapshot]): Set[Name] = {
    // strategy: identify all the irrelevant actors and return the set without them
    val (outgoingRefs, receptionists) = mapSnapshots(snapshots)
    val irrelevantActors: mutable.Set[Name] = mutable.Set()

    // An actor's snapshot is "irrelevant" if:
    // (1) it is a "receptionist", i.e. it is owned by an external actor; or
    // (2) any of its incoming unreleased *refs* are irrelevant; or
    // (3) the actor is reachable (via a sequence of unreleased refobs) from an "irrelevant" actor

    // This loop finds all the actors satisfying property (1) and adds all reachable actors to the
    // irrelevant set.
    for (actor <- receptionists)
      collectReachable(actor, irrelevantActors, outgoingRefs)

    // This loop finds all the actors satisfying property (2) and adds all reachable actors to the
    // irrelevant set.
    for {
      refs <- outgoingRefs.values
      ref <- refs
      if !isRelevant(ref, snapshots)
    } collectReachable(ref.target, irrelevantActors, outgoingRefs)

    // An actor is terminated if its snapshot is not irrelevant
    snapshots.keySet &~ irrelevantActors
  }

}

object SimpleQuiescenceDetector {
  val AkkaQuiescenceDetector = new SimpleQuiescenceDetector[typed.ActorRef[Nothing], gc.Token, ActorRef[Nothing], ActorSnapshot]
}