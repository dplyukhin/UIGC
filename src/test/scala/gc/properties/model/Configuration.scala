package gc.properties.model

import gc.ActorState

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

  val initialActor: DummyName = DummyName()

  /** A mapping from all actors in the configuration to their states */
  var state: Map[DummyName, DummyState] = Map(
    initialActor -> new DummyState(
      self = DummyRef(Some(initialActor), initialActor),
      creator = DummyRef(None, initialActor)
    )
  )

  /** An indicator of whether an actor is idle, blocked, or stopped */
  var status: Map[DummyName, Configuration.ActorStatus] = Map(
    initialActor -> Busy
  )

  /** Associates each actor to the collection of undelivered messages that have been sent to them */
  var pendingMessages: Map[DummyName, PendingMessages] = Map(
    initialActor -> new PendingMessages()
  )

  /** This sequence is the list of snapshots taken by actors throughout this configuration. */
  var snapshots: Seq[(DummyName, DummySnapshot)] = Seq()

  /** The execution that produced this configuration. */
  var execution: Execution = Seq()

  /** The set of actors that have self-terminated due to a reference count of 0 */
  private var _terminatedActors: Set[DummyName] = Set()

  //// Accessor methods

  def idle(actor: DummyName): Boolean = status(actor) == Idle

  def busy(actor: DummyName): Boolean = status(actor) == Busy

  def terminated(actor: DummyName): Boolean = _terminatedActors contains actor

  def receptionist(actor: DummyName): Boolean =
    state(actor).owners.exists(_.owner.isEmpty)

  def canDeactivate(actor: DummyName): Boolean = {
    val s = state(actor)
    busy(actor) && s.activeRefs.exists(_ != s.selfRef)
  }

  def canTakeSnapshot(actor: DummyName): Boolean =
    idle(actor) && !terminated(actor)

  def unblocked(actor: DummyName): Boolean =
    busy(actor) || pendingMessages(actor).nonEmpty || receptionist(actor)

  def blocked(actor: DummyName): Boolean =
    idle(actor) && pendingMessages(actor).isEmpty && !receptionist(actor)

  def ready(actor: DummyName): Boolean =
    idle(actor) && pendingMessages(actor).nonEmpty

  /** An actor is garbage if it is only potentially reachable by blocked actors.
   * This excludes actors that have self-terminated.
   */
  def garbage(actor: DummyName): Boolean =
    !terminated(actor) && canPotentiallyReach(actor).forall(blocked)

  def idleActors: Iterable[DummyName] = actors.filter(idle)
  def busyActors: Iterable[DummyName] = actors.filter(busy)
  def blockedActors: Iterable[DummyName] = actors.filter(blocked)
  def unblockedActors: Iterable[DummyName] = actors.filter(unblocked)
  def receptionists: Iterable[DummyName] = actors.filter(receptionist)
  def readyActors: Iterable[DummyName] = actors.filter(ready)
  def terminatedActors: Iterable[DummyName] = actors.filter(terminated)
  def actorsThatCanDeactivate: Iterable[DummyName] = actors.filter(canDeactivate)
  def actorsThatCanTakeASnapshot: Iterable[DummyName] = actors.filter(canTakeSnapshot)
  def garbageActors: Iterable[DummyName] = actors.filter(garbage)

  def actors: Iterable[DummyName] = state.keys
  def actorStates: Iterable[DummyState] = state.values

  /**
   * The set of all pending refobs in the configuration. A refob is pending if
   * it is contained in an in-transit `AppMessage`.
   */
  def pendingRefobs: Iterable[DummyRef] =
    for {
      msgs <- pendingMessages.values
      msg <- msgs.toIterable
      ref <- msg match {
        case AppMessage(refs, _) => refs
        case _ => Seq()
      }
    } yield ref

  /**
   * The set of all deactivated refobs in the configuration. A refob is deactivated
   * if its owner has deactivated it, but the target has not yet processed the
   * corresponding `ReleaseMessage`.
   */
  def deactivatedRefobs: Iterable[DummyRef] =
    for {
      msgs <- pendingMessages.values
      msg <- msgs.toIterable
      ref <- msg match {
        case ReleaseMessage(releasing, _) => releasing
        case _ => Seq()
      }
    } yield ref

  /**
   * The set of all active refobs in the configuration. A refob is active if it is
   * in an actor's `activeRefs` set.
   */
  def activeRefobs: Iterable[DummyRef] =
    for {
      actorState <- actorStates
      ref <- actorState.activeRefs
    } yield ref

  /**
   * The set of all unreleased refobs pointing to an actor. A refob is unreleased if
   * it is either potentially active, active, or deactivated (and not yet released).
   */
  def unreleasedRefobs(actor: DummyName): Iterable[DummyRef] = {
    val potentiallyActive = pendingRefobs.filter(_.target == actor)
    val active = activeRefobs.filter(_.target == actor)
    val deactivated = deactivatedRefobs.filter(_.target == actor)

    (potentiallyActive ++ active ++ deactivated).toSet
  }

  /** Gets the owners of the unreleased refobs to an actor, i.e. the actor's potential inverse acquaintances.
   * Note that, since all actors have a `self` refob, this set includes the actor itself.
   */
  def potentialInverseAcquaintances(actor: DummyName): Iterable[DummyName] = {
    unreleasedRefobs(actor).map(_.owner.get)
  }

  /** Returns the set of actors that can potentially reach the given actor.
   * This set always includes the actor itself.
   */
  def canPotentiallyReach(actor: DummyName): Set[DummyName] = {

    // Traverse the actor graph depth-first to compute the transitive closure.
    // The `visited` set contains all the actors seen so far in the traversal.
    def traverse(actor: DummyName, visited: Set[DummyName]): Set[DummyName] = {
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

  private def deactivate(actor: DummyName, refs: Iterable[DummyRef]): Unit = {
    val actorState = state(actor)
    // have actor release the refs and update its state
    val targets = actorState.release(refs)
    // set up messages for each target being released
    for ((target, (targetedRefs, createdRefs)) <- targets) {
      pendingMessages(target).add(
        ReleaseMessage(releasing = targetedRefs, created = createdRefs),
        sender = actor
      )
    }
  }

  private def tryTerminating(actor: DummyName, actorState: DummyState): Unit = {
    actorState.tryTerminate() match {
      case ActorState.NotTerminated =>
      case ActorState.RemindMeLater =>
        pendingMessages(actor).add(SelfCheck, sender = actor)
      case ActorState.Terminated =>
        deactivate(actor, actorState.nontrivialActiveRefs)
        _terminatedActors += actor
    }
  }

  def transition(e: Event): Unit = {
    e match {
      case Spawn(parent, child, creatorRef, selfRef) =>
        require(state contains parent)
        require(!(state contains child))
        require(!(state(parent).activeRefs contains creatorRef))
        require(busy(parent))
        // create child's state
        val childState = new DummyState(selfRef, creatorRef)
        // add the new active ref to the parent's state
        state(parent).addRef(creatorRef)
        // update the configuration
        state += (child -> childState)
        status += (child -> Busy)
        pendingMessages += (child -> new PendingMessages())

      case Send(sender, recipientRef, createdRefs, createdUsingRefs) =>
        require(state contains sender)
        require(state(sender).activeRefs contains recipientRef)
        require(createdUsingRefs.forall(ref => state(sender).activeRefs contains ref))
        require(busy(sender))
        val senderState = state(sender)
        // Add createdRefs to sender's state
        for ((targetRef, newRef) <- createdUsingRefs zip createdRefs)
          senderState.handleCreatedRef(targetRef, newRef)
        // increment sender's send count
        senderState.incSentCount(recipientRef.token)
        // add the message to the recipient's "mailbox"
        pendingMessages(recipientRef.target).add(
          AppMessage(createdRefs, recipientRef.token),
          sender
        )

      case Receive(recipient, sender) =>
        require(state contains recipient)
        require(idle(recipient))
        // take the next message from this sender out of the queue;
        // this should mimic the behavior of [[gc.AbstractBehavior]]
        val message = pendingMessages(recipient).deliverFrom(sender)
        val actorState = state(recipient)
        message match {
            case AppMessage(refs, travelToken) =>
              actorState.handleMessage(refs, travelToken)
              status += (recipient -> Busy)
            case ReleaseMessage(releasing, created) =>
              actorState.handleRelease(releasing, created)
              tryTerminating(recipient, actorState)
            case SelfCheck =>
              actorState.handleSelfCheck()
              tryTerminating(recipient, actorState)
          }

      case BecomeIdle(actor) =>
        require(state contains actor)
        require(busy(actor))
        status += (actor -> Idle)

      case Deactivate(actor, ref) =>
        require(state contains actor)
        require(state(actor).activeRefs contains ref)
        require(ref != state(actor).selfRef)
        require(busy(actor))
        deactivate(actor, Seq(ref))

      case Snapshot(actor) =>
        require(state contains actor)
        require(idle(actor))
        require(!terminated(actor))
        val snapshot = state(actor).snapshot()
        snapshots :+= ((actor, snapshot))
    }
    // Once successful, add the event to the execution so far
    execution = execution :+ e
  }

  def stateToString(name: DummyName, state: DummyState): String =
    s"""    $name:
       |      Active:  ${state.activeRefs}
       |      Created: ${state.createdUsing}
       |      Owners:  ${state.owners}
       |      Released: ${state.releasedOwners}
       |      Sent:     ${state.sentCount}
       |      Received: ${state.recvCount}
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

  object DummyName {
    // just use an internal counter to make unique addresses
    var count: Int = 0
    def apply(): DummyName = {
      val name = new DummyName(count)
      count += 1
      name
    }
  }

  object DummyRef {
    def apply(owner: Option[DummyName], target: DummyName): DummyRef =
      if (owner.isDefined)
        new DummyRef(Some(DummyToken()), owner, target)
      else
        new DummyRef(None, None, target)
  }

  object DummyToken {
    var count = 0
    def apply(): DummyToken = {
      val t = new DummyToken(count)
      count += 1
      t
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
