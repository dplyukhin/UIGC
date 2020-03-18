package gc

import akka.actor.typed.{ActorRef => AkkaActorRef}
import akka.actor.typed.scaladsl.{ActorContext => AkkaActorContext}

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
  private var refs: Set[ActorRef[Nothing]] = Set(self)
  /** References this actor has created for other actors. */
  private var created: Set[ActorRef[Nothing]] = Set()
  /** References to this actor. Starts with its self reference and its creator's reference to it. */
  private var owners: Set[ActorRef[Nothing]] = Set(self, new ActorRef[T](token, creator, context.self))
  /** References to this actor discovered through [[ReleaseMsg]]. */
  private var released_owners: Set[ActorRef[Nothing]] = Set()

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
  def addRefs(payload : Iterable[ActorRef[Nothing]]) : Unit = {
    refs ++= payload
  }

  /**
   * Handles the internal logistics of this actor receiving a [[ReleaseMsg]].
   * @param releasing The collection of references to be released by this actor.
   * @param created The collection of references the releaser has created.
   * @return True if this actor's behavior should stop.
   */
  def handleRelease(releasing : Iterable[ActorRef[Nothing]], created : Iterable[ActorRef[Nothing]]): Boolean = {
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
  def createRef[S <: Message](target : ActorRef[S], owner : ActorRef[Nothing]) : ActorRef[S] = {
    val token = newToken()
    val sharedRef = new ActorRef[S](token, owner.target, target.target)
    created += sharedRef
    sharedRef
  }

  /**
   * Releases a collection of references from an actor.
   * @param releasing A collection of references
   */
  def release(releasing: Iterable[ActorRef[Nothing]]): Unit = {
    var targets: mutable.Map[AkkaActorRef[GCMessage[Nothing]], Set[ActorRef[Nothing]]] = mutable.Map()
    // group the references in releasing by target
    releasing.foreach(ref => {
      val key = ref.target
      val set = targets.getOrElse(key, Set())
      targets(key) = set + ref
    })
    // filter the created and refs sets by target
    targets.keys.foreach(target => {
      val creations = created.filter {
        createdRef => createdRef.target == target
      }
      created --= creations
      val matchedRefs = refs.filter(
        ref => ref.target == target
      )
      target ! ReleaseMsg[Nothing](targets(target), creations)
    })
    refs --= releasing
  }

  /**
   * Gets the current [[ActorSnapshot]] and increments the epoch afterward.
   * @return The current snapshot.
   */
  def snapshot(): ActorSnapshot = {
    epoch += 1
    ActorSnapshot(refs ++ owners ++ created)
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
