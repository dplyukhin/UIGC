package edu.illinois.osl.uigc

import akka.actor.typed
import akka.actor.typed.scaladsl
import akka.actor.typed.scaladsl.AskPattern.{Askable, schedulerFromActorSystem}
import akka.util.Timeout
import edu.illinois.osl.uigc.engines.Engine
import edu.illinois.osl.uigc.interfaces._

import scala.concurrent.duration.{Duration, DurationInt}
import scala.concurrent.{Await, Future}

/** A version of [[scaladsl.ActorContext]] used by garbage-collected actors. Provides methods for
  * spawning garbage-collected actors, creating new references, and releasing references. Also
  * stores GC-related local state of the actor. By keeping GC state in the [[ActorContext]],
  * garbage-collected actors can safely change their behavior by passing their [[ActorContext]] to
  * the behavior they take on.
  */
class ActorContext[T](
    val typedContext: scaladsl.ActorContext[GCMessage[T]],
    val spawnInfo: SpawnInfo
) {

  private[uigc] val engine: Engine = UIGC(typedContext.system)

  private[uigc] val state: State = engine.initState(typedContext, spawnInfo)

  val self: ActorRef[T] = engine.getSelfRef(state, typedContext)

  def name: ActorName = typedContext.self

  def system: ActorSystem[Nothing] = ActorSystem(typedContext.system)

  /** Spawn a new named actor into the GC system.
    *
    * @param factory
    *   The behavior factory for the spawned actor.
    * @param name
    *   The name of the spawned actor.
    * @tparam S
    *   The type of application-level messages to be handled by the new actor.
    * @return
    *   An [[ActorRef]] for the spawned actor.
    */
  def spawn[S](factory: ActorFactory[S], name: String): ActorRef[S] =
    engine.spawn(info => typedContext.spawn(factory(info), name), state, typedContext)

  def spawnRemote[S](
      factory: String,
      location: unmanaged.ActorRef[RemoteSpawner.Command[S]]
  ): ActorRef[S] = {
    implicit val system: typed.ActorSystem[Nothing] = typedContext.system
    implicit val timeout: Timeout = Timeout(1.minute)

    def spawnIt(info: SpawnInfo): unmanaged.ActorRef[GCMessage[S]] = {
      val f: Future[unmanaged.ActorRef[GCMessage[S]]] =
        location.ask((ref: unmanaged.ActorRef[unmanaged.ActorRef[GCMessage[S]]]) =>
          RemoteSpawner.Spawn(factory, info, ref)
        )

      Await.result[unmanaged.ActorRef[GCMessage[S]]](f, Duration.Inf)
    }

    engine.spawn(info => spawnIt(info), state, typedContext)
  }

  /** Spawn a new anonymous actor into the GC system.
    *
    * @param factory
    *   The behavior factory for the spawned actor.
    * @tparam S
    *   The type of application-level messages to be handled by the new actor.
    * @return
    *   An [[ActorRef]] for the spawned actor.
    */
  def spawnAnonymous[S](factory: ActorFactory[S]): ActorRef[S] =
    engine.spawn(info => typedContext.spawnAnonymous(factory(info)), state, typedContext)

  /** Creates a reference to an actor to be sent to another actor and adds it to the created
    * collection. e.g. A has x: A->B and y: A->C. A could create z: B->C using y and send it to B
    * along x.
    *
    * @param target
    *   The [[ActorRef]] the created reference points to.
    * @param owner
    *   The [[ActorRef]] that will receive the created reference.
    * @tparam S
    *   The type that the actor handles.
    * @return
    *   The created reference.
    */
  def createRef[S](target: ActorRef[S], owner: ActorRef[Nothing]): ActorRef[S] =
    engine.createRef(target, owner, state, typedContext)

  /** Releases a collection of references from an actor.
    */
  def release(releasing: Iterable[ActorRef[Nothing]]): Unit =
    engine.release(releasing, state, typedContext)

  /** Releases all of the given references.
    * @param releasing
    *   A list of references.
    */
  def release(releasing: ActorRef[Nothing]*): Unit = release(releasing)

}
