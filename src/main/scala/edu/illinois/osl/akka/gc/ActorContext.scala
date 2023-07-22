package edu.illinois.osl.akka.gc

import akka.actor.typed
import akka.actor.typed.scaladsl.AskPattern.{Askable, schedulerFromActorSystem}
import akka.actor.typed.{Props, SpawnProtocol, scaladsl}
import akka.util.Timeout
import edu.illinois.osl.akka.gc.interfaces.RefobLike
import edu.illinois.osl.akka.gc.proxies._

import scala.concurrent.{Await, Future}
import scala.concurrent.duration.{Duration, DurationInt}

/**
 * A version of [[scaladsl.ActorContext]] used by garbage-collected actors. Provides
 * methods for spawning garbage-collected actors, creating new references, and
 * releasing references. Also stores GC-related local state of the actor. By
 * keeping GC state in the [[ActorContext]], garbage-collected actors can safely
 * change their behavior by passing their [[ActorContext]] to the behavior they
 * take on.
 */
class ActorContext[T](
  val rawContext: scaladsl.ActorContext[protocol.GCMessage[T]],
  val spawnInfo: protocol.SpawnInfo,
) {

  private[gc] val proxyContext = AkkaContext(rawContext)

  private[gc] val state: protocol.State = protocol.initState(proxyContext, spawnInfo)

  val self: ActorRef[T] = protocol.getSelfRef(state, proxyContext)

  def name: ActorName = rawContext.self

  /**
   * Spawn a new named actor into the GC system.
   *
   * @param factory The behavior factory for the spawned actor.
   * @param name The name of the spawned actor.
   * @tparam S The type of application-level messages to be handled by the new actor.
   * @return An [[ActorRef]] for the spawned actor.
   */
  def spawn[S](factory: ActorFactory[S], name: String): ActorRef[S] = {
    protocol.spawnImpl(
      info => AkkaRef(rawContext.spawn(factory(info), name)), 
      state, proxyContext)
  }

  def spawnRemote[S](factory: String, location: unmanaged.ActorRef[RemoteSpawner.Command[S]]): ActorRef[S] = {
    implicit val system = rawContext.system
    implicit val timeout: Timeout = Timeout(1.minute)

    def spawnIt(info: protocol.SpawnInfo): unmanaged.ActorRef[protocol.GCMessage[S]] = {
      val f: Future[unmanaged.ActorRef[protocol.GCMessage[S]]] =
        location.ask((ref: unmanaged.ActorRef[unmanaged.ActorRef[protocol.GCMessage[S]]]) =>
          RemoteSpawner.Spawn(factory, info, ref))

      Await.result[unmanaged.ActorRef[protocol.GCMessage[S]]](f, Duration.Inf)
    }

    protocol.spawnImpl(
      info => AkkaRef(spawnIt(info)),
      state, proxyContext)
  }

  def sendMessage[S](ref: RefobLike[S], msg: S, refs: Iterable[RefobLike[Nothing]]): Unit =
    protocol.sendMessage(
      ref.asInstanceOf[protocol.Refob[S]],
      msg,
      refs.asInstanceOf[Iterable[protocol.Refob[Nothing]]],
      state, proxyContext)

  /**
   * Spawn a new anonymous actor into the GC system.
   *
   * @param factory The behavior factory for the spawned actor.
   * @tparam S The type of application-level messages to be handled by the new actor.
   * @return An [[ActorRef]] for the spawned actor.
   */
  def spawnAnonymous[S](factory: ActorFactory[S]): ActorRef[S] = {
    protocol.spawnImpl(
      info => AkkaRef(rawContext.spawnAnonymous(factory(info))), 
      state, proxyContext)
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
  def createRef[S](target: ActorRef[S], owner: ActorRef[Nothing]): ActorRef[S] = {
    protocol.createRef(target, owner, state, proxyContext)
  }

  /**
   * Releases a collection of references from an actor.
   */
  def release(releasing: Iterable[ActorRef[Nothing]]): Unit = {
    protocol.release(releasing, state, proxyContext)
  }

  /**
   * Releases all of the given references.
   * @param releasing A list of references.
   */
  def release(releasing: ActorRef[Nothing]*): Unit = release(releasing)

  /**
   * Release all references owned by this actor.
   */
  def releaseEverything(): Unit = protocol.releaseEverything(state, proxyContext)

}
