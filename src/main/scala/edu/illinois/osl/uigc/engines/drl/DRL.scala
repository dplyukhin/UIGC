package edu.illinois.osl.uigc.engines.drl

import akka.actor.typed.scaladsl.ActorContext
import akka.actor.typed.{ActorRef, Signal, Terminated}
import edu.illinois.osl.uigc.engines.{Engine, drl}
import edu.illinois.osl.uigc.interfaces

import scala.collection.mutable

object DRL {
  class SpawnInfo(
      val token: Option[Token],
      val creator: Option[Name]
  ) extends interfaces.SpawnInfo
}

class DRL extends Engine {
  import DRL._

  override type GCMessageImpl[+T] = drl.GCMessage[T]
  override type RefobImpl[-T] = drl.Refob[T]
  override type StateImpl = drl.State
  override type SpawnInfoImpl = SpawnInfo

  override def rootMessageImpl[T](payload: T, refs: Iterable[Refob[Nothing]]): GCMessage[T] =
    AppMsg(payload, None, refs)

  override def rootSpawnInfoImpl(): SpawnInfo =
    new SpawnInfo(None, None)

  override def toRefobImpl[T](ref: ActorRef[GCMessage[T]]): Refob[T] =
    Refob(None, None, ref)

  override def initStateImpl[T](
      context: ActorContext[GCMessage[T]],
      spawnInfo: SpawnInfo
  ): State = {
    val state = new State(context.self, spawnInfo)
    state
  }

  def getSelfRefImpl[T](
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

  override def onMessageImpl[T](
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

  override def onIdleImpl[T](
      msg: GCMessage[T],
      state: State,
      ctx: ActorContext[GCMessage[T]]
  ): Engine.TerminationDecision =
    msg match {
      case Kill =>
        Engine.ShouldStop
      case _ =>
        tryTerminate(state, ctx)
    }

  /** Attempts to terminate this actor, sends a [[SelfCheck]] message to try again if it can't.
    */
  def tryTerminate[T](
      state: State,
      ctx: ActorContext[GCMessage[T]]
  ): Engine.TerminationDecision =
    if (ctx.children.nonEmpty || state.anyInverseAcquaintances || state.anyPendingSelfMessages)
      Engine.ShouldContinue
    else
      Engine.ShouldStop

  override def createRefImpl[S, T](
      target: Refob[S],
      owner: Refob[Nothing],
      state: State,
      ctx: ActorContext[GCMessage[T]]
  ): Refob[S] = {
    val ref = state.newRef(owner, target)
    state.handleCreatedRef(target, ref)
    ref
  }

  override def releaseImpl[S, T](
      releasing: Iterable[Refob[S]],
      state: State,
      ctx: ActorContext[GCMessage[T]]
  ): Unit = {

    val targets: mutable.HashMap[Name, (Seq[Ref], Seq[Ref])] = state.release(releasing)

    // send the release message for each target actor
    for ((target, (targetedRefs, createdRefs)) <- targets)
      target ! ReleaseMsg(targetedRefs, createdRefs)
  }

  override def preSignalImpl[T](
      signal: Signal,
      state: State,
      ctx: ActorContext[GCMessage[T]]
  ): Unit = ()

  override def postSignalImpl[T](
      signal: Signal,
      state: State,
      ctx: ActorContext[GCMessage[T]]
  ): Engine.TerminationDecision =
    signal match {
      case signal: Terminated =>
        tryTerminate(state, ctx)
      case signal =>
        Engine.Unhandled
    }

  override def sendMessageImpl[T, S](
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
