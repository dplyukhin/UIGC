package gc.detector

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

trait QuiescenceDetector [
  Name,
  Token,
  Ref <: AbstractRef[Name, Token],
  Snapshot <: AbstractSnapshot[Name, Token, Ref]
] {

  /**
   * Tests whether a ref is relevant with respect to a set of snapshots. A ref is relevant when its owner and
   * target are in the set of snapshots, the owner actively holds it, and the sent and receive counts are equal.
   * @param ref Ref to check consistency of.
   * @param snapshots Map of actor references to their snapshots.
   * @return
   */
  def isRelevant(ref: Ref, snapshots: Map[Name, Snapshot]): Boolean = {
    // if the refob is owned by an external actor, it is never relevant
    if (ref.owner.isEmpty) return false

    val x: Token = ref.token.get
    val A: Name = ref.owner.get
    val B: Name = ref.target

    (snapshots contains A) && (snapshots contains B) &&
      (snapshots(A).refs exists { _ == ref }) &&
      (snapshots(A).sentCounts.getOrElse(x, 0) == snapshots(B).recvCounts.getOrElse(x, 0))
  }

  /**
   * Given a collection of snapshots, this function finds a subset of
   * those actors that have become quiescent garbage.
   * @param snapshots A map associating an actor's name to its snapshot
   * @return The quiescent actors.
   */
  def findGarbage(snapshots: Map[Name, Snapshot]): Iterable[Name]
}
