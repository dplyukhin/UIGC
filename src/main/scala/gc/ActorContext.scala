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

  private var refs: Set[ActorRef[Nothing]] = Set(self)
  private var created: Set[ActorRef[Nothing]] = Set()
  private var owners: Set[ActorRef[Nothing]] = Set(self, new ActorRef[T](token, creator, context.self))
  private var released_owners: Set[ActorRef[Nothing]] = Set()

  private var tokenCount: Int = 0

  def spawn[S <: Message](factory : ActorFactory[S], name : String) : ActorRef[S] = {
    val x = newToken()
    val self = context.self
    val child = context.spawn(factory(self, x), name)
    new ActorRef[S](x, self, child)
  }

  def addRefs(payload : Seq[ActorRef[Nothing]]) : Unit = {
    refs ++= payload
  }

  def handleRelease(releasing : Seq[ActorRef[Nothing]], created : Seq[ActorRef[Nothing]]) : Unit = {
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
    if (owners.isEmpty && released_owners.isEmpty) {
      release(refs)
      context.stop(context.self)
    }
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
   * Releases a set of references from an actor.
   * @param releasing
   */
  def release(releasing: Set[ActorRef[Nothing]]): Unit = {
    var targets: mutable.Map[AkkaActorRef[GCMessage[Nothing]], Set[ActorRef[Nothing]]] = mutable.Map()
    releasing.foreach(ref => {
      val key = ref.target
      val set = targets.getOrElse(key, Set())
      targets(key) = set + ref
    })
    targets.keys.foreach(target => {
      val creations = created.filter {
        createdRef => createdRef.target == target
      }
      created --= creations
      target ! ReleaseMsg[Nothing](releasing.toSeq, creations.toSeq)
    })
  }

  /**
   * Creates a new [[Token]]. Increments the internal token count of the actor.
   * @return The new [[Token]].
   */
  private def newToken() : Token = {
    val token = Token(context.self, tokenCount)
    tokenCount += 1
    token
  }
}
