package gc

import akka.actor.typed.{ActorRef => AkkaActorRef}
import akka.actor.typed.scaladsl.{ActorContext => AkkaActorContext}

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
class ActorContext[T](
  val context : AkkaActorContext[GCMessage[T]],
  val creator : AkkaActorRef[Nothing],
  val token : Token
) {

  val self = new ActorRef[T](newToken(), context.self, context.self)

  private var refs: Set[ActorRef[Nothing]] = Set(self)
  private var created: Set[ActorRef[Nothing]] = Set()
  private var owners: Set[ActorRef[Nothing]] = Set(self, new ActorRef(token, creator, context.self))
  private var released_owners: Set[ActorRef[Nothing]] = Set()
  private var tokenCount: Int = 0

  def spawn[S](factory : ActorFactory[S], name : String) : ActorRef[S] = {
    val x = newToken()
    val self = context.self
    val child = context.spawn(factory(self, x), name)
    new ActorRef[S](x, self, child)
  }

  def createRef[S](target : ActorRef[S], owner : ActorRef[Nothing]) : ActorRef[S]

  def release[S](releasing : Seq[ActorRef[Nothing]])

  private def newToken() : Token

}
