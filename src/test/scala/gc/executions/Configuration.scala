package gc.executions

import gc.ActorState

import scala.collection.mutable

/**
 * This class represents a simulated Actor System for the purpose of property-based testing.
 * An [[Event]] can be used to transition the configuration from one global state to another
 * if that event is legal in the current global state. For example, an actor cannot send a
 * message to another actor that it does not have a reference to.
 *
 * In the initial configuration, there is just one actor that acts as a receptionist, i.e.
 * it never terminates.
 */
class Configuration(
                     var state: Map[DummyName, DummyState],
                     var status: Map[DummyName, Configuration.ActorStatus],
                     private var mailbox: Map[DummyName, mutable.Queue[ExecMessage]],
) {
  import Configuration._

  /** This sequence is the list of snapshots taken by actors throughout this configuration. */
  var snapshots: Seq[(DummyName, DummySnapshot)] = Seq()


  //// Accessor methods

  def idle(actor: DummyName): Boolean = status(actor) == Idle

  def busy(actor: DummyName): Boolean = status(actor) == Busy

  def stopped(actor: DummyName): Boolean = status(actor) == Stopped

  def pendingMessages(name: DummyName): Iterable[ExecMessage] = mailbox(name)

  def liveActors: Iterable[DummyName] = state.keys.filter { !stopped(_) }

  def stoppedActors: Iterable[DummyName] = state.keys.filter { stopped }

  def busyActors: Iterable[DummyName] = state.keys.filter { busy }

  def idleActors: Iterable[DummyName] = state.keys.filter { idle }

  /** Returns the actors that have active refs, excluding the unreleasable `self` ref */
  def actorsWithActiveRefs: Iterable[DummyName] = for {
    actor <- busyActors
    s = state(actor)
    if (s.activeRefs - s.selfRef).nonEmpty
  } yield actor

  /** Returns the actors that are busy or are idle with pending messages */
  def unblockedActors: Iterable[DummyName] = state.keys.filter {
    actor => busy(actor) || (idle(actor) && pendingMessages(actor).nonEmpty)
  }

  /** Returns the actors that are idle and have pending messages */
  def readyActors: Iterable[DummyName] = state.keys.filter {
    actor => idle(actor) && pendingMessages(actor).nonEmpty
  }

  /** Returns the actors that are idle and have no messages left to process. */
  def blockedActors: Iterable[DummyName] = state.keys.filter {
    actor => idle(actor) && pendingMessages(actor).isEmpty
  }

  /**
   * The set of all pending refobs in the configuration. A refob is pending if
   * it is contained in an in-transit `AppMessage`.
   */
  def pendingRefobs: Iterable[DummyRef] =
    for {
      mail <- mailbox.values
      msg <- mail
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
      mail <- mailbox.values
      msg <- mail
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
      actorState <- state.values
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

  /** Returns the set of actors that can potential reach the given actor. */
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

  /** Returns the garbage actors in a configuration. */
  def garbageActors: Set[DummyName] = {
    // actors are garbage iff they are blocked and only potentially reachable by blocked actors
    var garbage: Set[DummyName] = Set()
    val blocked = blockedActors.toSet
    for (actor <- blocked) {
      // for every blocked actor, get the actors that can reach it
      val canReach = canPotentiallyReach(actor)
      // if all the actors that can reach this actor are also blocked, then this actor is garbage
      if (canReach subsetOf blocked) {
        garbage += actor
      }
    }
    garbage
  }


  //// Helper methods

  private def deactivate(actor: DummyName, refs: Iterable[DummyRef]): Unit = {
    val actorState = state(actor)
    // have actor release the refs and update its state
    val targets = actorState.release(refs)
    // set up messages for each target being released
    for ((target, (targetedRefs, createdRefs)) <- targets) {
      val m = mailbox(target)
      m.enqueue(ReleaseMessage(releasing = targetedRefs, created = createdRefs))
    }
  }

  private def tryTerminating(actor: DummyName, actorState: DummyState): Unit = {
    actorState.tryTerminate() match {
      case ActorState.NotTerminated =>
      case ActorState.RemindMeLater =>
        mailbox(actor).enqueue(SelfCheck)
      case ActorState.Terminated =>
        deactivate(actor, actorState.nontrivialActiveRefs)
        status += (actor -> Stopped)
    }
  }

  def transition(e: Event): Unit = {
    e match {
      case Spawn(parent, child) =>
        // create new references
        val creatorRef = DummyRef(Some(parent), child) // parent's ref to child
        val selfRef = DummyRef(Some(child), child) // child's self-ref
        // create child's state
        val childState = new DummyState(selfRef, creatorRef)
        // add the new active ref to the parent's state
        state(parent).addRef(creatorRef)
        // update the configuration
        state += (child -> childState)
        status += (child -> Busy)
        mailbox += (child -> mutable.Queue())

      case Send(sender, recipientRef, createdRefs, createdUsingRefs) =>
        val senderState = state(sender)
        // Add createdRefs to sender's state
        for ((targetRef, newRef) <- createdUsingRefs zip createdRefs)
          senderState.handleCreatedRef(targetRef, newRef)
        // increment sender's send count
        senderState.incSentCount(recipientRef.token)
        // add the message to the recipient's "mailbox"
        val m = mailbox(recipientRef.target)
        m.enqueue(AppMessage(createdRefs, recipientRef.token))

      case Receive(recipient) =>
        require(!stopped(recipient))
        // take the next message out from the queue;
        // this should mimic the behavior of [[gc.AbstractBehavior]]
        val message = mailbox(recipient).dequeue
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
        status += (actor -> Idle)

      case Deactivate(actor, ref) =>
        deactivate(actor, Seq(ref))

      case Snapshot(actor) =>
        val snapshot = state(actor).snapshot()
        snapshots :+= ((actor, snapshot))
    }
  }
}

object Configuration {

  sealed trait ActorStatus
  case object Idle extends ActorStatus
  case object Busy extends ActorStatus
  case object Stopped extends ActorStatus

  def apply(): Configuration = {

    // the initial actor
    val A = DummyName()
    // dummy reference for the receptionist A
    val x = DummyRef(None, A)
    // reference from A to itself
    val y = DummyRef(Some(A), A)
    // A starts knowing itself and that it is a receptionist
    val aState = new DummyState(y, x)

    new Configuration(Map(A -> aState), Map(A -> Busy), Map(A -> mutable.Queue()))
  }
}
