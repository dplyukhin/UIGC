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

  /** Used for token generation */

  private var tokenCount: Int = 0

  /** This actor's self reference. */
  val self = new ActorRef[T](Some(newToken()), Some(context.self), context.self)
  self.initialize(this)

  val creatorRef = new ActorRef[Nothing](token, creator, context.self)

  val state = new ActorState[AkkaActorRef[Nothing], Token, ActorRef[Nothing], ActorSnapshot](
    self,
    creatorRef,
    ActorSnapshot
  )

  private def newRef[S <: Message](owner: AnyActorRef, target: ActorRef[S]): ActorRef[S] = {
    val token = newToken()
    new ActorRef[S](Some(token), Some(owner.target), target.target)
  }

  /**
   * Spawns a new actor into the GC system.
   *
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
    state.addRef(ref)
    ref
  }

  /**
   * Accepts the references from a message and increments the receive count
   * of the reference that was used to send the message.
   * @param messageRefs The refs sent with the message.
   * @param token Token of the ref this message was sent with.
   */
  def handleMessage(messageRefs: Iterable[AnyActorRef], token: Option[Token]): Unit = {
    messageRefs.foreach(ref => ref.initialize(this))
    state.handleMessage(messageRefs, token)
  }


  /**
   * Handles the internal logistics of this actor receiving a [[ReleaseMsg]].
   * @param releasing The collection of references to be released by this actor.
   * @param created The collection of references the releaser has created.
   * @return True if this actor's behavior should stop.
   */
  def handleRelease(releasing: Iterable[AnyActorRef], created: Iterable[AnyActorRef]): Unit = {
    state.handleRelease(releasing, created)
  }

  /**
   * Attempts to terminate this actor, sends a [[SelfCheck]] message to try again if it can't.
   * @return Either [[AkkaBehaviors.stopped]] or [[AkkaBehaviors.same]].
   */
  def tryTerminate(): Behavior[T] = {
    state.tryTerminate() match {
      case ActorState.NotTerminated =>
        AkkaBehaviors.same

      case ActorState.RemindMeLater =>
        self.target ! SelfCheck() // TODO: should this change message counts?
        AkkaBehaviors.same

      case ActorState.Terminated =>
        releaseEverything()
        AkkaBehaviors.stopped
    }
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
  def createRef[S <: Message](target: ActorRef[S], owner: AnyActorRef): ActorRef[S] = {
    val ref = newRef(owner, target)
    state.handleCreatedRef(target, ref)
    ref
  }

  /**
   * Releases a collection of references from an actor, sending batches [[ReleaseMsg]] to each targeted actor.
   * @param releasing A collection of references.
   */
  def release(releasing: Iterable[AnyActorRef]): Unit = {

    val targets: Map[AkkaActorRef[Nothing], (Seq[AnyActorRef], Seq[AnyActorRef])]
      = state.release(releasing)

    // send the release message for each target actor
    for ((target, (targetedRefs, createdRefs)) <- targets) {
      // TODO Remove unsafe upcast if possible
      target.unsafeUpcast[GCMessage[Nothing]] ! ReleaseMsg(targetedRefs, createdRefs)
    }
  }

  /**
   * Releases all of the given references.
   * @param releasing A list of references.
   */
  def release(releasing: AnyActorRef*): Unit = release(releasing)

  /**
   * Release all references owned by this actor.
   */
  def releaseEverything(): Unit = release(state.nontrivialRefs)

  /**
   * Gets the current [[ActorSnapshot]].
   * @return The current snapshot.
   */
  def snapshot(): ActorSnapshot = {
    state.snapshot()
  }

  /**
   * Increments the received count of the given reference token, assuming it exists.
   * @param optoken The (optional) token of the reference to be incremented.
   */
  def incReceivedCount(optoken: Option[Token]): Unit = {
    state.incReceivedCount(optoken)
  }

  /**
   * Increments the sent count of the given reference token, assuming it exists.
   * @param optoken The (optional) token of the reference to be incremented.
   */
  def incSentCount(optoken: Option[Token]): Unit = {
    state.incSentCount(optoken)
  }

  /**
   * Creates a new [[Token]] for use in an [[ActorRef]]. Increments the internal token count of the actor.
   *
   * @return The new token.
   */
  private def newToken(): Token = {
    val token = Token(context.self, tokenCount)
    tokenCount += 1
    token
  }
}
