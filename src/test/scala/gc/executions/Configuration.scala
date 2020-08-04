package gc.executions

import scala.collection.mutable

/**
 *
 * @param states A map from actor addresses to their states.
 * @param busy A map from actor addresses to a boolean for whether they're busy or not (idle).
 * @param msgs A map from actor addresses to bags of messages.
 * @param receptionists Actors that can receive messages from external actors.
 */
class Configuration(var states: Map[DummyName, DummyState],
                    var busy: Map[DummyName, Boolean],
                    var msgs: Map[DummyName, mutable.Queue[ExecMessage]],
                    var receptionists: Set[DummyName]) {
  /** This set tracks what references have been sent in messages throughout this configuration. */
  val sentRefs: mutable.Set[DummyToken] = mutable.Set()
  /** This sequence is the list of snapshots taken by actors throughout this configuration. */
  val snapshots: mutable.Seq[(DummyName, DummySnapshot)] = mutable.Seq()

  def transition(e: Event): Unit = {
    e match {
      case Spawn(parent, child) =>
        // create new references
        val x = DummyRef(parent, child) // parent's ref to child
        val y = DummyRef(child, child) // child's self-ref
        // create child's state
        val childState = DummyState(self = child, activeRefs = Set(y), owners = Set(x, y))
        // add the new active ref to the parent's state
        states(parent).addRef(x)
        // update the configuration
        states += (child -> childState)
        busy += (child -> true)
        msgs += (child -> mutable.Queue())
        DummyName.count += 1

      case CreateRef(actor, refToOwner, refToTarget, newToken) =>
        val state = states(actor)
        state.createRef(refToTarget, refToOwner)

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
              recipientState.handleMessage(refs)
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
        snapshots :+ (actor, snapshot)
    }
  }

  /**
   * Returns whether applying a given event to the configuration is valid.
   * @param e
   * @return
   */
  def isLegal(e: Event): Boolean = {
    e match {
      case Spawn(parent, child) =>
        // child doesn't already exist, parent is busy and exists
        !states.contains(child) && states.contains(parent) && busy(parent)

      case CreateRef(actor, refToOwner, refToTarget, newToken) =>
        if (!states.contains(actor)) {
          return false
        }
        val state = states(actor)
        state.activeRefs.contains(refToOwner) && state.activeRefs.contains(refToTarget)

      case Send(sender, recipient, message) =>
        if (!states.contains(sender) || !states.contains(recipient)) {
          return false
        }
        // sender has the reference that the message is to be sent on
        states(sender).activeRefs.contains(DummyRef(message.travelToken, Some(sender), recipient)) && (
          // sender is not sending any refs that have been sent already
          message.refs forall { ref =>
              !sentRefs.contains(ref.token.get)
              // NOTE: what about case where token/owner is null?
          }
        )

      case Receive(recipient) =>
        // legal when mailbox isn't empty and the actor is idle
        states.contains(recipient) && msgs(recipient).nonEmpty && !busy(recipient)

      case Idle(actor) =>
        // an actor must be busy and have an empty mailbox to go idle
        states.contains(actor) && busy(actor) && msgs(actor).isEmpty

      case Deactivate(actor, ref) =>
        if (!states.contains(actor)) {
          return false
        }
        // the actor has to have the ref being deactivated
        val active = states(actor).activeRefs
        active.contains(ref)

      case Snapshot(actor) =>
        // actor is idle
        states.contains(actor) && !busy(actor)
    }
  }
}

object Configuration {
  def apply(states: Map[DummyName, DummyState],
            busy: Map[DummyName, Boolean],
            msgs: Map[DummyName, mutable.Queue[ExecMessage]],
            receptionists: Set[DummyName]
           ): Configuration = new Configuration(states, busy, msgs, receptionists)
  /**
   * Default constructor.
   * @return The initial configuration. An actor A is created as a receptionist (to provide an anchor for the entire
   *         configuration so it's not just totally garbage collected).
   */
  def apply(): Configuration = {
    // the initial actor
    val A = DummyName()
    // an "external" actor that does not do anything
    val E = DummyName(-1)
    // reference from that external actor to A
    val x = DummyRef(E, A)
    // reference from A to itself
    val y = DummyRef(A, A)
    // A starts knowing itself and that an external actor has a reference to it
    val aState = DummyState(self = A, activeRefs = Set(y), owners = Set(x, y))
    new Configuration(
      states = Map(A -> aState),
      busy = Map(A -> true),
      msgs = Map(A -> mutable.Queue()),
      receptionists = Set(A),
    )
  }
}
