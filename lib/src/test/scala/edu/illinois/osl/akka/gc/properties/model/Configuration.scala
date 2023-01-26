package edu.illinois.osl.akka.gc.properties.model

import edu.illinois.osl.akka.gc.protocol
import edu.illinois.osl.akka.gc.interfaces._
import scala.collection.mutable

object Configuration {
  sealed trait RefobStatus 
  case object Pending extends RefobStatus
  case object Active extends RefobStatus
  case object Deactivated extends RefobStatus
  case object Released extends RefobStatus

  def initialConfig(): Configuration = {
    val config = new Configuration()
    val actor = config.newName()
    val ctx = new Context(
      actor, config, protocol.rootSpawnInfo,
      busy = true, root = true, 
    )
    config.context(actor) = ctx
    config.mailbox(actor) = new FIFOMailbox[Msg]()
    config
  }
}

class Configuration {
  import Configuration._

  var counter: Int = 0
  var context: mutable.Map[Name, Context] = mutable.Map()
  var mailbox: mutable.Map[Name, Mailbox[Msg]] = mutable.Map()
  var children: mutable.Map[Name, Set[Name]] = mutable.Map()
  var watchedBy: mutable.Map[Name, Set[Name]] = mutable.Map()
  var terminated: mutable.Set[Name] = mutable.Set()
  var sentMessages: List[Msg] = Nil
  var deactivated: mutable.Set[Ref] = mutable.Set()

  //// Accessor methods

  def idle(actor: Name): Boolean = !busy(actor)

  def busy(actor: Name): Boolean = context(actor).busy

  def root(actor: Name): Boolean = context(actor).root

  def unblocked(actor: Name): Boolean =
    busy(actor) || mailbox(actor).nonEmpty || root(actor)

  def blocked(actor: Name): Boolean =
    !unblocked(actor)

  def ready(actor: Name): Boolean =
    idle(actor) && mailbox(actor).nonEmpty

  /** 
   * An actor is garbage if it is only potentially reachable by blocked actors.
   * This excludes actors that have self-terminated.
   */
  def garbage(actor: Name): Boolean =
    !terminated(actor) && canPotentiallyReach(actor).forall(blocked)

  def aliveActors: Iterable[Name] = context.keys
  def idleActors: Iterable[Name] = aliveActors.filter(idle)
  def busyActors: Iterable[Name] = aliveActors.filter(busy)
  def blockedActors: Iterable[Name] = aliveActors.filter(blocked)
  def unblockedActors: Iterable[Name] = aliveActors.filter(unblocked)
  def rootActors: Iterable[Name] = aliveActors.filter(root)
  def readyActors: Iterable[Name] = aliveActors.filter(ready)
  def garbageLiveActors: Iterable[Name] = aliveActors.filter(garbage)


  /** 
   * Gets the owners of the unreleased refobs to an actor, i.e. the actor's potential inverse acquaintances.
   * Note that, since all actors have a `self` refob, this set includes the actor itself.
   */
  def potentialInverseAcquaintances(actor: Name): Iterable[Name] = {
    // Search every mailbox for refobs. If a refob points to the actor, add the
    // recipient as an inverse acquaintance.
    val pendingInverseAcquaintances = for {
        recipient <- mailbox.keys;
        msg <- mailbox(recipient).toIterable;
        ref <- msg.refs;
        if ref.target == actor
      } yield recipient

    // Search every actor's local state for active refs.
    val activeInverseAcquaintances = for {
        owner <- aliveActors;
        ref <- context(owner).activeRefs
        if ref.target == actor
      } yield owner

    (pendingInverseAcquaintances ++ activeInverseAcquaintances).toSet
  }

