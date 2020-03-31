package gc

import akka.actor.typed.scaladsl.{ActorContext => AkkaActorContext}
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
    refs += ref
    ref
  }

  /**
   * Adds a collection of references to this actor's internal collection.
   * @param payload
   */
  def addRefs(payload : Iterable[AnyActorRef]) : Unit = {
    refs ++= payload
  }

  /**
   * Handles the internal logistics of this actor receiving a [[ReleaseMsg]].
   * @param releasing The collection of references to be released by this actor.
   * @param created The collection of references the releaser has created.
   * @return True if this actor's behavior should stop.
   */
  def handleRelease(releasing : Iterable[AnyActorRef], created : Iterable[AnyActorRef]): Boolean = {
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
    if (owners == Set(self) && released_owners.isEmpty) {
      release(refs)
      return true
    }
    false
  }

  /**
   * Creates a reference to an actor to be sent to another actor
   * and adds it to the creator's [[created]] field.
   * @param target The [[ActorRef]] the created reference points to.
   * @param owner The [[ActorRef]] that will receive the created reference.
   * @tparam S The type of [[Message]](?) that the actor handles.
   * @return The created reference.
   */
  def createRef[S <: Message](target : ActorRef[S], owner : AnyActorRef) : ActorRef[S] = {
    val token = newToken()
    val sharedRef = new ActorRef[S](token, owner.target, target.target)
    created += sharedRef
    sharedRef
  }

  /**
   * Releases a collection of references from an actor.
   * @param releasing A collection of references
   */
  def release(releasing: Iterable[AnyActorRef]): Unit = {
    var targets: mutable.Map[AkkaActorRef[GCMessage[Nothing]], Set[AnyActorRef]] = mutable.Map()
    // group the references in releasing by target
    releasing.foreach(ref => {
      val key = ref.target
      val set = targets.getOrElse(key, Set())
      targets(key) = set + ref
    })
    // filter the created and refs sets by target
    targets.keys.foreach(target => {
      val targetedCreations = created filter {
        createdRef => createdRef.target == target
      }

      // remove those references from their sets
      created --= targetedCreations
      refs --= targets(target)
      // combine the references pointing to this target in the created set and the refs set
      // and add it to the buffer
      val refsToRelease: Set[AnyActorRef] = targetedCreations ++ targets(target)
      releasing_buffer(releaseCount) = refsToRelease

      target ! ReleaseMsg[Nothing](context.self, targets(target), targetedCreations, releaseCount)
      releaseCount += 1
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
   * Gets the current [[ActorSnapshot]] and increments the epoch afterward.
   * @return The current snapshot.
   */
  def snapshot(): ActorSnapshot = {
    epoch += 1
    val buffer: Iterable[AnyActorRef] = releasing_buffer.values.flatten
    ActorSnapshot(refs ++ owners ++ created ++ buffer)
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
