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

  /** Returns whether a refob is unreleased (if it's been created but not released) */
  def unreleased(refob: DummyRef): Boolean = {
    (state(refob.owner.get).activeRefs contains refob) || // contained in the owner's active refs
      (pendingMessages(refob.target) exists { // in transit to target
        case SelfCheck => false
        // TODO: check if travelToken is valid? (i.e. if it identifies a ref held by the owner to the target)
        // TODO 2: I think technically this should look across ALL the mailboxes for an AppMessage with this refob
        case AppMessage(refs, travelToken) => refs.toSet.contains(refob) // in transit in a message from owner
        case ReleaseMessage(releasing, created) => releasing.toSet.contains(refob) // in transit in a deactivation from owner
      })
  }

  /** Finds the potential inverse acquaintances of an actor */
  def potentialInverseAcquaintances(actor: DummyName): Set[DummyName] = {
    var invAcquaintances: Set[DummyName] = Set(actor)
    // get the mailboxes of *other* actors to find any in-transit refobs pointing to the actor
    val inTransitMsgs = for {
      (name, mail) <- mailbox
      msg <- mail
      if name != actor
    } yield msg
    // look through the in-transit messages for created refobs pointing to the actor
    for (msg <- inTransitMsgs) {
      msg match {
        case AppMessage(refs, _) =>
          // find refobs that point to the targeted actor and select their owners
          val owners = for {
            ref <- refs
            if ref.target == actor
            owner <- ref.owner
          } yield owner
          invAcquaintances ++= owners
        case _ => ()
      }
    }
    // look in the actor's own mailbox for release messages with refobs to it
    for (msg <- pendingMessages(actor)) {
      msg match {
        case ReleaseMessage(releasing, created) =>
          // look at the owners of the refobs being released
          val owners = for {
            ref <- releasing
            owner <- ref.owner
          } yield owner
          invAcquaintances ++= owners
        case _ => ()
      }
    }
    // look at all actors's active refs and select the owner of those pointing to the target
    val owners = for {
      (_, s) <- state
      ref <- s.activeRefs
      owner <- ref.owner
      if ref.target == actor
    } yield owner
    invAcquaintances ++= owners
    invAcquaintances
  }

  /** Returns the set of actors that can reach the given actor. */
  def canPotentiallyReach(actor: DummyName, set: Set[DummyName] = Set()): Set[DummyName] = {
    val invAcquaintances = potentialInverseAcquaintances(actor)
    var canReach = Set(actor) ++ invAcquaintances
    for (actor <- invAcquaintances) {
      canReach ++= canPotentiallyReach(actor)
    }
    canReach
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
