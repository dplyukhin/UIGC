package gc

import scala.collection.mutable
import akka.actor.typed

trait AbstractRef[Name, Token] {
  val token: Option[Token]
  val owner: Option[Name]
  val target: Name
}

trait AbstractSnapshot[Name, Token, Ref <: AbstractRef[Name, Token]] {
  val refs: Iterable[Ref]
  val owners: Iterable[Ref]
  val created: Iterable[Ref]
  val releasedRefs: Iterable[Ref]
  val sentCounts: Map[Token, Int]
  val recvCounts: Map[Token, Int]
}

class QuiescenceDetector [
  Name,
  Token,
  Ref <: AbstractRef[Name, Token],
  Snapshot <: AbstractSnapshot[Name, Token, Ref]
] {


  /**
   * Tests whether a ref is consistent with respect to a set of snapshots. A ref is consistent when its owner and
   * target are in the set of snapshots, the owner actively holds it, and the sent and receive counts are equal.
   * @param ref Ref to check consistency of.
   * @param snapshots Map of actor references to their snapshots.
   * @return
   */
  def isConsistent(ref: Ref, snapshots: Map[Name, Snapshot]): Boolean = {
    // if the refob is owned by an external actor, it is never consistent
    if (ref.owner.isEmpty) return false

    val x: Token = ref.token.get
    val A: Name = ref.owner.get
    val B: Name = ref.target

    (snapshots contains A) && (snapshots contains B) &&
      (snapshots(A).refs exists { _ == ref }) &&
      (snapshots(A).sentCounts.getOrElse(x, 0) == snapshots(B).recvCounts.getOrElse(x, 0))
  }

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

      // each active refob (x: A -o B) in the snapshot must be added to createdRefs(A);
      val created = createdRefs getOrElseUpdate(actor, mutable.Set())
      created ++= snapshot.refs
    }

    // remove the references we've learned are released
    for ((name, unreleased) <- createdRefs) {
      unreleased --= releasedRefs.getOrElse(name, Set())
    }

    (createdRefs, receptionists)
  }

  /**
   * Identifies the actors that have terminated in a given map of names to snapshots.
   * @param snapshots A map of names to snapshots.
   * @return The terminated actors.
   */
  def findTerminated(snapshots: Map[Name, Snapshot]): Set[Name] = {
    // strategy: identify all the inconsistent actors and return the set without them
    val (outgoingRefs, receptionists) = mapSnapshots(snapshots)
    val inconsistentActors: mutable.Set[Name] = mutable.Set()

    // An actor's snapshot is "inconsistent" if:
    // (1) it is a "receptionist", i.e. it is owned by an external actor; or
    // (2) any of its incoming unreleased *refs* are inconsistent; or
    // (3) the actor is reachable (via a sequence of unreleased refobs) from an "inconsistent" actor

    // This loop finds all the actors satisfying property (1) and adds all reachable actors to the
    // inconsistent set.
    for (actor <- receptionists)
      collectReachable(actor, inconsistentActors, outgoingRefs)

    // This loop finds all the actors satisfying property (2) and adds all reachable actors to the
    // inconsistent set.
    for {
      refs <- outgoingRefs.values
      ref <- refs
      if !isConsistent(ref, snapshots)
    } collectReachable(ref.target, inconsistentActors, outgoingRefs)

    // An actor is terminated if its snapshot is not inconsistent
    snapshots.keySet &~ inconsistentActors
  }

}

object QuiescenceDetector {
  val AkkaQuiescenceDetector = new QuiescenceDetector[typed.ActorRef[Nothing], gc.Token, ActorRef[Nothing], ActorSnapshot]
}