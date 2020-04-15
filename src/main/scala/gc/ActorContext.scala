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
  val context : AkkaActorContext[GCMessage[T]],
  val creator : AkkaActorRef[Nothing],
  val token : Token
) {

  val self = new ActorRef[T](newToken(), context.self, context.self)

  /** References this actor owns. Starts with its self reference */
  private var refs: Set[AnyActorRef] = Set(self)
  /** References this actor has created for other actors. */
  private var created: Set[AnyActorRef] = Set()
  /** References to this actor. Starts with its self reference and its creator's reference to it. */
  private var owners: Set[AnyActorRef] = Set(self, new ActorRef[T](token, creator, context.self))
  /** References to this actor discovered through [[ReleaseMsg]]. */
  private var released_owners: Set[AnyActorRef] = Set()
  /** Groups of references that have been released by this actor but are waiting for an acknowledgement  */
  private var releasing_buffer: mutable.Map[Int, Set[AnyActorRef]] = mutable.Map()

  /** Tracks how many messages are sent using each reference. */
  private var sent_per_ref: mutable.Map[Token, Int] = mutable.Map(self.token -> 0)
  /** Tracks how many messages are received using each reference. */
  private var received_per_ref: mutable.Map[Token, Int] = mutable.Map(self.token -> 0)

  private var tokenCount: Int = 0
  private var releaseCount: Int = 0
  private var epoch: Int = 0

  /**
   * Spawns a new actor into the GC system and adds it to [[refs]].
   * @param factory The behavior factory for the spawned actor.
   * @param name The name of the spawned actor.
   * @tparam S The type of application-level messages to be handled by this actor.
   * @return The [[ActorRef]] of the spawned actor.
   */
  def spawn[S <: Message](factory : ActorFactory[S], name : String) : ActorRef[S] = {
    val x = newToken()
    val self = context.self
    val child = context.spawn(factory(self, x), name)
    val ref = new ActorRef[S](x, self, child)
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
  def handleMessage(messageRefs: Iterable[AnyActorRef], token: Token): Unit = {
    handleRefs(messageRefs)
    incReceivedCount(token)
  }

  /**
   * Adds a collection of references to this actor's internal collection and marks their context fields.
   * @param payload
   */
  def handleRefs(payload : Iterable[AnyActorRef]) : Unit = {
    refs ++= payload
    refs.foreach(ref => ref.initialize(this))
  }

  /**
   * Handles the internal logistics of this actor receiving a [[ReleaseMsg]].
   * @param releasing The collection of references to be released by this actor.
   * @param created The collection of references the releaser has created.
   * @return True if this actor's behavior should stop.
   */
  def handleRelease(releasing : Iterable[AnyActorRef], created : Iterable[AnyActorRef]): Unit = {
    releasing.foreach(ref => {
      if (owners.contains(ref)) {
        owners -= ref
      }
      else {
        released_owners += ref
      }
    })
    created.foreach(ref => {
      if (released_owners.contains(ref)) {
        released_owners -= ref
      }
      else {
        owners += ref
      }
    })
  }

  /**
   * Handles an [[AckReleaseMsg]], removing the references from the knowledge set.
   * @param sequenceNum The sequence number of this release.
   */
  def finishRelease(sequenceNum: Int): Unit = {
    releasing_buffer -= sequenceNum
  }

  /**
   * Attempts to terminate this actor, sends a [[SelfCheck]] message to try again if it can't.
   * @return Either [[AkkaBehaviors.stopped]] or [[AkkaBehaviors.same]].
   */
  def tryTerminate(): Behavior[T] = {
    if (owners == Set(self) && released_owners.isEmpty && releasing_buffer.isEmpty) { // no incoming references
      if (received_per_ref(self.token) == sent_per_ref(self.token)) { // no pending self-messages
        if ((refs - self).isEmpty) {
          // if this actor owns no other references, we can release
          return AkkaBehaviors.stopped
        }
        else {
          // release any references we still have
          release(refs - self)
        }
      }
      self.target ! SelfCheck() // send a self check messages to try again
    }
    AkkaBehaviors.same
  }

  /**
   * Creates a reference to an actor to be sent to another actor
   * and adds it to the creator's [[created]] field.
   * @param target The [[ActorRef]] the created reference points to.
   * @param owner The [[ActorRef]] that will receive the created reference.
   * @tparam S The type that the actor handles.
   * @return The created reference.
   */
  def createRef[S <: Message](target: ActorRef[S], owner: AnyActorRef): ActorRef[S] = {
    val token = newToken()
    val sharedRef = new ActorRef[S](token, owner.target, target.target)
    created += sharedRef
    sharedRef
  }

  /**
   * Releases a collection of references from an actor.
   * @param releasing A collection of references.
   */
  def release(releasing: Iterable[AnyActorRef]): Unit = {
    val targets: mutable.Map[AkkaActorRef[GCMessage[Nothing]], Set[AnyActorRef]] = mutable.Map()
    // group the references in releasing that are in refs by target
    releasing.foreach(ref => {
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
   * Releases a single reference from an actor.
   * @param releasing A reference.
   */
  def release(releasing: AnyActorRef): Unit = release(Iterable(releasing))

  /**
   * Helper method for [[release()]], moves the references referring to the target to [[releasing_buffer]].
   * Sends a release message to the target.
   * @param target The actor to whom the references being released by this actor point to.
   * @param targetedRefs The associated references to target in this actor's [[refs]] set.
   */
  private def releaseTo(target: AkkaActorRef[GCMessage[Nothing]], targetedRefs: Set[AnyActorRef]): Unit = {
    val targetedCreations = created filter {
      createdRef => createdRef.target == target
    }
    // remove those references from their sets
    created --= targetedCreations
    refs --= targetedRefs
    // combine the references pointing to this target in the created set and the refs set
    // and add it to the buffer
    val refsToRelease: Set[AnyActorRef] = targetedCreations ++ targetedRefs
    releasing_buffer(releaseCount) = refsToRelease
    // send the message and increment the release count
    target ! ReleaseMsg(context.self, targetedRefs, targetedCreations, releaseCount)
    releaseCount += 1
  }

  /**
   * Gets the current [[ActorSnapshot]] and increments the epoch afterward.
   * @return The current snapshot.
   */
  def snapshot(): ActorSnapshot = {
    epoch += 1
    val buffer: Iterable[AnyActorRef] = releasing_buffer.values.flatten
    ActorSnapshot(refs ++ owners ++ created ++ buffer)
  }

  def incReceivedCount(token: Token): Unit = {
    received_per_ref.get(token) match {
      case None =>
        received_per_ref(token) = 1
      case Some(_) =>
        received_per_ref(token) += 1
    }
  }

  def incSentCount(token: Token): Unit = {
    sent_per_ref.get(token) match {
      case None =>
        sent_per_ref(token) = 1
      case Some(_) =>
        sent_per_ref(token) += 1
    }
  }

  /**
   * Creates a new [[Token]] for use in an [[ActorRef]]. Increments the internal token count of the actor.
   * @return The new token.
   */
  private def newToken() : Token = {
    val token = Token(context.self, tokenCount)
    tokenCount += 1
    token
  }
}
