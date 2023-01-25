package edu.illinois.osl.akka.gc.properties.model

/**
 * This class represents a simulated Actor System for the purpose of property-based testing.
 * An [[Event]] can be used to transition the configuration from one global state to another
 * if that event is legal in the current global state. For example, an actor cannot send a
 * message to another actor that it does not have a reference to.
 *
 * In the initial configuration, there is just one actor that acts as a receptionist, i.e.
 * it never terminates.
 */
class Configuration() {
  import Configuration._

  val initialActor: Name = Name()

  /** A mapping from all actors in the configuration to their states */
  var state: Map[Name, Behavior] = Map(
    initialActor -> ???
  )

  /** An indicator of whether an actor is idle, blocked, or stopped */
  var status: Map[Name, Configuration.ActorStatus] = Map(
    initialActor -> Busy
  )

  /** Associates each actor to the collection of undelivered messages that have been sent to them */
  var pendingMessages: Map[Name, FIFOMailbox] = Map(
    initialActor -> new FIFOMailbox()
  )

  /** This sequence is the list of snapshots taken by actors throughout this configuration. */
  var snapshots: Seq[(Name, Snapshot)] = Seq()

  /** The execution that produced this configuration. */
  var execution: Execution = Seq()

  /** The set of actors that have self-terminated due to a reference count of 0 */
  private var _terminatedActors: Set[Name] = Set()

  //// Accessor methods

  def idle(actor: Name): Boolean = status(actor) == Idle

  def busy(actor: Name): Boolean = status(actor) == Busy

  def terminated(actor: Name): Boolean = _terminatedActors contains actor

  def receptionist(actor: Name): Boolean = ???

  def canDeactivate(actor: Name): Boolean = ???

  def canTakeSnapshot(actor: Name): Boolean =
    idle(actor) && !terminated(actor)

  def unblocked(actor: Name): Boolean =
    busy(actor) || pendingMessages(actor).nonEmpty || receptionist(actor)

  def blocked(actor: Name): Boolean =
    idle(actor) && pendingMessages(actor).isEmpty && !receptionist(actor)

  def ready(actor: Name): Boolean =
    idle(actor) && pendingMessages(actor).nonEmpty

  /** An actor is garbage if it is only potentially reachable by blocked actors.
   * This excludes actors that have self-terminated.
   */
  def garbage(actor: Name): Boolean =
    !terminated(actor) && canPotentiallyReach(actor).forall(blocked)

  def idleActors: Iterable[Name] = actors.filter(idle)
  def busyActors: Iterable[Name] = actors.filter(busy)
  def blockedActors: Iterable[Name] = actors.filter(blocked)
  def unblockedActors: Iterable[Name] = actors.filter(unblocked)
  def receptionists: Iterable[Name] = actors.filter(receptionist)
  def readyActors: Iterable[Name] = actors.filter(ready)
  def terminatedActors: Iterable[Name] = actors.filter(terminated)
  def actorsThatCanDeactivate: Iterable[Name] = actors.filter(canDeactivate)
  def actorsThatCanTakeASnapshot: Iterable[Name] = actors.filter(canTakeSnapshot)
  def garbageActors: Iterable[Name] = actors.filter(garbage)

  def actors: Iterable[Name] = state.keys
  def actorStates: Iterable[Behavior] = state.values

  /**
   * The set of all pending refobs in the configuration. A refob is pending if
   * it is contained in an in-transit `AppMessage`.
   */
  def pendingRefobs: Iterable[Ref] =
    for {
      msgs <- pendingMessages.values
      msg <- msgs.toIterable
    } yield ???

  /**
   * The set of all deactivated refobs in the configuration. A refob is deactivated
   * if its owner has deactivated it, but the target has not yet processed the
   * corresponding `ReleaseMessage`.
   */
  def deactivatedRefobs: Iterable[Ref] =
    for {
      msgs <- pendingMessages.values
      msg <- msgs.toIterable
    } yield ???

  /**
   * The set of all active refobs in the configuration. A refob is active if it is
   * in an actor's `activeRefs` set.
   */
  def activeRefobs: Iterable[Ref] =
    for {
      actorState <- actorStates
    } yield ???

  /**
   * The set of all unreleased refobs pointing to an actor. A refob is unreleased if
   * it is either potentially active, active, or deactivated (and not yet released).
   */
  def unreleasedRefobs(actor: Name): Iterable[Ref] = {
    val potentiallyActive = pendingRefobs.filter(_.rawActorRef == actor)
    val active = activeRefobs.filter(_.rawActorRef == actor)
    val deactivated = deactivatedRefobs.filter(_.rawActorRef == actor)

    (potentiallyActive ++ active ++ deactivated).toSet
  }

