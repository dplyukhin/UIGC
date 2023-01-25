package edu.illinois.osl.akka.gc.protocols.drl

import akka.actor.typed.{ActorRef => AkkaActorRef, Behavior => AkkaBehavior, PostStop, Terminated, Signal}
import akka.actor.typed.scaladsl.{ActorContext => AkkaActorContext, Behaviors => AkkaBehaviors}
import scala.collection.mutable
import akka.actor.typed.SpawnProtocol
import edu.illinois.osl.akka.gc.{Protocol, Message, AnyActorRef, Behavior}

import edu.illinois.osl.akka.gc.protocols.drl

object DRL extends Protocol {

  type GCMessage[+T <: Message] = drl.GCMessage[T]
  type ActorRef[-T <: Message] = drl.ActorRef[T]
  type State = drl.State

  class SpawnInfo(
    val token: Option[Token],
    val creator: Option[Name]
  )

  override def rootMessage[T <: Message](payload: T): GCMessage[T] =
    AppMsg(payload, None)

  override def rootSpawnInfo(): SpawnInfo = 
    new SpawnInfo(None, None)

  override def initState[T <: Message](
    context: AkkaActorContext[GCMessage[T]],
    spawnInfo: SpawnInfo,
  ): State =
    new State(context.self, spawnInfo)

  override def spawnImpl[S <: Message, T <: Message](
    factory: SpawnInfo => AkkaActorRef[GCMessage[S]],
    state: State,
    ctx: AkkaActorContext[GCMessage[T]]
  ): ActorRef[S] = {
    val x = state.newToken()
    val self = state.self
    val child = factory(new SpawnInfo(Some(x), Some(self)))
    val ref = new ActorRef[S](Some(x), Some(self), child)
    ref.initialize(state)
    state.addRef(ref)
    ctx.watch(child)
    ref
  }

  override def onMessage[T <: Message](
    msg: GCMessage[T],
    uponMessage: T => AkkaBehavior[GCMessage[T]],
    state: State,
    ctx: AkkaActorContext[GCMessage[T]]
  ): AkkaBehavior[GCMessage[T]] =
    msg match {
      case ReleaseMsg(releasing, created) =>
        state.handleRelease(releasing, created)
        if (tryTerminate(state, ctx))
          AkkaBehaviors.stopped
        else 
          AkkaBehaviors.same
      case AppMsg(payload, token) =>
        val refs = payload.refs.asInstanceOf[Iterable[ActorRef[Nothing]]]
        refs.foreach(ref => ref.initialize(state))
        state.handleMessage(refs, token)
        uponMessage(payload)
      case SelfCheck =>
        state.handleSelfCheck()
        if (tryTerminate(state, ctx))
          AkkaBehaviors.stopped
        else 
          AkkaBehaviors.same
      // case TakeSnapshot =>
      //   // snapshotAggregator.put(context.self.target, context.snapshot())
      //   context.snapshot()
      //   AkkaBehaviors.same
      case Kill =>
        AkkaBehaviors.stopped
    }

  /**
   * Attempts to terminate this actor, sends a [[SelfCheck]] message to try again if it can't.
   * @return Either [[AkkaBehaviors.stopped]] or [[AkkaBehaviors.same]].
   */
  def tryTerminate[T <: Message](
    state: State,
    ctx: AkkaActorContext[GCMessage[T]]
  ): Boolean = {
    if (ctx.children.nonEmpty)
      return false

    state.tryTerminate() match {
      case State.NotTerminated =>
        false

      case State.RemindMeLater =>
        ctx.self ! SelfCheck
        false

      case State.AmTerminated =>
        releaseEverything(state)
        true
    }
  }

  override def createRef[S <: Message](
    target: ActorRef[S], owner: ActorRef[Nothing],
    state: State
  ): ActorRef[S] = {
    val ref = state.newRef(owner, target)
    state.handleCreatedRef(target, ref)
    ref
  }

  override def release(
    releasing: Iterable[ActorRef[Nothing]],
    state: State
  ): Unit = {

    val targets: Map[Name, (Seq[Ref], Seq[Ref])]
      = state.release(releasing)

    // send the release message for each target actor
    for ((target, (targetedRefs, createdRefs)) <- targets) {
      target ! ReleaseMsg(targetedRefs, createdRefs)
    }
  }

  override def releaseEverything(state: State): Unit = release(state.nontrivialActiveRefs, state)

  override def onSignal[T <: Message](
    signal: Signal, 
    uponSignal: PartialFunction[Signal, Behavior[T]],
    state: State,
    ctx: AkkaActorContext[GCMessage[T]]
  ): Behavior[T] =
    signal match {
      case PostStop =>
        // snapshotAggregator.unregister(context.self.target)
        // Forward the signal to the user level if there's a handler; else do nothing.
        uponSignal.applyOrElse[Signal, Behavior[T]](PostStop, _ => AkkaBehaviors.same)

      case signal: Terminated =>
        // Try handling the termination signal first
        val result = uponSignal.applyOrElse[Signal, Behavior[T]](signal, _ => AkkaBehaviors.same)
        // Now see if we can terminate
        if (tryTerminate(state, ctx))  
          AkkaBehaviors.stopped
        else
          result
      case signal =>
        uponSignal.applyOrElse[Signal, Behavior[T]](signal, _ => AkkaBehaviors.same)
    }
}