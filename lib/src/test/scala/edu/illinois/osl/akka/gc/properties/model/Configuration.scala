package edu.illinois.osl.akka.gc.properties.model

import edu.illinois.osl.akka.gc.protocol
import edu.illinois.osl.akka.gc.interfaces._
import scala.collection.mutable
import edu.illinois.osl.akka.gc.protocols.Protocol

case class Name(val id: Int) extends RefLike[Msg] {
  override def !(msg: Msg): Unit = {
    val config = Configuration.currentConfig  // FIXME ugly hack
    config.mailbox(this).add(msg, config.currentActor)
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
  var currentMessage: Option[Msg] = None

  def anyChildren: Boolean = 
    config.children(self).nonEmpty
  def watch[U](_other: RefLike[U]): Unit = {
    val other = _other.asInstanceOf[Name]
    config.watchers(other) = 
      config.watchers.getOrElse(other, Set[Name]()) + self
  }
}

object Configuration {
  sealed trait RefobStatus 
  case object Pending extends RefobStatus
  case object Active extends RefobStatus
  case object Deactivated extends RefobStatus
  case object Released extends RefobStatus

  var currentConfig: Configuration = null

  def initialConfig(): Configuration = {
    val config = new Configuration()
    val actor = config.newName()
    val ctx = new Context(
      actor, config, protocol.rootSpawnInfo,
      busy = true, root = true, 
    )
    config.context(actor) = ctx
    config.mailbox(actor) = new FIFOMailbox[Msg]()
    config.children(actor) = Set()
    config.watchers(actor) = Set()
    config
  }

  def execute(execution: Execution): Configuration = {
    val config = initialConfig()
    currentConfig = config // FIXME ugly hack
    for (event <- execution) {
      config.transition(event)
    }
    config
  }

  def checkDepthFirst(depth: Int)(property: (Configuration, Execution) => Unit): Unit = {
    var total = 0
    def go(execution: Execution): Unit = {
      val nextConfig = execute(execution)
      property(nextConfig, execution)
      total += 1
      if (execution.length < depth)
        for (event <- nextConfig.legalEvents)
          go(execution :+ event)
    }
    go(Nil)
    println(s"Checked $total executions")
  }

  def check(depth: Int)(property: (Configuration, Execution) => Unit): Unit = {
    var total = 0
    def go(executions: List[Execution], currentDepth: Int = 1): Unit = {
      val nextDepth = for {
          execution <- executions
          config = execute(execution)
          _ = property(config, execution)
          choice <- config.legalEvents
        } yield 
          execution :+ choice
      println(s"Checked ${executions.length} executions of depth $currentDepth")
      if (currentDepth < depth)
        go(nextDepth, currentDepth + 1)
    }
    go(initialConfig().legalEvents.map(List(_)))
  }
}

class Configuration {
  import Configuration._

  var counter: Int = 0
  var context: mutable.Map[Name, Context] = mutable.Map()
  var mailbox: mutable.Map[Name, Mailbox[Msg]] = mutable.Map()
  var children: mutable.Map[Name, Set[Name]] = mutable.Map()
  var watchers: mutable.Map[Name, Set[Name]] = mutable.Map()
  var terminated: mutable.Set[Name] = mutable.Set()
  var deactivated: mutable.Set[Ref] = mutable.Set()

  // Ugly hack so that we can get FIFO delivery to work properly
  var currentActor: Name = null

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
    new Name(counter-1)
  }

  def legalSpawnEvents: List[Spawn] =
    for {
      parent <- busyActors.toList
    } yield Spawn(parent)

  def legalSendEvents: List[Send] = {
    // The number of possible send events is unbounded; pick only the ones that 
    // send up to 3 refs in a message.
    def powerset[T](n: Int, refs: List[T]): List[List[T]] =
      refs match {
        case Nil => List(Nil)
        case ref :: refs if n == 0 => List(Nil)
        case ref :: refs =>
          powerset(n, refs) ++
          powerset(n-1, refs).map(ref :: _)
      }

    for {
      sender <- busyActors.toList;
      recipient <- context(sender).activeRefs;
      targetRefs <- powerset(3, context(sender).activeRefs.toList)
    } yield Send(sender, recipient, targetRefs)
  }

  def legalReceiveEvents: List[Receive] =
    for {
      recipient <- idleActors.toList;
      msg <- mailbox(recipient).next
     } yield Receive(recipient, msg)

  def legalBecomeIdleEvents: List[BecomeIdle] =
    for (actor <- busyActors.toList) yield BecomeIdle(actor)

  def legalDeactivateEvents: List[Deactivate] =
    for {
      actor <- busyActors.toList;
      ref <- context(actor).activeRefs
    } yield Deactivate(actor, ref)

  def legalSnapshotEvents: List[Snapshot] =
    for (actor <- idleActors.toList) yield Snapshot(actor)

  def legalDroppedMessageEvents: List[DroppedMessage] =
    for {
      recipient <- idleActors.toList;
      msg <- mailbox(recipient).next
    } yield DroppedMessage(recipient, msg)

  def legalEvents: List[Event] =
    legalSpawnEvents ++ legalSendEvents ++ legalReceiveEvents ++
    legalBecomeIdleEvents ++ legalDeactivateEvents ++ legalSnapshotEvents ++
    legalDroppedMessageEvents 

  def transition(event: Event): Unit = event match {
    case Spawn(parent) => 
      currentActor = parent
      val child = newName()
      var childContext: Context = null
      def spawn(info: protocol.SpawnInfo): Name = {
        childContext = new Context(
          child, this, info,
          busy = true, root = false, 
        )
        child
      }
      // create child's state
      val refob = protocol.spawnImpl(
        spawn,
        context(parent).gcState,
        context(parent)
      )
      // add the new active ref to the parent's state
      context(parent).activeRefs += refob
      // update the configuration
      context(child) = childContext
      mailbox(child) = new FIFOMailbox()
      children(child) = Set()
      watchers(child) = Set()
      children(parent) += child

    case Send(sender, recipientRef, targetRefs) =>
      currentActor = sender
      val senderCtx = context(sender)
      // Create refs
      val createdRefs = for (target <- targetRefs) yield
        protocol.createRef(target, recipientRef, senderCtx.gcState)
      // send the message, as well as any control messages needed in the protocol
      recipientRef.tell(new Payload(), createdRefs)

    case DroppedMessage(recipient, msg) =>
      // take the next message from this sender out of the queue;
      // don't do anything with it
      mailbox(recipient).deliverMessage(msg)

    case Receive(recipient, msg) =>
      currentActor = recipient
      // take the next message from this sender out of the queue;
      mailbox(recipient).deliverMessage(msg)
      val ctx = context(recipient)
      // handle the message
      val option = protocol.onMessage(msg, ctx.gcState, ctx)
      option match {
        case Some(_) =>
          // start processing
          ctx.busy = true
          ctx.currentMessage = Some(msg)
        case None =>
          // immediately finish processing
          ctx.busy = false
          protocol.onIdle(msg, ctx.gcState, ctx) match {
            case Protocol.ShouldContinue =>
            case Protocol.ShouldStop =>
              terminated.add(recipient)
              // TODO Trigger watchers when actors terminate (here and elsewhere)
          }
      }

    case BecomeIdle(actor) =>
      currentActor = actor
      val ctx = context(actor)
      ctx.busy = false
      // An actor might be busy because it's spawning a message, or because it
      // was just spawned. We only call `onIdle` in the former case.
      for (msg <- ctx.currentMessage)
        protocol.onIdle(msg, ctx.gcState, ctx) match {
          case Protocol.ShouldContinue =>
          case Protocol.ShouldStop =>
            terminated.add(actor)
            // TODO Trigger watchers when actors terminate (here and elsewhere)
        }
      ctx.currentMessage = None

    case Deactivate(actor, ref) =>
      currentActor = actor
      val ctx = context(actor)
      ctx.activeRefs -= ref
      protocol.release(ref :: Nil, ctx.gcState)

    case Snapshot(actor) =>
      currentActor = actor
      // do nothing for now
  }

}