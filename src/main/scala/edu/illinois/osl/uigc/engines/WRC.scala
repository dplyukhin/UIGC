package edu.illinois.osl.uigc.engines

import akka.actor.ExtendedActorSystem
import akka.actor.typed.scaladsl.ActorContext
import akka.actor.typed.{ActorRef, Signal, Terminated}
import edu.illinois.osl.uigc.interfaces

import scala.collection.mutable

object WRC {
  type Name = ActorRef[GCMessage[Nothing]]

  private val RC_INC: Long = 255

  sealed trait SpawnInfo extends interfaces.SpawnInfo

  trait GCMessage[+T] extends interfaces.GCMessage[T]

  case class Refob[-T](target: ActorRef[GCMessage[T]]) extends interfaces.Refob[T]

  case class AppMsg[T](payload: T, refs: Iterable[Refob[Nothing]], isSelfMsg: Boolean)
      extends GCMessage[T]

  class Pair(
      var numRefs: Long = 0,
      var weight: Long = 0
  )

  class State(val self: Refob[Nothing], val kind: SpawnInfo) extends interfaces.State {
    val actorMap: mutable.HashMap[Name, Pair] = mutable.HashMap()
    var rc: Long = RC_INC
    var pendingSelfMessages: Long = 0
  }

  private case class DecMsg(weight: Long) extends GCMessage[Nothing] {
    override def refs: Iterable[Refob[Nothing]] = Nil
  }

  private case object IncMsg extends GCMessage[Nothing] {
    override def refs: Iterable[Refob[Nothing]] = Nil
  }

  private case object IsRoot extends SpawnInfo

  private case object NonRoot extends SpawnInfo
}

class WRC(system: ExtendedActorSystem) extends Engine {
  import WRC._

  override type GCMessageImpl[+T] = WRC.GCMessage[T]
  override type RefobImpl[-T] = WRC.Refob[T]
  override type SpawnInfoImpl = WRC.SpawnInfo
  override type StateImpl = WRC.State

  /** Transform a message from a non-GC actor so that it can be understood by a GC actor.
    * Necessarily, the recipient is a root actor.
    */
  override def rootMessageImpl[T](payload: T, refs: Iterable[Refob[Nothing]]): GCMessage[T] =
    AppMsg(payload, refs, isSelfMsg = false)

  /** Produces SpawnInfo indicating to the actor that it is a root actor.
    */
  override def rootSpawnInfoImpl(): SpawnInfo = IsRoot

  override def initStateImpl[T](
      context: ActorContext[GCMessage[T]],
      spawnInfo: SpawnInfo
  ): State = {
    val state = new State(Refob(context.self), spawnInfo)
    val pair = new Pair(numRefs = 1, weight = RC_INC)
    state.actorMap(context.self) = pair
    state
  }

  override def getSelfRefImpl[T](
      state: State,
      context: ActorContext[GCMessage[T]]
  ): Refob[T] =
    state.self.asInstanceOf[Refob[T]]

  override def spawnImpl[S, T](
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

  override def onMessageImpl[T](
      msg: GCMessage[T],
      state: State,
      ctx: ActorContext[GCMessage[T]]
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
      state.rc = state.rc + RC_INC
      None
  }

  override def onIdleImpl[T](
      msg: GCMessage[T],
      state: State,
      ctx: ActorContext[GCMessage[T]]
  ): Engine.TerminationDecision =
    tryTerminate(state, ctx)

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

  def tryTerminate[T](
      state: State,
      ctx: ActorContext[GCMessage[T]]
  ): Engine.TerminationDecision =
    if (
      state.kind == NonRoot && state.rc == 0 && state.pendingSelfMessages == 0 && ctx.children.isEmpty
    )
      Engine.ShouldStop
    else
      Engine.ShouldContinue

  override def createRefImpl[S, T](
      target: Refob[S],
      owner: Refob[Nothing],
      state: State,
      ctx: ActorContext[GCMessage[T]]
  ): Refob[S] =
    if (target.target == ctx.self) {
      state.rc += 1
      Refob(target.target)
    } else {
      val pair = state.actorMap(target.target)
      if (pair.weight <= 1) {
        pair.weight = pair.weight + RC_INC - 1
        target.target ! IncMsg
      } else {
        pair.weight -= 1
      }
      Refob(target.target)
    }

  override def releaseImpl[S, T](
      releasing: Iterable[Refob[S]],
      state: State,
      ctx: ActorContext[GCMessage[T]]
  ): Unit =
    for (ref <- releasing)
      if (ref.target == ctx.self) {
        state.rc -= 1
      } else {
        val pair = state.actorMap(ref.target)
        if (pair.numRefs <= 1) {
          ref.target ! DecMsg(pair.weight)
          state.actorMap.remove(ref.target)
        } else {
          pair.numRefs -= 1
        }
      }

  override def sendMessageImpl[T, S](
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
