package gc

import scala.collection.mutable
import akka.actor.typed.{ActorRef => AkkaActorRef}


class ActorState[T <: Message, N <: Name](val self: N, val selfToken: Token,
                                          val creator: Option[N], val creatorToken: Option[Token]) {

  val selfRef = new ActorRef[T](Some(selfToken), Some(self), self)
  val creatorRef = new ActorRef[T](creatorToken, creator, self)

  /** References this actor owns. Starts with its self reference. */
  private var activeRefs: Set[AnyActorRef] = Set(selfRef)
  /**
   * References this actor has created for other actors.
   * Maps a key reference to a value set of references that were creating using that key. */
  private val createdUsing: mutable.Map[AnyActorRef, Seq[AnyActorRef]] = mutable.Map()
  /** References to this actor. Starts with its self reference and its creator's reference to it. */
  private var owners: Set[AnyActorRef] = Set(selfRef, creatorRef)
  /** References to this actor discovered when they've been released. */
  private var releasedOwners: Set[AnyActorRef] = Set()
  /** Tracks how many messages are sent using each reference. */
  private val sentCount: mutable.Map[Token, Int] = mutable.Map()
  /** Tracks how many messages are received using each reference. */
  private val recvCount: mutable.Map[Token, Int] = mutable.Map()

  /**
   * Spawns a new actor into the GC system and adds it to [[activeRefs]].
   */
  def spawn[S <: Message](child: N, token: Token): ActorRef[S] = {
    val ref = new ActorRef[S](Some(token), Some(self), child)
    ref.initialize(this)
    activeRefs += ref
    ref
  }

  /**
   * Accepts the references from a message and increments the receive count
   * of the reference that was used to send the message.
   * @param messageRefs The refs sent with the message.
   * @param token Token of the ref this message was sent with.
   */
  def handleMessage(messageRefs: Iterable[AnyActorRef], token: Option[Token]): Unit = {
    activeRefs ++= messageRefs
    messageRefs.foreach(ref => ref.initialize(this))
    incReceivedCount(token)
  }


  /**
   * Handles the internal logistics of this actor receiving a [[ReleaseMsg]].
   * @param releasing The collection of references to be released by this actor.
   * @param created The collection of references the releaser has created.
   * @return True if this actor's behavior should stop.
   */
  def handleRelease(releasing: Iterable[AnyActorRef], created: Iterable[AnyActorRef]): Unit = {
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

  def hasUnreleasedRef: Boolean = {
    releasedOwners.nonEmpty || owners != Set(selfRef)
  }

  def hasPendingSelfMessage: Boolean = {
    recvCount(selfRef.token.get) != sentCount(selfRef.token.get)
  }

  def hasActiveRef: Boolean = {
    (activeRefs - selfRef).nonEmpty
  }

  /**
   * Creates a reference to an actor to be sent to another actor and adds it to the created collection.
   * e.g. A has x: A->B and y: A->C. A could create z: B->C using y and send it to B along x.
   *
   * @param target The [[ActorRef]] the created reference points to.
   * @param owner  The [[ActorRef]] that will receive the created reference.
   * @tparam S The type that the actor handles.
   * @return The created reference.
   */
  def createRef[S <: Message](token: Token, target: ActorRef[S], owner: AnyActorRef): ActorRef[S] = {
    // create reference and add it to the created map
    val sharedRef = new ActorRef[S](Some(token), Some(owner.target), target.target)
    val seq = createdUsing getOrElse(target, Seq())
    createdUsing(target) = seq :+ sharedRef
    sharedRef
  }

  /**
   * Releases a collection of references from an actor.
   * @param releasing A collection of references.
   * @return A map from actors to the refs to them being released and refs to them that have been created for other actors.
   */
  def release(releasing: Iterable[AnyActorRef]): Map[AkkaActorRef[GCMessage[Nothing]], (Seq[AnyActorRef], Seq[AnyActorRef])] = {
    // maps target actors being released -> (set of associated references being released, refs created using refs in that set)
    val targets: mutable.Map[AkkaActorRef[GCMessage[Nothing]], (Seq[AnyActorRef], Seq[AnyActorRef])] = mutable.Map()
    // process the references that are actually in the refs set
    for (ref <- releasing if activeRefs contains ref) {
      // remove each released reference's sent count
      sentCount remove ref.token.get
      // get the reference's target for grouping
      val key = ref.target
      // get current mapping/make new one if not found
      val (targetRefs: Seq[AnyActorRef], targetCreated: Seq[AnyActorRef]) = targets getOrElse(key, (Seq(), Seq()))
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
  def release(releasing: AnyActorRef*): Unit = release(releasing)

  /**
   * Release all references owned by this actor.
   */
  def releaseEverything(): Unit = release(activeRefs - selfRef)

  /**
   * Gets the current [[ActorSnapshot]].
   * @return The current snapshot.
   */
  def snapshot(): ActorSnapshot = {
    // get immutable copies
    val sent: Map[Token, Int] = sentCount.toMap
    val recv: Map[Token, Int] = receivedCounts.toMap
    val created: Seq[AnyActorRef] = createdUsing.values.toSeq.flatten
    ActorSnapshot(activeRefs, owners, created, releasedOwners, sent, recv)
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