  /** 
   * Returns the set of actors that can potentially reach the given actor.
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


  def newName(): Name = {
    counter += 1
    new Name(counter, this)
  }

  def legalSpawnEvents: Iterable[Spawn] =
    for {
      parent <- busyActors
    } yield Spawn(parent)

  def legalSendEvents: Iterable[Send] = {
    // The number of possible send events is unbounded; pick only the ones that 
    // send up to 3 refs in a message.
    def powerset(n: Int, refs: List[Ref]): List[List[Ref]] =
      refs match {
        case Nil => Nil
        case ref :: refs if n == 0 => Nil
        case ref :: refs =>
          powerset(n, refs) ++
          powerset(n-1, refs).map(ref :: _)
      }

    for {
      sender <- busyActors;
      recipient <- context(sender).activeRefs;
      targetRefs <- powerset(3, context(sender).activeRefs.toList)
    } yield Send(sender, recipient, targetRefs)
  }

  def legalReceiveEvents: Iterable[Receive] =
    for {
      recipient <- idleActors;
      msg <- mailbox(recipient).next
     } yield Receive(recipient, msg)

  def legalBecomeIdleEvents: Iterable[BecomeIdle] =
    for (actor <- busyActors) yield BecomeIdle(actor)

  def legalDeactivateEvents: Iterable[Deactivate] =
    for {
      actor <- busyActors;
      ref <- context(actor).activeRefs
    } yield Deactivate(actor, ref)

  def legalSnapshotEvents: Iterable[Snapshot] =
    for (actor <- idleActors) yield Snapshot(actor)

  def legalDroppedMessageEvents: Iterable[DroppedMessage] =
    for {
      recipient <- idleActors;
      msg <- mailbox(recipient).next
    } yield DroppedMessage(recipient, msg)

  def legalEvents: Iterable[Event] =
    legalSpawnEvents ++ legalSendEvents ++ legalReceiveEvents ++
    legalBecomeIdleEvents ++ legalDeactivateEvents ++ legalSnapshotEvents ++
    legalDroppedMessageEvents 

  // def transition(event: Event): Unit = event match {
    // case Spawn(parent, child, creatorRef, selfRef) => 
    //   require(state contains parent)
    //   require(!(state contains child))
    //   require(!(state(parent).activeRefs contains creatorRef))
    //   require(busy(parent))
    //   // create child's state
    //   val childState = ???
    //   // add the new active ref to the parent's state
    //   ???
    //   // update the configuration
    //   state += (child -> childState)
    //   status += (child -> Busy)
    //   pendingMessages += (child -> new FIFOMailbox())

    // case Send(sender, recipientRef, createdRefs, createdUsingRefs) =>
    //   require(state contains sender)
    //   require(state(sender).activeRefs contains recipientRef)
    //   require(createdUsingRefs.forall(ref => state(sender).activeRefs contains ref))
    //   require(busy(sender))
    //   val senderState = state(sender)
    //   // Add createdRefs to sender's state
    //   ???
    //   // add the message to the recipient's "mailbox"
    //   ???

    // case DroppedMessage(recipient, sender) =>
    //   require(state contains recipient)
    //   require(state contains sender)
    //   // take the next message from this sender out of the queue;
    //   // don't do anything with it
    //   pendingMessages(recipient).deliverFrom(sender)

    // case Receive(recipient, sender) =>
    //   require(state contains recipient)
    //   require(state contains sender)
    //   require(idle(recipient))
    //   // take the next message from this sender out of the queue;
    //   // this should mimic the behavior of [[gc.AbstractBehavior]]
    //   val message = pendingMessages(recipient).deliverFrom(sender)
    //   val actorState = state(recipient)
    //   // handle the message
    //   ???

    // case BecomeIdle(actor) =>
    //   require(state contains actor)
    //   require(busy(actor))
    //   status += (actor -> Idle)

    // case Deactivate(actor, ref) =>
    //   require(state contains actor)
    //   require(state(actor).activeRefs contains ref)
    //   require(ref != state(actor).selfRef)
    //   require(busy(actor))
    //   ???

    // case Snapshot(actor) =>
    //   require(state contains actor)
    //   require(idle(actor))
    //   require(!terminated(actor))
    //   val snapshot = ???
    //   snapshots :+= ((actor, snapshot))
  // }

}

class Name(
  val id: Int, config: Configuration
) extends RefLike[Msg] {
  override def !(msg: Msg): Unit =
    config.sentMessages = config.sentMessages :+ msg

  override def equals(that: Any): Boolean = that match {
    case that: Name => this.id == that.id
    case _ => false
  }

  override def toString(): String = {
    val alpha = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
    if (id >= 0 && id < 26) 
      return alpha(id).toString()
    else
      return s"A${id}" 
  }
}

class Context(
  val self: Name, 
  val config: Configuration,
  val spawnInfo: protocol.SpawnInfo,
  var busy: Boolean,
  val root: Boolean,
) extends ContextLike[Msg] {

  val gcState: protocol.State = protocol.initState(this, spawnInfo)
  val selfRef: Ref = protocol.getSelfRef(gcState, this)
  var activeRefs: Set[Ref] = Set(selfRef)

  def children: Iterable[RefLike[Nothing]] = 
    config.children(self)
  def watch[U](other: RefLike[U]): Unit = {
    config.watchedBy(self) = config.watchedBy(self) + other.asInstanceOf[Name]
  }
}
