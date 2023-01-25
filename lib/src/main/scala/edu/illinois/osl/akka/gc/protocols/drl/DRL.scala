package edu.illinois.osl.akka.gc.protocols.drl

import akka.actor.typed.{PostStop, Terminated, Signal}
import edu.illinois.osl.akka.gc.{raw, Protocol, Message, Behavior}
import edu.illinois.osl.akka.gc.protocols.drl

object DRL extends Protocol {

  type GCMessage[+T <: Message] = drl.GCMessage[T]
  type Refob[-T <: Message] = drl.Refob[T]
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
    context: raw.ActorContext[GCMessage[T]],
    spawnInfo: SpawnInfo,
  ): State =
    new State(context.self, spawnInfo)

  override def spawnImpl[S <: Message, T <: Message](
    factory: SpawnInfo => raw.ActorRef[GCMessage[S]],
    state: State,
    ctx: raw.ActorContext[GCMessage[T]]
  ): Refob[S] = {
    val x = state.newToken()
    val self = state.self
    val child = factory(new SpawnInfo(Some(x), Some(self)))
    val ref = new Refob[S](Some(x), Some(self), child)
    ref.initialize(state)
    state.addRef(ref)
    ctx.watch(child)
    ref
  }

  override def onMessage[T <: Message](
    msg: GCMessage[T],
    uponMessage: T => raw.Behavior[GCMessage[T]],
    state: State,
    ctx: raw.ActorContext[GCMessage[T]]
  ): raw.Behavior[GCMessage[T]] =
    msg match {
      case ReleaseMsg(releasing, created) =>
        state.handleRelease(releasing, created)
        if (tryTerminate(state, ctx))
          raw.Behaviors.stopped
        else 
          raw.Behaviors.same
      case AppMsg(payload, token) =>
        val refs = payload.refs.asInstanceOf[Iterable[Refob[Nothing]]]
        refs.foreach(ref => ref.initialize(state))
        state.handleMessage(refs, token)
        uponMessage(payload)
      case SelfCheck =>
        state.handleSelfCheck()
        if (tryTerminate(state, ctx))
          raw.Behaviors.stopped
        else 
          raw.Behaviors.same
      // case TakeSnapshot =>
      //   // snapshotAggregator.put(context.self.target, context.snapshot())
      //   context.snapshot()
      //   AkkaBehaviors.same
      case Kill =>
        raw.Behaviors.stopped
    }

  /**
   * Attempts to terminate this actor, sends a [[SelfCheck]] message to try again if it can't.
   * @return Either [[AkkaBehaviors.stopped]] or [[AkkaBehaviors.same]].
   */
  def tryTerminate[T <: Message](
    state: State,
    ctx: raw.ActorContext[GCMessage[T]]
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
    target: Refob[S], owner: Refob[Nothing],
    state: State
  ): Refob[S] = {
    val ref = state.newRef(owner, target)
    state.handleCreatedRef(target, ref)
    ref
  }

  override def release(
    releasing: Iterable[Refob[Nothing]],
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
    ctx: raw.ActorContext[GCMessage[T]]
  ): Behavior[T] =
    signal match {
      case PostStop =>
        // snapshotAggregator.unregister(context.self.target)
        // Forward the signal to the user level if there's a handler; else do nothing.
        uponSignal.applyOrElse[Signal, Behavior[T]](PostStop, _ => raw.Behaviors.same)

      case signal: Terminated =>
        // Try handling the termination signal first
        val result = uponSignal.applyOrElse[Signal, Behavior[T]](signal, _ => raw.Behaviors.same)
        // Now see if we can terminate
        if (tryTerminate(state, ctx))  
          raw.Behaviors.stopped
        else
          result
      case signal =>
        uponSignal.applyOrElse[Signal, Behavior[T]](signal, _ => raw.Behaviors.same)
    }
}