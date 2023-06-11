package edu.illinois.osl.akka.gc.protocols.monotone

import akka.actor.typed.{Signal, Terminated}
import edu.illinois.osl.akka.gc.interfaces._
import edu.illinois.osl.akka.gc.protocols.{Protocol, monotone}
import edu.illinois.osl.akka.gc.proxies.AkkaContext

import java.util.concurrent.ConcurrentLinkedQueue

object Monotone extends Protocol {

  val EntryPool: ConcurrentLinkedQueue[Entry] = new ConcurrentLinkedQueue[Entry]()

  type GCMessage[+T] = monotone.GCMessage[T]
  type Refob[-T] = monotone.Refob[T]
  type State = monotone.State

  class SpawnInfo(
    val creator: Option[Refob[Nothing]],
    val shadow: Shadow
  )

  override def rootMessage[T](payload: T, refs: Iterable[RefobLike[Nothing]]): GCMessage[T] =
    AppMsg(payload, refs.asInstanceOf[Iterable[Refob[Nothing]]])

  override def rootSpawnInfo(): SpawnInfo = 
    new SpawnInfo(None, new Shadow())

  override def initState[T](
    context: ContextLike[GCMessage[T]],
    spawnInfo: SpawnInfo,
  ): State = {
    val self = context.self
    val selfRefob = new Refob[Nothing](self, spawnInfo.shadow)
    val state = new State(selfRefob)
    state.onCreate(selfRefob, selfRefob)
    spawnInfo.creator match {
      case Some(creator) =>
        state.onCreate(creator, selfRefob)
      case None =>
        state.markAsRoot()
    }
    state
  }

  override def getSelfRef[T](
    state: State,
    context: ContextLike[GCMessage[T]]
  ): Refob[T] =
    state.self.asInstanceOf[Refob[T]]

  override def spawnImpl[S, T](
    factory: SpawnInfo => RefLike[GCMessage[S]],
    state: State,
    ctx: ContextLike[GCMessage[T]]
  ): Refob[S] = {
    val shadow = new Shadow()
    val child = factory(new SpawnInfo(Some(state.self), shadow))
    val ref = new Refob[S](child, shadow)
      // NB: "onCreate" is only updated at the child, not the parent.
    state.onSpawn(ref)
    ref
  }

  override def onMessage[T](
    msg: GCMessage[T],
    state: State,
    ctx: ContextLike[GCMessage[T]]
  ): Option[T] =
    msg match {
      case AppMsg(payload, _) =>
        state.incReceiveCount()
        Some(payload)
      case _ =>
        None
    }

  override def onIdle[T](
    msg: GCMessage[T],
    state: State,
    ctx: ContextLike[GCMessage[T]]
  ): Protocol.TerminationDecision =
    msg match {
      case StopMsg() =>
        state.stopRequested = true
        tryTerminate(state, ctx)
      case _ =>
        sendEntry(state.finalizeEntry(false), ctx)
        Protocol.ShouldContinue
    }

  private def tryTerminate[T](
    state: State,
    ctx: ContextLike[GCMessage[T]]
  ): Protocol.TerminationDecision = {
    if (state.stopRequested && !ctx.anyChildren)
      Protocol.ShouldStop
    else
      Protocol.ShouldContinue
  }

  override def createRef[S,T](
    target: Refob[S],
    owner: Refob[Nothing],
    state: State,
    ctx: ContextLike[GCMessage[T]]
  ): Refob[S] = {
    val ref = Refob[S](target.target, target.targetShadow)
    val entry = state.onCreate(owner, target)
    if (entry != null) sendEntry(entry, ctx)
    ref
  }

  override def release[S,T](
    releasing: Iterable[Refob[S]],
    state: State,
    ctx: ContextLike[GCMessage[T]]
  ): Unit = {
    for (ref <- releasing) {
      val entry = state.onDeactivate(ref)
      if (entry != null) sendEntry(entry, ctx)
    }
  }

  override def releaseEverything[T](
    state: State,
    ctx: ContextLike[GCMessage[T]]
  ): Unit = ???

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
      case _: Terminated =>
        tryTerminate(state, ctx)
      case _ =>
        Protocol.Unhandled
    }

  private def sendEntry[T](
    entry: Entry,
    ctx: ContextLike[GCMessage[T]]
  ): Unit = {
    ctx match {
      case ctx: AkkaContext[GCMessage[T]] =>
        ActorGC(ctx.system).Queue.add(entry)
      case _ => ???
    }
  }

  override def sendMessage[T, S](
    ref: Refob[T],
    msg: T,
    refs: Iterable[Refob[Nothing]],
    state: State,
    ctx: ContextLike[GCMessage[S]]
  ): Unit = {
    val entry = state.onSend(ref)
    if (entry != null) sendEntry(entry, ctx)
    ref.target ! AppMsg(msg, refs)
  }
}