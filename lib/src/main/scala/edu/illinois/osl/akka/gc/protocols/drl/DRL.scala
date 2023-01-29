package edu.illinois.osl.akka.gc.protocols.drl

import akka.actor.typed.{PostStop, Terminated, Signal}
import scala.collection.mutable
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
  ): State = {
    val state = new State(context.self, spawnInfo)
    initializeRefob(state.selfRef, state, context)
    state
  }

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
    initializeRefob(ref, state, ctx)
    state.addRef(ref)
    ctx.watch(child)
    ref
  }

  override def onMessage[T](
    msg: GCMessage[T],
    state: State,
    ctx: ContextLike[GCMessage[T]]
  ): Option[T] =
    msg match {
      case AppMsg(payload, token, refs) =>
        refs.foreach(ref => initializeRefob(ref, state, ctx))
        state.handleMessage(refs, token)
        Some(payload)
      case ReleaseMsg(releasing, created) =>
        state.handleRelease(releasing, created)
        None
      case SelfCheck =>
        state.handleSelfCheck()
        None
      // case TakeSnapshot =>
      //   // snapshotAggregator.put(context.self.target, context.snapshot())
      //   context.snapshot()
      //   None
      case Kill =>
        None
    }

  override def onIdle[T](
    msg: GCMessage[T],
    state: State,
    ctx: ContextLike[GCMessage[T]]
  ): Protocol.TerminationDecision =
    msg match {
      case Kill =>
        Protocol.ShouldStop
      case _ =>
        tryTerminate(state, ctx)
    }

  /**
   * Attempts to terminate this actor, sends a [[SelfCheck]] message to try again if it can't.
   * @return Either [[AkkaBehaviors.stopped]] or [[AkkaBehaviors.same]].
   */
  def tryTerminate[T](
    state: State,
    ctx: ContextLike[GCMessage[T]]
  ): Protocol.TerminationDecision = {
    if (ctx.anyChildren || state.anyInverseAcquaintances || state.anyPendingSelfMessages)
      Protocol.ShouldContinue
    else
      Protocol.ShouldStop
  }

  override def createRef[S,T](
    target: Refob[S], 
    owner: Refob[Nothing],
    state: State,
    ctx: ContextLike[GCMessage[T]]
  ): Refob[S] = {
    val ref = state.newRef(owner, target)
    state.handleCreatedRef(target, ref)
    ref
  }

  override def release[S,T](
    releasing: Iterable[Refob[S]],
    state: State,
    ctx: ContextLike[GCMessage[T]]
  ): Unit = {

    val targets: mutable.HashMap[Name, (Seq[Ref], Seq[Ref])]
      = state.release(releasing)

    // send the release message for each target actor
    for ((target, (targetedRefs, createdRefs)) <- targets) {
      target ! ReleaseMsg(targetedRefs, createdRefs)
    }
  }

  override def releaseEverything[T](
    state: State,
    ctx: ContextLike[GCMessage[T]]
  ): Unit = 
    release(state.nontrivialActiveRefs, state, ctx)

  override def preSignal[T](
    signal: Signal, 
    state: State,
    ctx: ContextLike[GCMessage[T]]
  ): Unit = ()

  override def postSignal[T](
    signal: Signal, 
    state: State,
    ctx: ContextLike[GCMessage[T]]
  ): Protocol.TerminationDecision =
    signal match {
      case signal: Terminated =>
        tryTerminate(state, ctx)
      case signal =>
        Protocol.Unhandled
    }

  def initializeRefob[T](
    refob: Refob[Nothing],
    state: State,
    ctx: ContextLike[GCMessage[T]]
  ): Unit =
    refob.initialize(state)
}