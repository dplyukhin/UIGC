package gc.executions

import scala.collection.mutable

case class DummyState(self: DummyName,
                      var activeRefs: Set[DummyRef] = Set(),
                      var createdRefs: mutable.Map[DummyRef, Set[DummyRef]] = mutable.Map(),
                      var owners: Set[DummyRef] = Set(),
                      var released: Set[DummyRef] = Set(),
                      var sent: mutable.Map[DummyToken, Int] = mutable.Map(),
                      var recv: mutable.Map[DummyToken, Int] = mutable.Map()) {
  def spawn(r: DummyRef): Unit = {
    activeRefs += r
  }

  def handleMessage(messageRefs: Iterable[DummyRef]): Unit = {
    activeRefs ++= messageRefs
  }

  def createRef(target: DummyRef, newOwner: DummyRef): DummyRef = {
    // e.g. target: A->C, newOwner: A->B
    val newRef = DummyRef(newOwner.target, target.target) // B->C
    createdRefs(target) += newRef // A->C has been used to make (B->C,)
    newRef
  }

  def release(releasing: Iterable[DummyRef]): Map[DummyName, (Seq[DummyRef], Seq[DummyRef])] = {
    // maps target actors being released -> (set of associated references being released, refs created using refs in that set)
    val targets: mutable.Map[DummyName, (Seq[DummyRef], Seq[DummyRef])] = mutable.Map()
    // process the references that are actually in the refs set
    for (ref <- releasing if activeRefs contains ref) {
      // remove each released reference's sent count
      sent -= ref.token.get
      // get the reference's target for grouping
      val key = ref.target
      // get current mapping/make new one if not found
      val (targetRefs: Seq[DummyRef], targetCreated: Seq[DummyRef]) = targets getOrElse(key, (Seq(), Seq()))
      // get the references created using this reference
      val created = createdRefs getOrElse(ref, Seq())
      // add this ref to the set of refs with this same target
      // append the group of refs created using this ref to the group of created refs to this target
      targets(key) = (targetRefs :+ ref, targetCreated :++ created)
      // remove this ref's created info and remove it from the refs set
      createdRefs -= ref
      activeRefs -= ref
    }
    targets.toMap
  }

  def handleRelease(releasing: Iterable[DummyRef], created: Iterable[DummyRef]): Unit = {
    for (ref <- releasing) {
      // delete receive count for this refob
        recv -= ref.token.get
      // if this actor already knew this refob was in its owner set then remove that info,
      // otherwise add to released_owners, we didn't know about this refob
      if (owners.contains(ref)) {
        owners -= ref
      }
      else {
        released += ref
      }
    }
    for (ref <- created) {
      // if this actor already discovered this refob from when it was released, remove that info
      // otherwise, add it to its owner set
      if (released contains ref) {
        released -= ref
      }
      else {
        owners += ref
      }
    }
  }

  def incReceivedCount(optoken: Option[DummyToken]): Unit = {
    for (token <- optoken) {
      val count = recv getOrElse (token, 0)
      recv(token) = count + 1
    }
  }

  def incSentCount(optoken: Option[DummyToken]): Unit = {
    for (token <- optoken) {
      val count = sent getOrElse (token, 0)
      sent(token) = count + 1
    }
  }

  def snapshot(): Unit = {}
}