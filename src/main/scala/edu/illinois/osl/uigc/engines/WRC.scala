package edu.illinois.osl.uigc.engines

import akka.actor.typed.{ActorRef, Signal, Terminated}
import akka.actor.typed.scaladsl.ActorContext
import edu.illinois.osl.uigc.interfaces._

import scala.collection.mutable

object WRC extends Engine {

  type Name = ActorRef[GCMessage[Nothing]]

  val RC_INC: Long = 255

  case class Refob[-T](target: ActorRef[GCMessage[T]]) extends RefobLike[T]

  trait GCMessage[+T] extends Message
  case class AppMsg[T](payload: T, refs: Iterable[Refob[Nothing]], isSelfMsg: Boolean) extends GCMessage[T]
  case object IncMsg extends GCMessage[Nothing] {
    override def refs: Iterable[RefobLike[Nothing]] = Nil
  }
  case class DecMsg(weight: Long) extends GCMessage[Nothing] {
    override def refs: Iterable[RefobLike[Nothing]] = Nil
  }

  class Pair (
    var numRefs: Long = 0,
    var weight: Long = 0
  )

  class State(val self: Refob[Nothing], val kind: SpawnInfo) {
    var rc: Long = RC_INC
    var pendingSelfMessages: Long = 0
    val actorMap: mutable.HashMap[Name, Pair] = mutable.HashMap()
  }

  sealed trait SpawnInfo extends Serializable
  case object IsRoot extends SpawnInfo
  case object NonRoot extends SpawnInfo

  /**
   * Transform a message from a non-GC actor so that it can be understood
   * by a GC actor. Necessarily, the recipient is a root actor.
   */
  def rootMessage[T](payload: T, refs: Iterable[RefobLike[Nothing]]): GCMessage[T] = 
    AppMsg(payload, refs.asInstanceOf[Iterable[Refob[Nothing]]], isSelfMsg = false)

  /** 
   * Produces SpawnInfo indicating to the actor that it is a root actor.
   */
  def rootSpawnInfo(): SpawnInfo = IsRoot

  def initState[T](
    context: ActorContext[GCMessage[T]],
    spawnInfo: SpawnInfo,
  ): State = {
    val state = new State(Refob(context.self), spawnInfo)
    val pair = new Pair(numRefs = 1, weight = RC_INC)
    state.actorMap(context.self) = pair
    state
  }

  def getSelfRef[T](
    state: State,
    context: ActorContext[GCMessage[T]]
  ): Refob[T] =
    state.self.asInstanceOf[Refob[T]]

  def spawnImpl[S, T](
    factory: SpawnInfo => ActorRef[GCMessage[S]],
    state: State,
    ctx: ActorContext[GCMessage[T]]
  ): Refob[S] = {
    val actorRef = factory(NonRoot)
    ctx.watch(actorRef)
    val pair = new Pair(numRefs = 1, weight = RC_INC)
    state.actorMap(actorRef) = pair
    val refob = Refob(actorRef)
    refob
  }

  def onMessage[T](
    msg: GCMessage[T],
    state: State,
    ctx: ActorContext[GCMessage[T]],
  ): Option[T] = msg match {
    case AppMsg(payload, refs, isSelfMsg) => 
      if (isSelfMsg) {
        state.pendingSelfMessages -= 1
      }
      for (ref <- refs) {
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
    ctx: ActorContext[GCMessage[T]]
  ): Engine.TerminationDecision =
    if (state.kind == NonRoot && state.rc == 0 && state.pendingSelfMessages == 0 && ctx.children.isEmpty)
      Engine.ShouldStop
    else 
      Engine.ShouldContinue

  def onIdle[T](
    msg: GCMessage[T],
    state: State,
    ctx: ActorContext[GCMessage[T]],
  ): Engine.TerminationDecision =
    tryTerminate(state, ctx)

  def preSignal[T](
    signal: Signal, 
    state: State,
    ctx: ActorContext[GCMessage[T]]
  ): Unit = ()

  def postSignal[T](
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

  def createRef[S,T](
    target: Refob[S], 
    owner: Refob[Nothing],
    state: State,
    ctx: ActorContext[GCMessage[T]]
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
    ctx: ActorContext[GCMessage[T]]
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
    ctx: ActorContext[GCMessage[T]]
  ): Unit = ???

  override def sendMessage[T, S](
    ref: Refob[T],
    msg: T,
    refs: Iterable[Refob[Nothing]],
    state: State,
    ctx: ActorContext[GCMessage[S]]
  ): Unit = {
    val isSelfMsg = ref.target == state.self.target
    if (isSelfMsg) {
      state.pendingSelfMessages += 1
    }
    ref.target ! AppMsg(msg, refs, isSelfMsg)
  }

}
