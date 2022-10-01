package edu.illinois.osl.akka.gc.detector

import scala.collection.mutable

/**
 * This quiescence detector is not optimized for performance. However,
 * it is theoretically "complete": To detect a garbage actor,
 * it's only necessary to have the latest snapshots from that actor and every actor
 * that can "potentially reach" it.
 */
class CompleteQuiescenceDetector [
  Name,
  Token,
  Ref <: AbstractRef[Name, Token],
  Snapshot <: AbstractSnapshot[Name, Token, Ref]
] extends QuiescenceDetector[Name, Token, Ref, Snapshot] {

  def collectReachable(actor: Name,
                       irrelevant: mutable.Set[Name],
                       potentialAcquaintances: Map[Name, Set[Name]]
                      ): Unit = {

    if (!irrelevant.contains(actor)) {
      irrelevant += actor

      // mark all potential acquaintances as irrelevant as well
      if (potentialAcquaintances contains actor) {
        for (acq <- potentialAcquaintances(actor)) {
          collectReachable(acq, irrelevant, potentialAcquaintances)
        }
      }
    }
  }

  /**
   * Look at all the refs pointing to `actor` and either:
   * (a) Return None, indicating that its snapshot is irrelevant; or
   * (b) Return the set of this actor's potential inverse acquaintances, for each of whom
   *     there exists a chain of relevant refobs leading from `actor` to that
   *     potential inverse acquaintance
   */
  def possiblyRelevantPotentialInverseAcquaintances(actor: Name, snapshots: Map[Name, Snapshot]): Option[Set[Name]] = {
    var potentialInverseAcquaintances = Set[Name]()
    var workqueue = mutable.Queue[Name]()
    val released = snapshots(actor).releasedRefs.toSet

    /**
     * If the ref is relevant, mark its owner as a potential inverse acquaintance
     * and add it to the work queue if necessary; return true.
     * Otherwise, return false.
     */
    def addIfRelevant(ref: Ref): Boolean = {
      if (!isRelevant(ref, snapshots))
        return false

      val owner = ref.owner.get
      if (!potentialInverseAcquaintances.contains(owner)) {
        potentialInverseAcquaintances += owner
        workqueue += owner
      }
      true
    }

    for (incomingRef <- snapshots(actor).owners) {
      val isRelevant = addIfRelevant(incomingRef)
      if (!isRelevant)
        return None
    }

    while (workqueue.nonEmpty) {
      val piacq = workqueue.dequeue()

      // Find all unreleased refs created by `piacq` and add them to the queue;
      // if any of them are irrelevant, return early.
      for (
        createdRef <- snapshots(piacq).created
        if createdRef.target == actor && !released.contains(createdRef)
      ) {
        val isRelevant = addIfRelevant(createdRef)
        if (!isRelevant)
          return None
      }
    }

    Some(potentialInverseAcquaintances)
  }

  override def findGarbage(snapshots: Map[Name, Snapshot]): Iterable[Name] = {
    var potentialAcquaintances: Map[Name, Set[Name]] = Map()
    var irrelevantActors: mutable.Set[Name] = mutable.Set()

    for (actor <- snapshots.keys) {
      possiblyRelevantPotentialInverseAcquaintances(actor, snapshots) match {
        case None =>
          irrelevantActors += actor
        case Some(piacqs) =>
          for (piacq <- piacqs) {
            val acqs = potentialAcquaintances.getOrElse(piacq, Set())
            potentialAcquaintances += (piacq -> (acqs + actor))
          }
      }
    }
    // potentialAcquaintances(A) contains B iff
    // there is a consistent refob from A to B and B is possibly a relevant actor
    // (contingent on the relevance of its potential inverse acquaintances)

    // At this stage, irrelevantActors contains A iff
    // there is a chain from A to an irrelevant incoming refob

    val reachable = mutable.Set[Name]()
    // Starting from the irrelevant actors, mark everything they can reach in the map as irrelevant.
    for (actor <- irrelevantActors)
      collectReachable(actor, reachable, potentialAcquaintances)

    // Return all actors that are not irrelevant
    snapshots.keys.filter(!reachable.contains(_))
  }

}


