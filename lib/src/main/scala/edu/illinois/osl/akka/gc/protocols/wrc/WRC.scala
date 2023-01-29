package edu.illinois.osl.akka.gc.protocols.wrc

import edu.illinois.osl.akka.gc.protocols.Protocol
import edu.illinois.osl.akka.gc.interfaces._
import akka.actor.typed.Signal
import akka.actor.ActorPath
import scala.annotation.unchecked.uncheckedVariance
import scala.collection.mutable
import akka.actor.typed.Terminated

object WRC extends Protocol {

  type Name = RefLike[GCMessage[Nothing]]

  val RC_INC: Long = 255

  case class Refob[-T](target: RefLike[GCMessage[T]]) extends RefobLike[T] {
    private var state: State = _

    def initialize[S](_state: State): Unit = {
      state = _state
    }

    override def !(msg: T, refs: Iterable[RefobLike[Nothing]]): Unit = {
      val isSelfMsg = (target == state.self.target)
      if (isSelfMsg) {
        state.pendingSelfMessages += 1
      }
      target ! AppMsg(msg, refs.asInstanceOf[Iterable[Refob[Nothing]]], isSelfMsg)
    }

    override def pretty: String = target.pretty
  }

  trait GCMessage[+T] extends Message with Pretty
  case class AppMsg[T](payload: T, refs: Iterable[Refob[Nothing]], isSelfMsg: Boolean) extends GCMessage[T] {
    override def pretty: String = s"AppMsg($payload, ${refs.toList.pretty})"
  }
  case object IncMsg extends GCMessage[Nothing] {
    override def refs: Iterable[RefobLike[Nothing]] = Nil
    override def pretty: String = "IncMsg"
  }
  case class DecMsg(weight: Long) extends GCMessage[Nothing] {
    override def pretty: String = s"DecMsg($weight)"
    override def refs: Iterable[RefobLike[Nothing]] = Nil
  }

  class Pair (
    var numRefs: Long = 0,
    var weight: Long = 0
  )

  class State(val self: Refob[Nothing], val kind: SpawnInfo) extends Pretty {
    var rc: Long = RC_INC
    var pendingSelfMessages: Long = 0
    val actorMap: mutable.HashMap[Name, Pair] = mutable.HashMap()
    override def pretty: String =
      s"""STATE:
      < kind: $kind
        rc:   $rc
        actorMap: ${actorMap.pretty}
      >
      """
  }

  sealed trait SpawnInfo
  case object IsRoot extends SpawnInfo
  case object NonRoot extends SpawnInfo

  /**
   * Transform a message from a non-GC actor so that it can be understood
   * by a GC actor. Necessarily, the recipient is a root actor.
   */
  def rootMessage[T](payload: T, refs: Iterable[RefobLike[Nothing]]): GCMessage[T] = 
    AppMsg(payload, refs.asInstanceOf[Iterable[Refob[Nothing]]], false)

  /** 
   * Produces SpawnInfo indicating to the actor that it is a root actor.
   */
  def rootSpawnInfo(): SpawnInfo = IsRoot

  def initState[T](
    context: ContextLike[GCMessage[T]],
    spawnInfo: SpawnInfo,
  ): State = {
    val state = new State(Refob(context.self), spawnInfo)
    val pair = new Pair(numRefs = 1, weight = RC_INC)
    state.actorMap(context.self) = pair
    initializeRefob(state.self, state, context)
    state
  }

  def getSelfRef[T](
    state: State,
    context: ContextLike[GCMessage[T]]
  ): Refob[T] =
    state.self.asInstanceOf[Refob[T]]

  def spawnImpl[S, T](
    factory: SpawnInfo => RefLike[GCMessage[S]],
    state: State,
    ctx: ContextLike[GCMessage[T]]
  ): Refob[S] = {
    val actorRef = factory(NonRoot)
    ctx.watch(actorRef)
    val pair = new Pair(numRefs = 1, weight = RC_INC)
    state.actorMap(actorRef) = pair
    val refob = Refob(actorRef)
    initializeRefob(refob, state, ctx)
    refob
  }

  def onMessage[T](
    msg: GCMessage[T],
    state: State,
    ctx: ContextLike[GCMessage[T]],
  ): Option[T] = msg match {
    case AppMsg(payload, refs, isSelfMsg) => 
      if (isSelfMsg) {
        state.pendingSelfMessages -= 1
      }
      for (ref <- refs) {
        initializeRefob(ref, state, ctx)
        val pair = state.actorMap.getOrElseUpdate(ref.target, new Pair())
        pair.numRefs = pair.numRefs + 1
        pair.weight = pair.weight + 1
      }
      Some(payload)
    case DecMsg(weight) => 
      state.rc = state.rc - weight
      None
    case IncMsg => 
      state.rc =  state.rc + RC_INC
      None
  }

  def tryTerminate[T](
    state: State,
    ctx: ContextLike[GCMessage[T]]
  ): Protocol.TerminationDecision =
    if (state.kind == NonRoot && state.rc == 0 && state.pendingSelfMessages == 0 && !ctx.anyChildren) 
      Protocol.ShouldStop 
    else 
      Protocol.ShouldContinue

  def onIdle[T](
    msg: GCMessage[T],
    state: State,
    ctx: ContextLike[GCMessage[T]],
  ): Protocol.TerminationDecision =
    tryTerminate(state, ctx)

  def preSignal[T](
    signal: Signal, 
    state: State,
    ctx: ContextLike[GCMessage[T]]
  ): Unit = ()

  def postSignal[T](
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

  def createRef[S,T](
    target: Refob[S], 
    owner: Refob[Nothing],
    state: State,
    ctx: ContextLike[GCMessage[T]]
  ): Refob[S] = {
    if (target.target == ctx.self) {
      state.rc += 1
      Refob(target.target)
    }
    else {
      val pair = state.actorMap(target.target)
      if (pair.weight <= 1) {
          pair.weight = pair.weight + RC_INC - 1
          target.target ! IncMsg
      }
      else {
          pair.weight -= 1
      }
      Refob(target.target)
    }
  }

  def release[S,T](
    releasing: Iterable[Refob[S]],
    state: State,
    ctx: ContextLike[GCMessage[T]]
  ): Unit = {
    for (ref <- releasing) {
      if (ref.target == ctx.self) {
        state.rc -= 1
      }
      else {
        val pair = state.actorMap(ref.target)
        if (pair.numRefs <= 1) {
            ref.target ! DecMsg(pair.weight)
            state.actorMap.remove(ref.target)
        }
        else {
            pair.numRefs -= 1
        }
      }
    }
  }

  def releaseEverything[T](
    state: State,
    ctx: ContextLike[GCMessage[T]]
  ): Unit = ???

  def initializeRefob[T](
    refob: Refob[Nothing],
    state: State,
    ctx: ContextLike[GCMessage[T]]
  ): Unit = {
    refob.initialize(state)
  }
}
