package gc

import akka.actor.typed.scaladsl.{ActorContext => AkkaActorContext, Behaviors => AkkaBehaviors}
import akka.actor.typed.{ActorRef => AkkaActorRef}
import scala.collection.mutable

/**
 * A version of [[AkkaActorContext]] used by garbage-collected actors. Provides
 * methods for spawning garbage-collected actors, creating new references, and
 * releasing references. Also stores GC-related local state of the actor. By
 * keeping GC state in the [[ActorContext]], garbage-collected actors can safely
 * change their behavior by passing their [[ActorContext]] to the behavior they
 * take on.
 *
 * @param context The context of the actor using this object.
 * @param creator The ActorRef of the actor's creator.
 * @param token A globally unique token.
 * @tparam T The type of application-level messages handled by the actor.
 */
class ActorContext[T <: Message](
  val context: AkkaActorContext[GCMessage[T]],
  val creator: Option[AkkaActorRef[Nothing]],
  val token: Option[Token]
) {

  /** This actor's self reference. */
  val self = new RefOb[T](Some(newToken()), Some(context.self), context.self)
  self.initialize(this)

  /** References this actor owns. Starts with its self reference. */
  private var refs: Set[AnyRefOb] = Set(self)
  /**
   * References this actor has created for other actors.
   * Maps a key reference to a value set of references that were creating using that key. */
  private val createdUsing: mutable.Map[AnyRefOb, Seq[AnyRefOb]] = mutable.Map()
  /** References to this actor. Starts with its self reference and its creator's reference to it. */
  private var owners: Set[AnyRefOb] = Set(self, new RefOb[T](token, creator, context.self))
  /** References to this actor discovered through [[ReleaseMsg]]. */
  private var released_owners: Set[AnyRefOb] = Set()

  /** Tracks how many messages are sent using each reference. */
  private val sentCounts: mutable.Map[Token, Int] = mutable.Map(self.token.get -> 0)
  /** Tracks how many messages are received using each reference. */
  private val receivedCounts: mutable.Map[Token, Int] = mutable.Map(self.token.get -> 0)
  /** Used for token generation */
  private var tokenCount: Int = 0

  /**
   * Spawns a new actor into the GC system and adds it to [[refs]].
   *
   * @param factory The behavior factory for the spawned actor.
   * @param name The name of the spawned actor.
   * @tparam S The type of application-level messages to be handled by this actor.
   * @return The [[RefOb]] of the spawned actor.
   */
  def spawn[S <: Message](factory: ActorFactory[S], name: String): RefOb[S] = {
    val x = newToken()
    val self = context.self
    val child = context.spawn(factory(self, x), name)
    val ref = new RefOb[S](Some(x), Some(self), child)
    ref.initialize(this)
    refs += ref
    ref
  }

  /**
   * Accepts the references from a message and increments the receive count
   * of the reference that was used to send the message.
   * @param messageRefs The refs sent with the message.
   * @param token Token of the ref this message was sent with.
   */
  def handleMessage(messageRefs: Iterable[AnyRefOb], token: Option[Token]): Unit = {
    refs ++= messageRefs
    messageRefs.foreach(ref => ref.initialize(this))
    incReceivedCount(token)
  }


  /**
   * Handles the internal logistics of this actor receiving a [[ReleaseMsg]].
   * @param releasing The collection of references to be released by this actor.
   * @param created The collection of references the releaser has created.
   * @return True if this actor's behavior should stop.
   */
  def handleRelease(releasing: Iterable[AnyRefOb], created: Iterable[AnyRefOb]): Unit = {
    releasing.foreach(ref => {
      // delete receive count for this refob
      receivedCounts remove ref.token.get
      // if this actor already knew this refob was in its owner set then remove that info,
      // otherwise add to released_owners, we didn't know about this refob
      if (owners.contains(ref)) {
        owners -= ref
      }
      else {
        released_owners += ref
      }
    })

    created.foreach(ref => {
      // if this actor already discovered this refob from when it was released, remove that info
      // otherwise, add it to its owner set
      if (released_owners.contains(ref)) {
        released_owners -= ref
      }
      else {
        owners += ref
      }
    })
  }

  /**
   * Attempts to terminate this actor, sends a [[SelfCheck]] message to try again if it can't.
   * @return Either [[AkkaBehaviors.stopped]] or [[AkkaBehaviors.same]].
   */
  def tryTerminate(): Behavior[T] = {
    // Check if there are any unreleased references to this actor.
    if (owners != Set(self) || released_owners.nonEmpty) {
      AkkaBehaviors.same
    }
    // There are no references to this actor remaining.
    // Check if there are any pending messages from this actor to itself.
    else if (receivedCounts(self.token.get) != sentCounts(self.token.get)) {
      // Remind this actor to try and terminate after all those messages have been delivered.
      self.target ! SelfCheck() // TODO: should this change message counts?
      AkkaBehaviors.same
    }
    // There are no application messages to this actor remaining.
    // Therefore it should begin the termination process.
    // Check if this actor still holds any references.
    else {
      if ((refs - self).nonEmpty) {
        // Release all the references held by this actor.
        releaseEverything()
      }
    // There are no application messages to this actor remaining, and it doesn't hold any references.
    // There are no references to this actor and all of its references have been released.
      AkkaBehaviors.stopped
    }
  }

  /**
   * Creates a reference to an actor to be sent to another actor and adds it to the created collection.
   * e.g. A has x: A->B and y: A->C. A could create z: B->C using y and send it to B along x.
 *
   * @param target The [[RefOb]] the created reference points to.
   * @param owner  The [[RefOb]] that will receive the created reference.
   * @tparam S The type that the actor handles.
   * @return The created reference.
   */
  def createRef[S <: Message](target: RefOb[S], owner: AnyRefOb): RefOb[S] = {
    val token = newToken()
    // create reference and add it to the created map
    val sharedRef = new RefOb[S](Some(token), Some(owner.target), target.target)
    val seq = createdUsing getOrElse(target, Seq())
    createdUsing(target) = seq :+ sharedRef
    sharedRef
  }

  /**
   * Releases a collection of references from an actor, sending batches [[ReleaseMsg]] to each targeted actor.
   * @param releasing A collection of references.
   */
  def release(releasing: Iterable[AnyRefOb]): Unit = {
    // maps target actors being released -> (set of associated references being released, refs created using refs in that set)
    val targets: mutable.Map[AkkaActorRef[GCMessage[Nothing]], (Seq[AnyRefOb], Seq[AnyRefOb])] = mutable.Map()
    // process the references that are actually in the refs set
    for (ref <- releasing if refs contains ref) {
      // remove each released reference's sent count
      sentCounts remove ref.token.get
      // get the reference's target for grouping
      val key = ref.target
      // get current mapping/make new one if not found
      val (targetRefs: Seq[AnyRefOb], targetCreated: Seq[AnyRefOb]) = targets getOrElse(key, (Seq(), Seq()))
      // get the references created using this reference
      val created = createdUsing getOrElse(ref, Seq())
      // add this ref to the set of refs with this same target
      // append the group of refs created using this ref to the group of created refs to this target
      targets(key) = (targetRefs :+ ref, targetCreated :++ created)
      // remove this ref's created info and remove it from the refs set
      createdUsing remove ref
      refs -= ref
    }
    // send the release message for each target actor
    for ((target, (targetedRefs, createdRefs)) <- targets) {
      target ! ReleaseMsg(context.self, targetedRefs, createdRefs)
    }
  }

  /**
   * Releases all of the given references.
   * @param releasing A list of references.
   */
  def release(releasing: AnyRefOb*): Unit = release(releasing)

  /**
   * Release all references owned by this actor.
   */
  def releaseEverything(): Unit = release(refs - self)

  /**
   * Gets the current [[ActorSnapshot]].
   * @return The current snapshot.
   */
  def snapshot(): ActorSnapshot = {
    // get immutable copies
    val sent: Map[Token, Int] = sentCounts.toMap
    val recv: Map[Token, Int] = receivedCounts.toMap
    val created: Seq[AnyRefOb] = createdUsing.values.toSeq.flatten
    ActorSnapshot(refs, owners, created, released_owners, sent, recv)
  }

  /**
   * Increments the received count of the given reference token, assuming it exists.
   * @param optoken The (optional) token of the reference to be incremented.
   */
  def incReceivedCount(optoken: Option[Token]): Unit = {
    for (token <- optoken) {
      val count = receivedCounts getOrElse (token, 0)
      receivedCounts(token) = count + 1
    }
  }

  /**
   * Increments the sent count of the given reference token, assuming it exists.
   * @param optoken The (optional) token of the reference to be incremented.
   */
  def incSentCount(optoken: Option[Token]): Unit = {
    for (token <- optoken) {
      val count = sentCounts getOrElse (token, 0)
      sentCounts(token) = count + 1
    }
  }

  /**
   * Creates a new [[Token]] for use in an [[RefOb]]. Increments the internal token count of the actor.
   *
   * @return The new token.
   */
  private def newToken(): Token = {
    val token = Token(context.self, tokenCount)
    tokenCount += 1
    token
  }
}
