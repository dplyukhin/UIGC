package edu.illinois.osl.akka.gc.protocols.drl

import akka.actor.{Address, ExtendedActorSystem}
import akka.actor.typed.{PostStop, Signal, Terminated}
import akka.actor.typed.ActorRef
import akka.actor.typed.scaladsl.ActorContext
import akka.remote.artery.{ObjectPool, OutboundEnvelope, ReusableOutboundEnvelope}

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
  ) extends Serializable

  override def rootMessage[T](payload: T, refs: Iterable[RefobLike[Nothing]]): GCMessage[T] =
    AppMsg(payload, None, refs.asInstanceOf[Iterable[Refob[Nothing]]])

  override def rootSpawnInfo(): SpawnInfo = 
    new SpawnInfo(None, None)

  override def initState[T](
    context: ActorContext[GCMessage[T]],
    spawnInfo: SpawnInfo,
  ): State = {
    val state = new State(context.self, spawnInfo)
    state
  }

  def getSelfRef[T](
    state: State,
    context: ActorContext[GCMessage[T]]
  ): Refob[T] =
    state.selfRef.asInstanceOf[Refob[T]]

  override def spawnImpl[S, T](
    factory: SpawnInfo => ActorRef[GCMessage[S]],
    state: State,
    ctx: ActorContext[GCMessage[T]]
  ): Refob[S] = {
    val x = state.newToken()
    val self = state.self
    val child = factory(new SpawnInfo(Some(x), Some(self)))
    val ref = new Refob[S](Some(x), Some(self), child)
    state.addRef(ref)
    ctx.watch(child)
    ref
  }

  override def onMessage[T](
    msg: GCMessage[T],
    state: State,
    ctx: ActorContext[GCMessage[T]]
  ): Option[T] =
    msg match {
      case AppMsg(payload, token, refs) =>
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
    ctx: ActorContext[GCMessage[T]]
  ): Protocol.TerminationDecision =
    msg match {
      case Kill =>
        Protocol.ShouldStop
      case _ =>
        tryTerminate(state, ctx)
    }

  /**
   * Attempts to terminate this actor, sends a [[SelfCheck]] message to try again if it can't.
   */
  def tryTerminate[T](
    state: State,
    ctx: ActorContext[GCMessage[T]]
  ): Protocol.TerminationDecision = {
    if (ctx.children.nonEmpty || state.anyInverseAcquaintances || state.anyPendingSelfMessages)
      Protocol.ShouldContinue
    else
      Protocol.ShouldStop
  }

  override def createRef[S,T](
    target: Refob[S], 
    owner: Refob[Nothing],
    state: State,
    ctx: ActorContext[GCMessage[T]]
  ): Refob[S] = {
    val ref = state.newRef(owner, target)
    state.handleCreatedRef(target, ref)
    ref
  }

  override def release[S,T](
    releasing: Iterable[Refob[S]],
    state: State,
    ctx: ActorContext[GCMessage[T]]
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
    ctx: ActorContext[GCMessage[T]]
  ): Unit = 
    release(state.nontrivialActiveRefs, state, ctx)

  override def preSignal[T](
    signal: Signal, 
    state: State,
    ctx: ActorContext[GCMessage[T]]
  ): Unit = ()

  override def postSignal[T](
    signal: Signal, 
    state: State,
    ctx: ActorContext[GCMessage[T]]
  ): Protocol.TerminationDecision =
    signal match {
      case signal: Terminated =>
        tryTerminate(state, ctx)
      case signal =>
        Protocol.Unhandled
    }

  override def sendMessage[T, S](
    ref: Refob[T],
    msg: T,
    refs: Iterable[Refob[Nothing]],
    state: State,
    ctx: ActorContext[GCMessage[S]]
  ): Unit = {
    ref.target ! AppMsg(msg, ref.token, refs)
    state.incSentCount(ref.token)
  }

}