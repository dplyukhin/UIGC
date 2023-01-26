package edu.illinois.osl.akka.gc.protocols.drl

import akka.actor.typed.{PostStop, Terminated, Signal}
import scala.collection.mutable

object State {
  sealed trait TerminationStatus
  final case object NotTerminated extends TerminationStatus
  final case object RemindMeLater extends TerminationStatus
  final case object AmTerminated extends TerminationStatus
}

class State
(
  val self: Name,
  val spawnInfo: DRL.SpawnInfo,
) {
  import State._

  var count: Int = 1
  
  /** This actor's self reference. */
  val selfRef: Ref = Refob[Nothing](Some(Token(self, 0)), Some(self), self)
  selfRef.initialize(this)

  val creatorRef = Refob[Nothing](spawnInfo.token, spawnInfo.creator, self)

  /** References this actor owns. Starts with its self reference. */
  val activeRefs: mutable.HashSet[Ref] = mutable.HashSet(selfRef)
  /**
   * References this actor has created for other actors.
   * Maps a key reference to a value set of references that were creating using that key. */
  val createdUsing: mutable.HashMap[Ref, Seq[Ref]] = mutable.HashMap()
  /** References to this actor. Starts with its self reference and its creator's reference to it. */
  val owners: mutable.HashSet[Ref] = mutable.HashSet(selfRef, creatorRef)
  /** References to this actor discovered when they've been released. */
  val releasedOwners: mutable.HashSet[Ref] = mutable.HashSet()
  /** Tracks how many messages are sent using each reference. */
  val sentCount: mutable.HashMap[Token, Int] = mutable.HashMap(selfRef.token.get -> 0)
  /** Tracks how many messages are received using each reference. */
  val recvCount: mutable.HashMap[Token, Int] = mutable.HashMap(selfRef.token.get -> 0)

  /** The set of refs that this actor owns that point to this actor itself */
  def trivialActiveRefs: Iterable[Ref] = activeRefs filter { _.target == self }
  /** The set of refs that this actor owns that do not point to itself */
  def nontrivialActiveRefs: Iterable[Ref] = activeRefs filter { _.target != self }

  /**
   * Adds the given ref to this actor's collection of active refs
   */
  def addRef(ref: Ref): Unit = {
    activeRefs += ref
  }

  def newRef[S](owner: Refob[Nothing], target: Refob[S]): Refob[S] = {
    val token = newToken()
    Refob[S](Some(token), Some(owner.target), target.target)
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
    assert(releasing.nonEmpty)
    val sender = releasing.head.owner

    if (sender.get == self) {
      numPendingReleaseMessagesToSelf -= 1
    }

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

  def handleSelfCheck(): Unit = {
    incReceivedCount(selfRef.token)
  }

  /** An actor can receive a reference to itself and then deactivate it, placing a Release
   * message in its queue. We don't want to terminate actors if their message queue is nonempty.
   * This variable indicates whether any such messages exist. */
  private var numPendingReleaseMessagesToSelf = 0

  /** Assuming this actor has no inverse acquaintances besides itself, this function
   * determines whether the actor has any undelivered messages to itself.
   */
  // TODO Can we test that this is accurate with Scalacheck?
  private def anyPendingSelfMessages: Boolean = {
    // Actors can send themselves three kinds of messages:
    // - App messages
    // - Release messages
    // - SelfCheck messages

    // If an actor has deactivated a reference to itself, the send count is deleted and its receive
    // count may or may not have been initialized. First we check if any such messages exist.
    if (numPendingReleaseMessagesToSelf > 0) return true

    // At this point we know that the actor has no pending release messages to itself.
    // It is possible that the actor sent itself messages that have not
    // yet been delivered, in which case the received count has not yet
    // been initialized.
    for (ref <- trivialActiveRefs) {
      // The token is guaranteed to be defined because a token is only null when the reference
      // is from an *external actor* to an internal actor. Clearly, this actor is an internal actor.
      val token = ref.token.get

      // If the sent count is uninitialized, then no messages were sent using this ref.
      if (sentCount.contains(token)) {
        // If the receive count is uninitialized, then at least one message hasn't been received.
        if (!recvCount.contains(token))
          return true

        // Similarly if the send count is greater than the receive count.
        assert(sentCount(token) >= recvCount(token))
        if (sentCount(token) > recvCount(token))
          return true
      }
    }
    false
  }

  /** Whether this actor has any nontrivial inverse acquaintances, i.e. any other actors with unreleased
   * references to this one. */
  def anyInverseAcquaintances: Boolean = {
    // By the Chain Lemma, this check is sufficient: an actor has nontrivial inverse acquaintances iff
    // the `owners` set contains a refob owned by an actor other than itself.
    // TODO Can we test this invariant with Scalacheck?
    owners exists { ref =>
      ref.owner match {
        case Some(owner) => owner != self
        case None => true // In this case, an external actor has a reference to it
      }
    }
  }

  /** Check whether this actor is ready to self-terminate due to having no inverse acquaintances and no
   * pending messages; such an actor will never again receive a message. */
  def tryTerminate(): TerminationStatus = {
    if (anyInverseAcquaintances) {
      NotTerminated
    }
    // No other actor has a reference to this one.
    // Check if there are any pending messages from this actor to itself.
    else if (anyPendingSelfMessages) {
      // Remind this actor to try and terminate after all those messages have been delivered.
      // This is done by sending self a SelfCheck message, so we increment the message send count.
      incSentCount(selfRef.token)
      RemindMeLater
    }
    // There are no messages to this actor remaining.
    // Therefore it should begin the termination process; if it has any references, they should be deactivated.
    else {
      AmTerminated
    }
  }

  /**
   * Updates this actor's state to indicate that the new ref `newRef` was created using `target`.
   * The target of `target` should be the same as the target of `newRef`.
   * @param target An existing ref to the target
   * @param newRef The new ref that has been created using `target`
   */
  def handleCreatedRef(target: Ref, newRef: Ref): Unit = {
    assert(target.target == newRef.target)
    assert(activeRefs contains target)

    // If I am creating a reference to myself...
    if (target.target == self) {
      owners += newRef
    }
    // If I am creating a reference pointing to another actor...
    else {
      // Note that this code doesn't work when `target.target == self` because the `self`
      // ref never gets released; the `createdUsing` field would increase in size without bound.
      val seq = createdUsing getOrElse(target, Seq())
      createdUsing(target) = seq :+ newRef
    }
  }

  /**
   * Releases a collection of references from an actor.
   * @param releasing A collection of references.
   * @return A map from actors to the refs to them being released and refs to them that have been created for other actors.
   */
  def release(releasing: Iterable[Ref]): mutable.HashMap[Name, (Seq[Ref], Seq[Ref])] = {
    // maps target actors being released -> (set of associated references being released, refs created using refs in that set)
    val targets: mutable.HashMap[Name, (Seq[Ref], Seq[Ref])] = mutable.HashMap()
    // process the references that are actually in the refs set
    for (ref <- releasing if nontrivialActiveRefs exists (_ == ref)) {
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


    // Similarly, process the "trivial" references from this actor to itself.
    // But do not deactivate the `self` ref, because it is always accessible from
    // the ActorContext.
    var refsToSelf: Seq[Ref] = Seq()
    for (ref <- releasing if (trivialActiveRefs exists (_ == ref)) && (ref != selfRef)) {
      sentCount remove ref.token.get
      activeRefs -= ref
      refsToSelf :+= ref
    }
    if (refsToSelf.nonEmpty) {
      // Note that the `createdUsing` entry for self refs is always empty:
      // If an actor creates a reference to itself, it immediately adds it
      // to the `owners` set instead of placing it in the `createdUsing` set.
      // TODO Test that this is the case!
      targets(self) = (refsToSelf, Seq())
      numPendingReleaseMessagesToSelf += 1
    }


    // send the release message for each target actor
    // TODO: just leave this mutable?
    targets
  }

  /**
   * Releases all of the given references.
   * @param releasing A list of references.
   */
  def release(releasing: Ref*): Unit = release(releasing)

  // /**
  //  * Gets the current [[ActorSnapshot]].
  //  * @return The current snapshot.
  //  */
  // def snapshot(): ActorSnapshot = {
  //   // get immutable copies
  //   val sent: Map[Token, Int] = sentCount.toMap
  //   val recv: Map[Token, Int] = recvCount.toMap
  //   val created: Seq[Ref] = createdUsing.values.toSeq.flatten
  //   newSnapshot(activeRefs, owners, created, releasedOwners, sent, recv)
  // }

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

  def newToken() = { 
    val token = Token(self, count)
    count += 1
    token
  }
}