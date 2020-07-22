package gc.executions

import scala.collection.mutable

/**
 *
 * @param states A map from actor addresses to their states.
 * @param busy A map from actor addresses to a boolean for whether they're busy or not (idle).
 * @param msgs A map from actor addresses to bags of messages.
 * @param receptionists Actors that can receive messages from external actors.
 */
case class Configuration[T <: Message](var states: Map[DummyName, DummyState],
                         var busy: Map[DummyName, Boolean],
                         var msgs: Map[DummyName, mutable.Queue[ExecMessage[T]]],
                         var receptionists: Set[DummyName]) {
  def transition(e: Event): Unit = {
    e match {
      case Spawn(parent) =>
        // create child's name
        val child = DummyName()
        // create new references
        val x = DummyRef(parent, child) // parent's ref to child
        val y = DummyRef(child, child) // child's self-ref
        // create child's state
        val childState = DummyState(self = child, activeRefs = Set(y), owners = Set(x, y))
        // add the new active ref to the parent's state
        states(parent).spawn(x)
        // update the configuration
        states += (child -> childState)

      case Send(sender: DummyName, recipient: DummyName, message: AppMessage[T]) =>
        // get the sender's state
        val senderState = states(sender)
        // increment its send count
        senderState.incSentCount(message.travelToken)
        // add the message to the recipient's "mailbox"
        val mailbox = msgs(recipient)
        mailbox.enqueue(message)

      case Receive(recipient: DummyName) =>
        // take the next message out from the queue, going idle if empty
        try {
          val message = msgs(recipient).dequeue
          message match {
            case AppMessage(payload, travelToken) =>
              // if it's an app message, update the recv count and handle any refs
              val recipientState = states(recipient)
              recipientState.incReceivedCount(travelToken)
              recipientState.handleMessage(payload.refs)
            case ReleaseMessage(releasing, created) =>
              // if it's a release message, handle that in its own case
              transition(Release(recipient, releasing, created))
          }
        } catch {
          case e: NoSuchElementException => transition(Idle(recipient))
        }

      case Idle(actor) =>
//        busy(actor) = false
        busy += (actor -> false)

      case SendRelease(actor: DummyName, refs: Iterable[DummyRef]) =>
        val actorState = states(actor)
        // have actor release the refs and update its state
        val targets = actorState.release(refs)
        // set up messages for each target being released
        for ((target, (targetedRefs, createdRefs)) <- targets) {
          val mailbox = msgs(target)
          mailbox.enqueue(ReleaseMessage(releasing = targetedRefs, created = createdRefs))
        }

      case Release(actor, releasing, created) =>
        val actorState = states(actor)
        actorState.handleRelease(releasing, created)

      case Snapshot(actor) =>
        states(actor).snapshot()
    }
  }

  /**
   * Returns whether applying a given event to the configuration is valid.
   * @param e
   * @return
   */
  def isLegal(e: Event): Boolean = {
    e match {
      case Spawn(parent) =>
        // parent is busy and exists
        states.contains(parent) && busy(parent)

      case Send(sender, recipient, message) =>
        // sender has the reference that the message is to be sent on
        states(sender).activeRefs contains DummyRef(message.travelToken, Some(sender), recipient)
        // TODO: add third condition from paper?

      case Receive(recipient) =>
        msgs(recipient).nonEmpty && !busy(recipient)

      case Idle(actor) =>
        // an actor must be busy and have an empty mailbox to go idle
        busy(actor) && msgs(actor).isEmpty

      case SendRelease(actor, refs) =>
        // the actor has to have all the refs being released active
        val active = states(actor).activeRefs
        refs forall(ref => active contains ref)

      case Release(actor, releasing, created) =>
        states contains actor

      case Snapshot(actor) =>
        // actor is idle
        !busy(actor)
    }
  }
}

object Configuration {
  def apply[T <: Message](states: Map[DummyName, DummyState],
            busy: Map[DummyName, Boolean],
            msgs: Map[DummyName, mutable.Queue[ExecMessage[T]]],
            receptionists: Set[DummyName]
           ): Configuration[T] = new Configuration(states, busy, msgs, receptionists)
  /**
   * Default constructor.
   * @return The initial configuration. An actor A is created as a receptionist (to provide an anchor for the entire
   *         configuration so it's not just totally garbage collected).
   */
  def apply[T <: Message](): Configuration[T] = {
    // the initial actor
    val A = DummyName()
    // an "external" actor that does not do anything
    val E = DummyName()
    // reference from that external actor to A
    val x = DummyRef(E, A)
    // reference from A to itself
    val y = DummyRef(A, A)
    // A starts knowing itself and that an external actor has a reference to it
    val aState = DummyState(self = A, activeRefs = Set(y), owners = Set(x, y))
    new Configuration[T](
      states = Map(A -> aState),
      busy = Map(A -> true),
      msgs = Map(),
      receptionists = Set(A),
    )
  }
}
