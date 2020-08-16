package gc

import scala.collection.mutable


class ActorState[
  Name,
  Token,
  Ref <: AbstractRef[Name, Token],
  Snapshot <: AbstractSnapshot[Name, Token, Ref]
]
(
  val selfRef: Ref,
  val creatorRef: Ref,
  val newSnapshot: (Iterable[Ref], Iterable[Ref], Iterable[Ref], Iterable[Ref], Map[Token, Int], Map[Token, Int]) => Snapshot
) {
  import ActorState._

  /** References this actor owns. Starts with its self reference. */
  var activeRefs: Set[Ref] = Set(selfRef)
  /**
   * References this actor has created for other actors.
   * Maps a key reference to a value set of references that were creating using that key. */
  val createdUsing: mutable.Map[Ref, Seq[Ref]] = mutable.Map()
  /** References to this actor. Starts with its self reference and its creator's reference to it. */
  var owners: Set[Ref] = Set(selfRef, creatorRef)
  /** References to this actor discovered when they've been released. */
  var releasedOwners: Set[Ref] = Set()
  /** Tracks how many messages are sent using each reference. */
  val sentCount: mutable.Map[Token, Int] = mutable.Map()
  /** Tracks how many messages are received using each reference. */
  val recvCount: mutable.Map[Token, Int] = mutable.Map()

  /** The set of refs that this actor owns that do not point to itself */
  def nontrivialRefs: Iterable[Ref] = activeRefs - selfRef

  /**
   * Adds the given ref to this actor's collection of active refs
   */
  def addRef(ref: Ref): Unit = {
    activeRefs += ref
  }

  /**
   * Accepts the references from a message and increments the receive count
   * of the reference that was used to send the message.
   * @param messageRefs The refs sent with the message.
   * @param token Token of the ref this message was sent with.
   */
  def handleMessage(messageRefs: Iterable[Ref], token: Option[Token]): Unit = {
    activeRefs ++= messageRefs
    incReceivedCount(token)
  }


  /**
   * Handles the internal logistics of this actor receiving a [[ReleaseMsg]].
   * @param releasing The collection of references to be released by this actor.
   * @param created The collection of references the releaser has created.
   * @return True if this actor's behavior should stop.
   */
  def handleRelease(releasing: Iterable[Ref], created: Iterable[Ref]): Unit = {
    releasing.foreach(ref => {
      // delete receive count for this refob
      recvCount remove ref.token.get
      // if this actor already knew this refob was in its owner set then remove that info,
      // otherwise add to released_owners, we didn't know about this refob
      if (owners.contains(ref)) {
        owners -= ref
      }
      else {
        releasedOwners += ref
      }
    })

    created.foreach(ref => {
      // if this actor already discovered this refob from when it was released, remove that info
      // otherwise, add it to its owner set
      if (releasedOwners.contains(ref)) {
        releasedOwners -= ref
      }
      else {
        owners += ref
      }
    })
  }

  def tryTerminate(): TerminationStatus = {
    // Check if there are any unreleased references to this actor.
    if (owners != Set(selfRef) || releasedOwners.nonEmpty) {
      NotTerminated
    }
    // There are no references to this actor remaining.
    // Check if there are any pending messages from this actor to itself.
    else if (recvCount(selfRef.token.get) != sentCount(selfRef.token.get)) {
      // Remind this actor to try and terminate after all those messages have been delivered.
      RemindMeLater
    }
    // There are no application messages to this actor remaining.
    // Therefore it should begin the termination process.
    // Check if this actor still holds any references.
    else {
      // There are no application messages to this actor remaining, and it doesn't hold any references.
      // There are no references to this actor and all of its references should be released.
      Terminated
    }
  }

  def hasUnreleasedRef: Boolean = {
    releasedOwners.nonEmpty || owners != Set(selfRef)
  }

  def hasPendingSelfMessage: Boolean = {
    recvCount(selfRef.token.get) != sentCount(selfRef.token.get)
  }

  def hasActiveRef: Boolean = {
    (activeRefs - selfRef).nonEmpty
  }

  def handleCreatedRef(target: Ref, newRef: Ref): Unit = {
    val seq = createdUsing getOrElse(target, Seq())
    createdUsing(target) = seq :+ newRef
  }

  /**
   * Releases a collection of references from an actor.
   * @param releasing A collection of references.
   * @return A map from actors to the refs to them being released and refs to them that have been created for other actors.
   */
  def release(releasing: Iterable[Ref]): Map[Name, (Seq[Ref], Seq[Ref])] = {
    // maps target actors being released -> (set of associated references being released, refs created using refs in that set)
    val targets: mutable.Map[Name, (Seq[Ref], Seq[Ref])] = mutable.Map()
    // process the references that are actually in the refs set
    for (ref <- releasing if activeRefs contains ref) {
      // remove each released reference's sent count
      sentCount remove ref.token.get
      // get the reference's target for grouping
      val key = ref.target
      // get current mapping/make new one if not found
      val (targetRefs: Seq[Ref], targetCreated: Seq[Ref]) = targets getOrElse(key, (Seq(), Seq()))
      // get the references created using this reference
      val created = createdUsing getOrElse(ref, Seq())
      // add this ref to the set of refs with this same target
      // append the group of refs created using this ref to the group of created refs to this target
      targets(key) = (targetRefs :+ ref, targetCreated :++ created)
      // remove this ref's created info and remove it from the refs set
      createdUsing remove ref
      activeRefs -= ref
    }
    // send the release message for each target actor
    // TODO: just leave this mutable?
    targets.toMap
  }

  /**
   * Releases all of the given references.
   * @param releasing A list of references.
   */
  def release(releasing: Ref*): Unit = release(releasing)

  /**
   * Gets the current [[ActorSnapshot]].
   * @return The current snapshot.
   */
  def snapshot(): Snapshot = {
    // get immutable copies
    val sent: Map[Token, Int] = sentCount.toMap
    val recv: Map[Token, Int] = recvCount.toMap
    val created: Seq[Ref] = createdUsing.values.toSeq.flatten
    newSnapshot(activeRefs, owners, created, releasedOwners, sent, recv)
  }

  /**
   * Increments the received count of the given reference token, assuming it exists.
   * @param optoken The (optional) token of the reference to be incremented.
   */
  def incReceivedCount(optoken: Option[Token]): Unit = {
    for (token <- optoken) {
      val count = recvCount getOrElse (token, 0)
      recvCount(token) = count + 1
    }
  }

  /**
   * Increments the sent count of the given reference token, assuming it exists.
   * @param optoken The (optional) token of the reference to be incremented.
   */
  def incSentCount(optoken: Option[Token]): Unit = {
    for (token <- optoken) {
      val count = sentCount getOrElse (token, 0)
      sentCount(token) = count + 1
    }
  }
}

object ActorState {

  sealed trait TerminationStatus
  final case object NotTerminated extends TerminationStatus
  final case object RemindMeLater extends TerminationStatus
  final case object Terminated extends TerminationStatus
}