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

  val self = new ActorRef[T](Some(newToken()), Some(context.self), context.self)
  self.initialize(this)

  /** References this actor owns. Starts with its self reference */
  private var refs: Set[AnyActorRef] = Set(self)
  /**
   * References this actor has created for other actors.
   * Maps a key reference to a value set of references that were creating using that key */
  private val createdUsing: mutable.Map[AnyActorRef, Set[AnyActorRef]] = mutable.Map()
  /** References to this actor. Starts with its self reference and its creator's reference to it. */
  private var owners: Set[AnyActorRef] = Set(self, new ActorRef[T](token, creator, context.self))
  /** References to this actor discovered through [[ReleaseMsg]]. */
  private var released_owners: Set[AnyActorRef] = Set()

  /** Tracks how many messages are sent using each reference. */
  private val sentCounts: mutable.Map[Token, Int] = mutable.Map()
  /** Tracks how many messages are received using each reference. */
  private val receivedCounts: mutable.Map[Token, Int] = mutable.Map()
  if (self.token.isDefined) {
    sentCounts += (self.token.get -> 0)
    receivedCounts += (self.token.get -> 0)
  }
  /** Used for token generation */
  private var tokenCount: Int = 0

  /**
   * Spawns a new actor into the GC system and adds it to [[refs]].
   * @param factory The behavior factory for the spawned actor.
   * @param name The name of the spawned actor.
   * @tparam S The type of application-level messages to be handled by this actor.
   * @return The [[ActorRef]] of the spawned actor.
   */
  def spawn[S <: Message](factory: ActorFactory[S], name: String): ActorRef[S] = {
    val x = newToken()
    val self = context.self
    val child = context.spawn(factory(self, x), name)
    val ref = new ActorRef[S](Some(x), Some(self), child)
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
  def handleMessage(messageRefs: Iterable[AnyActorRef], token: Option[Token]): Unit = {
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
  def handleRelease(releasing: Iterable[AnyActorRef], created: Iterable[AnyActorRef]): Unit = {
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
   * @param target The [[ActorRef]] the created reference points to.
   * @param owner The [[ActorRef]] that will receive the created reference.
   * @tparam S The type that the actor handles.
   * @return The created reference.
   */
  def createRef[S <: Message](target: ActorRef[S], owner: AnyActorRef): ActorRef[S] = {
    val token = newToken()
    // create reference and add it to the created map
    val sharedRef = new ActorRef[S](Some(token), Some(owner.target), target.target)
    // get or create the set
    (createdUsing get target) match {
      case Some(set) =>
        createdUsing(target) += sharedRef
      case None =>
        createdUsing(target) = Set(sharedRef)
    }
    sharedRef
  }

  /**
   * Releases a collection of references from an actor.
   * @param releasing A collection of references.
   */
  def release(releasing: Iterable[AnyActorRef]): Unit = {
    val targets: mutable.Map[AkkaActorRef[GCMessage[Nothing]], Set[AnyActorRef]] = mutable.Map()
    releasing.foreach(ref => {
      // remove each released reference's sent count
      if (ref.token.isDefined) {
        sentCounts remove ref.token.get
      }
      // group the references that are in refs by target
      if (refs contains ref) {
        val key = ref.target
        val set = targets.getOrElse(key, Set())
        targets(key) = set + ref
      }
    })
    // filter the created and refs sets by target
    targets.foreachEntry((target, targetedRefs) => releaseTo(target, targetedRefs))
  }

  /**
   * Releases all of the given references.
   * @param releasing A list of references.
   */
  def release(releasing: AnyActorRef*): Unit = release(releasing)

  /**
   * Release all references owned by this actor.
   */
  def releaseEverything(): Unit = release(refs - self)

  /**
   * Helper method for [[release()]].
   * Sends a release message to the target.
   * @param target The actor to whom the references being released by this actor point to.
   * @param targetedRefs The associated references to target in this actor's [[refs]] set.
   */
  private def releaseTo(target: AkkaActorRef[GCMessage[Nothing]], targetedRefs: Set[AnyActorRef]): Unit = {
    var targetedCreations: Set[AnyActorRef] = Set() // set of all refs created using the targeted refs
    // TODO: is it possible to do this in release beforehand so as to not have potentially O(n²) runtime?
    targetedRefs foreach({ref =>
      // get the set of refs created using this reference
      (createdUsing get ref) match {
        case Some(set) =>
          targetedCreations ++= set
          createdUsing remove ref
        case None =>

      }
    })
    // remove those references from their sets
    refs --= targetedRefs
    // send the release message
    target ! ReleaseMsg(context.self, targetedRefs, targetedCreations)
  }

  /**
   * Gets the current [[ActorSnapshot]].
   * @return The current snapshot.
   */
  def snapshot(): ActorSnapshot = {
    // get immutable copies
    val sent: Map[Token, Int] = sentCounts.toMap
    val recv: Map[Token, Int] = receivedCounts.toMap
    ActorSnapshot(refs, owners, released_owners, sent, recv)
  }

  def incReceivedCount(optoken: Option[Token]): Unit = {
    if (optoken.isDefined) {
      val token = optoken.get
      receivedCounts.get(token) match {
        case None =>
          receivedCounts(token) = 1
        case Some(_) =>
          receivedCounts(token) += 1
      }
    }
  }

  def incSentCount(optoken: Option[Token]): Unit = {
    if (optoken.isDefined) {
      val token = optoken.get
      sentCounts.get(token) match {
        case None =>
          sentCounts(token) = 1
        case Some(_) =>
          sentCounts(token) += 1
      }
    }
  }

  /**
   * Creates a new [[Token]] for use in an [[ActorRef]]. Increments the internal token count of the actor.
   * @return The new token.
   */
  private def newToken(): Token = {
    val token = Token(context.self, tokenCount)
    tokenCount += 1
    token
  }
}
