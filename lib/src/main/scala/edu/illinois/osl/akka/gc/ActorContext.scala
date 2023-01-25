package edu.illinois.osl.akka.gc

/**
 * A version of [[AkkaActorContext]] used by garbage-collected actors. Provides
 * methods for spawning garbage-collected actors, creating new references, and
 * releasing references. Also stores GC-related local state of the actor. By
 * keeping GC state in the [[ActorContext]], garbage-collected actors can safely
 * change their behavior by passing their [[ActorContext]] to the behavior they
 * take on.
 */
class ActorContext[T <: Message](
  val rawContext: raw.ActorContext[protocol.GCMessage[T]],
  val spawnInfo: protocol.SpawnInfo,
) {

  val state = protocol.initState(rawContext, spawnInfo)

  def self: ActorRef[T] = state.selfRef.asInstanceOf[ActorRef[T]]

  def name: ActorName = state.selfRef.rawActorRef

  /**
   * Spawn a new named actor into the GC system.
   *
   * @param factory The behavior factory for the spawned actor.
   * @param name The name of the spawned actor.
   * @tparam S The type of application-level messages to be handled by the new actor.
   * @return An [[ActorRef]] for the spawned actor.
   */
  def spawn[S <: Message](factory: ActorFactory[S], name: String): ActorRef[S] = {
    protocol.spawnImpl(
      info => rawContext.spawn(factory(info), name), 
      state, rawContext)
  }

  /**
   * Spawn a new anonymous actor into the GC system.
   *
   * @param factory The behavior factory for the spawned actor.
   * @tparam S The type of application-level messages to be handled by the new actor.
   * @return An [[ActorRef]] for the spawned actor.
   */
  def spawnAnonymous[S <: Message](factory: ActorFactory[S]): ActorRef[S] = {
    protocol.spawnImpl(
      info => rawContext.spawnAnonymous(factory(info)), 
      state, rawContext)
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
  def createRef[S <: Message](target: ActorRef[S], owner: ActorRef[Nothing]): ActorRef[S] = {
    protocol.createRef(target, owner, state)
  }

  /**
   * Releases a collection of references from an actor, sending batches [[ReleaseMsg]] to each targeted actor.
   * @param releasing A collection of references.
   */
  def release(releasing: Iterable[ActorRef[Nothing]]): Unit = {
    protocol.release(releasing, state)
  }

  /**
   * Releases all of the given references.
   * @param releasing A list of references.
   */
  def release(releasing: ActorRef[Nothing]*): Unit = release(releasing)

  /**
   * Release all references owned by this actor.
   */
  def releaseEverything(): Unit = protocol.releaseEverything(state)

}
