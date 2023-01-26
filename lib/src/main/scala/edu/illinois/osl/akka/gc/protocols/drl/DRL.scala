package edu.illinois.osl.akka.gc.protocols.drl

import akka.actor.typed.{PostStop, Terminated, Signal}
import edu.illinois.osl.akka.gc.interfaces._
import edu.illinois.osl.akka.gc.protocols.{Protocol, drl}

object DRL extends Protocol {

  type GCMessage[+T] = drl.GCMessage[T]
  type Refob[-T] = drl.Refob[T]
  type State = drl.State

  class SpawnInfo(
    val token: Option[Token],
    val creator: Option[Name]
  )

  override def rootMessage[T](payload: T, refs: Iterable[RefobLike[Nothing]]): GCMessage[T] =
    AppMsg(payload, None, refs.asInstanceOf[Iterable[Refob[Nothing]]])

  override def rootSpawnInfo(): SpawnInfo = 
    new SpawnInfo(None, None)

  override def initState[T](
    context: ContextLike[GCMessage[T]],
    spawnInfo: SpawnInfo,
  ): State =
    new State(context.self, spawnInfo)

  def getSelfRef[T](
    state: State,
    context: ContextLike[GCMessage[T]]
  ): Refob[T] =
    state.selfRef.asInstanceOf[Refob[T]]

  override def spawnImpl[S, T](
    factory: SpawnInfo => RefLike[GCMessage[S]],
    state: State,
    ctx: ContextLike[GCMessage[T]]
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

  override def onMessage[T, Beh](
    msg: GCMessage[T],
    uponMessage: T => Beh,
    state: State,
    ctx: ContextLike[GCMessage[T]]
  ): Protocol.TerminationDecision[Beh] =
    msg match {
      case ReleaseMsg(releasing, created) =>
        state.handleRelease(releasing, created)
        if (tryTerminate(state, ctx))
          Protocol.ShouldStop
        else 
          Protocol.ShouldContinue
      case AppMsg(payload, token, refs) =>
        refs.foreach(ref => ref.initialize(state))
        state.handleMessage(refs, token)
        val beh = uponMessage(payload)
        Protocol.ContinueWith(beh)
      case SelfCheck =>
        state.handleSelfCheck()
        if (tryTerminate(state, ctx))
          Protocol.ShouldStop
        else 
          Protocol.ShouldContinue
      // case TakeSnapshot =>
      //   // snapshotAggregator.put(context.self.target, context.snapshot())
      //   context.snapshot()
      //   AkkaBehaviors.same
      case Kill =>
        Protocol.ShouldStop
    }

  /**
   * Attempts to terminate this actor, sends a [[SelfCheck]] message to try again if it can't.
   * @return Either [[AkkaBehaviors.stopped]] or [[AkkaBehaviors.same]].
   */
  def tryTerminate[T](
    state: State,
    ctx: ContextLike[GCMessage[T]]
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

  override def createRef[S](
    target: Refob[S], owner: Refob[Nothing],
    state: State
  ): Refob[S] = {
    val ref = state.newRef(owner, target)
    state.handleCreatedRef(target, ref)
    ref
  }

  override def release[S](
    releasing: Iterable[Refob[S]],
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

  override def onSignal[T, Beh](
    signal: Signal, 
    uponSignal: Signal => Beh,
    state: State,
    ctx: ContextLike[GCMessage[T]]
  ): Protocol.TerminationDecision[Beh] =
    signal match {
      case PostStop =>
        // snapshotAggregator.unregister(context.self.target)
        // Forward the signal to the user level if there's a handler; else do nothing.
        val beh = uponSignal(signal)
        Protocol.ContinueWith(beh)

      case signal: Terminated =>
        // Try handling the termination signal first
        val beh = uponSignal(signal)
        // Now see if we can terminate
        if (tryTerminate(state, ctx))  
          Protocol.ShouldStop
        else
          Protocol.ContinueWith(beh)
      case signal =>
        val beh = uponSignal(signal)
        Protocol.ContinueWith(beh)
    }
}