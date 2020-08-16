package gc.executions

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
    private var receptionists: Set[DummyName]
) {

  /** This sequence is the list of snapshots taken by actors throughout this configuration. */
  var snapshots: Set[(DummyName, DummySnapshot)] = Set()


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


  def transition(e: Event): Unit = {
    e match {
      case Spawn(parent, child) =>
        // create new references
        val x = DummyRef(Some(parent), child) // parent's ref to child
        val y = DummyRef(Some(child), child) // child's self-ref
        // create child's state
        val childState = new DummyState(y, x)
        // add the new active ref to the parent's state
        states(parent).addRef(x)
        // update the configuration
        states += (child -> childState)
        busy += (child -> true)
        msgs += (child -> mutable.Queue())

      case Send(sender, recipient, message) =>
        // get the sender's state
        val senderState = states(sender)
        // increment its send count
        senderState.incSentCount(message.travelToken)
        // add the message to the recipient's "mailbox"
        val mailbox = msgs(recipient)
        mailbox.enqueue(message)

      case Receive(recipient) =>
        // take the next message out from the queue, going idle if empty
          val message = msgs(recipient).dequeue
          message match {
            case AppMessage(refs, travelToken) =>
              // if it's an app message, update the recv count and handle any refs
              val recipientState = states(recipient)
              recipientState.incReceivedCount(travelToken)
              recipientState.handleMessage(refs, travelToken)
              // become busy as actor does some nonspecified sequence of actions
              busy(recipient)
            case ReleaseMessage(releasing, created) =>
              // if it's a release message, handle that in its own case
              val actorState = states(recipient)
              actorState.handleRelease(releasing, created)
          }

      case Idle(actor) =>
        busy += (actor -> false)

      case Deactivate(actor, ref) =>
        val actorState = states(actor)
        // have actor release the refs and update its state
        val targets = actorState.release(Iterable(ref))
        // set up messages for each target being released
        for ((target, (targetedRefs, createdRefs)) <- targets) {
          val mailbox = msgs(target)
          mailbox.enqueue(ReleaseMessage(releasing = targetedRefs, created = createdRefs))
        }

      case Snapshot(actor) =>
        val snapshot = states(actor).snapshot()
        snapshots += ((actor, snapshot))
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

    new Configuration(Map(A -> aState), Map(A -> true), Map(A -> mutable.Queue()), Set(A))
  }
}
