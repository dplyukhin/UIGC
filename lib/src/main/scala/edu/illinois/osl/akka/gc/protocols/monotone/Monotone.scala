package edu.illinois.osl.akka.gc.protocols.monotone

import akka.actor.typed.Signal
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
    val self = context.self
    val state = new State()
    val selfRef = Refob[Nothing](Some(newToken(state, context)), Some(self), self)
    val creatorRef = Refob[Nothing](spawnInfo.token, spawnInfo.creator, self)
    state.selfRef = selfRef
    state.onCreate(creatorRef)
    state.onCreate(selfRef)
    activate(selfRef, state, context)
    state
  }

  def activate[T,S](
    ref: Refob[T],
    state: State,
    ctx: ContextLike[GCMessage[S]]
  ): Unit = {
    ref.initialize(state, ctx)
    val entry = state.onActivate(ref)
    if (entry != null) sendEntry(entry, ctx)
  }

  def getSelfRef[T](
    state: State,
    context: ContextLike[GCMessage[T]]
  ): Refob[T] =
    state.selfRef.asInstanceOf[Refob[T]]

  def newToken[T](
    state: State, 
    ctx: ContextLike[GCMessage[T]]
  ): Token = {
    val count = state.count
    val token = Token(ctx.self, count)
    state.count += 1
    token
  }
  
  override def spawnImpl[S, T](
    factory: SpawnInfo => RefLike[GCMessage[S]],
    state: State,
    ctx: ContextLike[GCMessage[T]]
  ): Refob[S] = {
    val x = newToken(state, ctx)
    val self = ctx.self
    val child = factory(new SpawnInfo(Some(x), Some(self)))
    val ref = new Refob[S](Some(x), Some(self), child)
    activate(ref, state, ctx)
    ref
  }

  def onSend(
    ref: Refob[_],
    state: State,
    ctx: ContextLike[GCMessage[_]]
  ): Unit = {
    val entry = state.onSend(ref)
    if (entry != null) sendEntry(entry, ctx)
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
    }

  override def onIdle[T](
    msg: GCMessage[T],
    state: State,
    ctx: ContextLike[GCMessage[T]]
  ): Protocol.TerminationDecision =
    Protocol.ShouldContinue

  override def createRef[S,T](
    target: Refob[S], 
    owner: Refob[Nothing],
    state: State,
    ctx: ContextLike[GCMessage[T]]
  ): Refob[S] = {
    val token = newToken(state, ctx)
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

  def releaseEverything[T](
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
    Protocol.Unhandled

  def initializeRefob[T](
    refob: Refob[Nothing],
    state: State,
    ctx: ContextLike[GCMessage[T]]
  ): Unit = {
    ??? // Refobs carry message counts now, so it's unclear what this means
  }

  def sendEntry[T](
    entry: Entry,
    ctx: ContextLike[GCMessage[T]]
  ): Unit = {
    ctx match {
      case ctx: AkkaContext[GCMessage[T]] =>
        Bookkeeper(ctx.system)
      case _ => ???
    }
  }
}