package gc

import akka.actor.typed.{ActorRef => AkkaActorRef}

object TerminationDetector {
  /** Actor names that were expected to be found in the set of snapshots  */
  private var missingOwners: Set[AkkaActorRef[Nothing]] = Set()

  def addSnapshot(): Unit = {}

  def findTerminated(snapshots: Map[AkkaActorRef[Nothing], ActorSnapshot]): List[AkkaActorRef[Nothing]] = {
    var terminated: List[AkkaActorRef[Nothing]] = List()
    // if every actor's known owners are part of the snapshot group then the set is closed
    // we need to find any potential closed subsets
    // the owners of these refobs are in the map key set
//    val ownerInSnapshots = snapshots
//      .values
//      .flatMap {snap => snap.owners}
//      .map {owner => owner.owner.get}
//      .filter {ref => snapshots contains ref}
//      .toSet
    val test = snapshots withFilter {case (_, snap) => (snap.owners map {owner => owner.owner.get}) subsetOf snapshots.keySet}
    println(s"These actor names' owners are in the set of snaphots: ${test}")
    // identify the blocked actors
    for ((name, snap) <- test) {
      println(s"Checking msg counts for ${name.path.name}")
      // for every owner of this actor, check that the owner's sent count equals the target's receive count
      if (snap.owners forall { refob =>
        println(s"Checking owner refob ${refob}")
        val token = refob.token.get
        val ownerSent = snapshots(refob.owner.get).sentCounts(token)
        println(s"Owner ${refob.owner.get.path.name}'s sent count: ${ownerSent}")
        val targetRecv = snap.recvCounts(token)
        println(s"Target ${name.path.name}'s receive count: ${targetRecv}")
        // if they are equal, that target actor appears blocked
        ownerSent == targetRecv
      }) {
        println(s"Message counts agree for ${name.path.name}")
        terminated = terminated :+ name
      }
    }
    terminated
  }
}
