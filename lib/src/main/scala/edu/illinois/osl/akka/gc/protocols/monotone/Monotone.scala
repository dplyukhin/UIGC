package edu.illinois.osl.akka.gc.protocols.monotone

import akka.actor.typed.{PostStop, Terminated, Signal}
import scala.collection.mutable
import edu.illinois.osl.akka.gc.interfaces._
import edu.illinois.osl.akka.gc.protocols.{Protocol, monotone}

object Monotone extends Protocol {

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
    state.refs(selfRef.token.get) = RefobInfo.activeRefob
    state.recvCount(selfRef.token.get) = 0
    state.created.append(creatorRef, selfRef)
    initializeRefob(selfRef, state, context)
    state
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
  
  def incReceivedCount(optoken: Option[Token], state: State): Unit = {
    if (optoken.isDefined) {
      val token = optoken.get
      val count = state.recvCount.getOrElse(token, 0)
      state.recvCount(token) = count + 1
    }
  }

  def incSentCount(optoken: Option[Token], state: State): Unit = {
    if (optoken.isDefined) {
      val token = optoken.get
      val count = state.refs.getOrElse(token, RefobInfo.activeRefob)
      state.refs(token) = RefobInfo.incSendCount(count)
    }
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
    initializeRefob(ref, state, ctx)
    state.refs(x) = RefobInfo.activeRefob
    ref
  }

  override def onMessage[T](
    msg: GCMessage[T],
    state: State,
    ctx: ContextLike[GCMessage[T]]
  ): Option[T] =
    msg match {
      case AppMsg(payload, token, refs) =>
        refs.foreach(ref => {
          initializeRefob(ref, state, ctx)
          state.refs(ref.token.get) = RefobInfo.activeRefob
        })
        // increment recv count for this token
        incReceivedCount(token, state)
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
    state.created.append(ref)
    ref
  }

  override def release[S,T](
    releasing: Iterable[Refob[S]],
    state: State,
    ctx: ContextLike[GCMessage[T]]
  ): Unit = {
    for (ref <- releasing) {
      val info = state.refs(ref.token.get)
      state.refs(ref.token.get) = RefobInfo.deactivate(info)
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
  ): Unit =
    refob.initialize(state)
}