  /** Gets the owners of the unreleased refobs to an actor, i.e. the actor's potential inverse acquaintances.
   * Note that, since all actors have a `self` refob, this set includes the actor itself.
   */
  def potentialInverseAcquaintances(actor: Name): Iterable[Name] = ???

  /** Returns the set of actors that can potentially reach the given actor.
   * This set always includes the actor itself.
   */
  def canPotentiallyReach(actor: Name): Set[Name] = {

    // Traverse the actor graph depth-first to compute the transitive closure.
    // The `visited` set contains all the actors seen so far in the traversal.
    def traverse(actor: Name, visited: Set[Name]): Set[Name] = {
      var nowVisited = visited + actor

      for (invAcq <- potentialInverseAcquaintances(actor)) {
        // Add everything that can potentially reach `invAcq` to the `visited` set,
        // if we haven't visited `invAcq` already.
        if (!(nowVisited contains invAcq)) {
          nowVisited = traverse(invAcq, nowVisited)
        }
      }
      nowVisited
    }

    traverse(actor, Set())
  }


  //// Helper methods

  def transition(e: Event): Unit = {
    e match {
      case Spawn(parent, child, creatorRef, selfRef) =>
        require(state contains parent)
        require(!(state contains child))
        require(!(state(parent).activeRefs contains creatorRef))
        require(busy(parent))
        // create child's state
        val childState = ???
        // add the new active ref to the parent's state
        ???
        // update the configuration
        state += (child -> childState)
        status += (child -> Busy)
        pendingMessages += (child -> new FIFOMailbox())

      case Send(sender, recipientRef, createdRefs, createdUsingRefs) =>
        require(state contains sender)
        require(state(sender).activeRefs contains recipientRef)
        require(createdUsingRefs.forall(ref => state(sender).activeRefs contains ref))
        require(busy(sender))
        val senderState = state(sender)
        // Add createdRefs to sender's state
        ???
        // add the message to the recipient's "mailbox"
        ???

      case DroppedMessage(recipient, sender) =>
        require(state contains recipient)
        require(state contains sender)
        // take the next message from this sender out of the queue;
        // don't do anything with it
        pendingMessages(recipient).deliverFrom(sender)

      case Receive(recipient, sender) =>
        require(state contains recipient)
        require(state contains sender)
        require(idle(recipient))
        // take the next message from this sender out of the queue;
        // this should mimic the behavior of [[gc.AbstractBehavior]]
        val message = pendingMessages(recipient).deliverFrom(sender)
        val actorState = state(recipient)
        // handle the message
        ???

      case BecomeIdle(actor) =>
        require(state contains actor)
        require(busy(actor))
        status += (actor -> Idle)

      case Deactivate(actor, ref) =>
        require(state contains actor)
        require(state(actor).activeRefs contains ref)
        require(ref != state(actor).selfRef)
        require(busy(actor))
        ???

      case Snapshot(actor) =>
        require(state contains actor)
        require(idle(actor))
        require(!terminated(actor))
        val snapshot = ???
        snapshots :+= ((actor, snapshot))
    }
    // Once successful, add the event to the execution so far
    execution = execution :+ e
  }

  def stateToString(name: Name, state: Behavior): String =
    s"""    $name:
       |      Active:  ${state.activeRefs}
       |      Status:   ${status(name)}
       |      Mailbox:  ${pendingMessages(name)}
       |      Unreleased refobs: ${unreleasedRefobs(name)}
       |""".stripMargin

  override def toString: String = {
    s"""Configuration:
       |  Execution:
       |    $execution
       |  Snapshots:
       |    $snapshots
       |  States:
       |${state.keys.map(name => stateToString(name, state(name))).mkString("\n")}
       |  Blocked:
       |    $blockedActors
       |  Garbage:
       |    $garbageActors
       |""".stripMargin
  }

  object Name {
    // just use an internal counter to make unique addresses
    var count: Int = 0
    def apply(): Name = {
      val name = new Name(count)
      count += 1
      name
    }
  }

}

object Configuration {

  sealed trait ActorStatus
  case object Idle extends ActorStatus
  case object Busy extends ActorStatus

  def fromExecution(execution: Execution): Configuration = {
    val config = new Configuration()
    for (event <- execution) config.transition(event)
    config
  }
}
