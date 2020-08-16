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
    var states: Map[DummyName, DummyState],
    var busy: Map[DummyName, Boolean],
    private var msgs: Map[DummyName, mutable.Queue[ExecMessage]],
) {

  /** This sequence is the list of snapshots taken by actors throughout this configuration. */
  var snapshots: Seq[(DummyName, DummySnapshot)] = Seq()


  /**** Accessor methods ****/

  def actors: Iterable[DummyName] = states.keys

  def idle(actor: DummyName): Boolean = !busy(actor)

  def pendingMessages(name: DummyName): Iterable[ExecMessage] = msgs(name)

  def busyActors: Iterable[DummyName] = actors.filter { busy(_) }

  def idleActors: Iterable[DummyName] = actors.filter { idle }

  /** Returns the actors that are busy or have pending messages */
  def unblockedActors: Iterable[DummyName] = actors.filter {
    actor => busy(actor) || pendingMessages(actor).nonEmpty
  }

  /** Returns the actors that are idle and have pending messages */
  def readyActors: Iterable[DummyName] = actors.filter {
    actor => idle(actor) && pendingMessages(actor).nonEmpty
  }

  private def deactivate(actor: DummyName, refs: Iterable[DummyRef]): Unit = {
    val actorState = states(actor)
    // have actor release the refs and update its state
    val targets = actorState.release(refs)
    // set up messages for each target being released
    for ((target, (targetedRefs, createdRefs)) <- targets) {
      val mailbox = msgs(target)
      mailbox.enqueue(ReleaseMessage(releasing = targetedRefs, created = createdRefs))
    }
  }

  private def tryTerminating(actor: DummyName, actorState: DummyState): Unit = {
    actorState.tryTerminate() match {
      case ActorState.NotTerminated =>
      case ActorState.RemindMeLater =>
        msgs(actor).enqueue(SelfCheck)
      case ActorState.Terminated =>
        deactivate(actor, actorState.nontrivialRefs)
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
        states(parent).addRef(creatorRef)
        // update the configuration
        states += (child -> childState)
        busy += (child -> true)
        msgs += (child -> mutable.Queue())

      case Send(sender, recipientRef, createdRefs, createdUsingRefs) =>
        // get the sender's state
        val senderState = states(sender)
        // Add createdRefs to sender's state
        for ((targetRef, newRef) <- createdUsingRefs zip createdRefs)
          senderState.handleCreatedRef(targetRef, newRef)
        // increment sender's send count
        senderState.incSentCount(recipientRef.token)
        // add the message to the recipient's "mailbox"
        val mailbox = msgs(recipientRef.target)
        mailbox.enqueue(AppMessage(createdRefs, recipientRef.token))

      case Receive(recipient) =>
        // take the next message out from the queue;
        // this should mimic the behavior of [[gc.AbstractBehavior]]
        val message = msgs(recipient).dequeue
        val actorState = states(recipient)
        message match {
            case AppMessage(refs, travelToken) =>
              actorState.handleMessage(refs, travelToken)
              busy += (recipient -> true)
            case ReleaseMessage(releasing, created) =>
              actorState.handleRelease(releasing, created)
              tryTerminating(recipient, actorState)
            case SelfCheck =>
              tryTerminating(recipient, actorState)
          }

      case Idle(actor) =>
        busy += (actor -> false)

      case Deactivate(actor, ref) =>
        deactivate(actor, Seq(ref))

      case Snapshot(actor) =>
        val snapshot = states(actor).snapshot()
        snapshots :+= ((actor, snapshot))
    }
  }
}

object Configuration {
  def apply(): Configuration = {

    // the initial actor
    val A = DummyName()
    // dummy reference for the receptionist A
    val x = DummyRef(None, A)
    // reference from A to itself
    val y = DummyRef(Some(A), A)
    // A starts knowing itself and that it is a receptionist
    val aState = new DummyState(y, x)

    new Configuration(Map(A -> aState), Map(A -> true), Map(A -> mutable.Queue()))
  }
}
