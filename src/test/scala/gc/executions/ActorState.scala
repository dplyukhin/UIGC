package gc.executions

// TODO: write a better method for creating an ActorState from an existing ActorState

case class ActorState(busy: Boolean = true,
                      activeRefs: Set[Ref] = Set(),
                      createdRefs: Set[Ref] = Set(),
                      owners: Set[Ref] = Set(),
                      released: Set[Ref] = Set(),
                      sent: Map[Token, Int] = Map(),
                      recv: Map[Token, Int] = Map()) {
  /**
   * Adds the information from one ActorState to this one. Does not change [[busy]].
   * @param that The other ActorState with the information to add.
   * @return A new ActorState with the given information added.
   */
  def union(that: ActorState): ActorState = {
    ActorState(
      busy,
      activeRefs ++ that.activeRefs,
      createdRefs ++ that.createdRefs,
      owners ++ that.owners,
      released ++ that.released,
      sent ++ that.sent,
      recv ++ that.recv
    )
  }

  /** Alias for [[union]] */
  def +(that: ActorState): ActorState = {
    union(that)
  }
}