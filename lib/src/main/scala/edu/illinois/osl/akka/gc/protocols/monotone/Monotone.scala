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
    val token: Option[Token],
    val creator: Option[Name],
    val shadow: Shadow
  )

  override def rootMessage[T](payload: T, refs: Iterable[RefobLike[Nothing]]): GCMessage[T] =
    AppMsg(payload, None, refs.asInstanceOf[Iterable[Refob[Nothing]]])

  override def rootSpawnInfo(): SpawnInfo = 
    new SpawnInfo(None, None, new Shadow())

  override def initState[T](
    context: ContextLike[GCMessage[T]],
    spawnInfo: SpawnInfo,
  ): State = {
    val self = context.self
    val shadow = spawnInfo.shadow
    val state = new State(shadow)
    val selfRef = Refob[Nothing](Some(newToken(shadow, state, context)), Some(self), self)
    val creatorRef = Refob[Nothing](spawnInfo.token, spawnInfo.creator, self)
    state.selfRef = selfRef.asInstanceOf[Refob[AnyRef]]
    state.onCreate(creatorRef)
    state.onCreate(selfRef)
    activate(selfRef, state, context)
    state
  }

  private def activate[T,S](
    ref: Refob[T],
    state: State,
    ctx: ContextLike[GCMessage[S]]
  ): Unit = {
    val entry = state.onActivate(ref)
    if (entry != null) sendEntry(entry, ctx)
  }

  override def getSelfRef[T](
    state: State,
    context: ContextLike[GCMessage[T]]
  ): Refob[T] =
    state.selfRef.asInstanceOf[Refob[T]]

  private def newToken[T,S](
    targetShadow: Shadow,
    state: State,
    ctx: ContextLike[GCMessage[T]]
  ): Token = {
    val count = state.count
    val token = new Token(ctx.self, count, targetShadow)
    state.count += 1
    token
  }
  
  override def spawnImpl[S, T](
    factory: SpawnInfo => RefLike[GCMessage[S]],
    state: State,
    ctx: ContextLike[GCMessage[T]]
  ): Refob[S] = {
    val shadow = new Shadow()
    val x = newToken(shadow, state, ctx)
    val self = ctx.self
    val spawnInfo = new SpawnInfo(Some(x), Some(self), shadow)
    val child = factory(spawnInfo)
    val ref = new Refob[S](Some(x), Some(self), child)
    activate(ref, state, ctx)
    ref
  }

  override def onMessage[T](
    msg: GCMessage[T],
    state: State,
    ctx: ContextLike[GCMessage[T]]
  ): Option[T] =
    msg match {
      case AppMsg(payload, token, refs) =>
        for (ref <- refs) {
          activate(ref, state, ctx)
        }
        for (t <- token) {
          val entry = state.incReceiveCount(t)
          if (entry != null) sendEntry(entry, ctx)
        }
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
        if (!ctx.hasMessages) {
          sendEntry(state.finalizeEntry(), ctx)
        }
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
    val targetShadow = target.token.get.targetShadow
    val token = newToken(targetShadow, state, ctx)
    val ref = Refob[S](Some(token), Some(owner.target), target.target)
    val entry = state.onCreate(ref)
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
      case signal: Terminated =>
        tryTerminate(state, ctx)
      case signal =>
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
    ref.target ! AppMsg(msg, ref.token, refs)
  }
}