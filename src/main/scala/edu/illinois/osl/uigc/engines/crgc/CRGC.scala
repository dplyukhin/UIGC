package edu.illinois.osl.uigc.engines.crgc

import akka.actor.typed.scaladsl.ActorContext
import akka.actor.typed.{ActorRef, Signal}
import akka.actor.{Address, ExtendedActorSystem}
import akka.remote.artery.{InboundEnvelope, ObjectPool, OutboundEnvelope, ReusableOutboundEnvelope}
import akka.stream.stage.GraphStageLogic
import akka.stream.{FlowShape, Inlet, Outlet}
import com.typesafe.config.Config
import edu.illinois.osl.uigc.engines.crgc.jfr.EntrySendEvent
import edu.illinois.osl.uigc.engines.{Engine, crgc}
import edu.illinois.osl.uigc.interfaces

import java.util.concurrent.ConcurrentLinkedQueue

object CRGC {

  val EntryPool: ConcurrentLinkedQueue[Entry] = new ConcurrentLinkedQueue[Entry]()

  trait CollectionStyle

  class SpawnInfo(
      var creator: Option[Refob[Nothing]]
  ) extends interfaces.SpawnInfo with Serializable

  case object Wave extends CollectionStyle

  private case object OnBlock extends CollectionStyle

  private case object OnIdle extends CollectionStyle

}

class CRGC(system: ExtendedActorSystem) extends Engine {
  import CRGC._

  override type GCMessageImpl[+T] = crgc.GCMessage[T]
  override type RefobImpl[-T] = crgc.Refob[T]
  override type SpawnInfoImpl = SpawnInfo
  override type StateImpl = crgc.State

  val config: Config = system.settings.config
  val collectionStyle: CollectionStyle =
    config.getString("uigc.crgc.collection-style") match {
      case "wave"     => Wave
      case "on-block" => OnBlock
      case "on-idle"  => OnIdle
    }
  val crgcContext = new Context(config)

  // This could be split into multiple queues if contention becomes high
  val Queue: ConcurrentLinkedQueue[Entry] = new ConcurrentLinkedQueue[Entry]()

  val bookkeeper: akka.actor.ActorRef =
    system.systemActorOf(
      akka.actor.Props[LocalGC]().withDispatcher("my-pinned-dispatcher"),
      "Bookkeeper"
    )

  override def rootMessageImpl[T](payload: T, refs: Iterable[Refob[Nothing]]): GCMessage[T] =
    AppMsg(payload, refs)

  override def rootSpawnInfoImpl(): SpawnInfo =
    new SpawnInfo(None)

  override def toRefobImpl[T](ref: ActorRef[GCMessage[T]]): Refob[T] =
    new Refob[T](ref, targetShadow = null)

  override def initStateImpl[T](
      context: ActorContext[GCMessage[T]],
      spawnInfo: SpawnInfo
  ): State = {
    val self = context.self
    val selfRefob = new Refob[Nothing](self, targetShadow = null)
    val state = new State(selfRefob, crgcContext)
    state.recordNewRefob(selfRefob, selfRefob)
    spawnInfo.creator match {
      case Some(creator) =>
        state.recordNewRefob(creator, selfRefob)
      case None =>
        state.markAsRoot()
    }

    def onBlock(): Unit =
      sendEntry(state, isBusy=false)

    if (collectionStyle == OnBlock)
      context.queue.onFinishedProcessingHook = onBlock
    if ((collectionStyle == Wave && state.isRoot) || collectionStyle == OnIdle)
      sendEntry(state, isBusy=false)
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
    val child = factory(new SpawnInfo(Some(state.self)))
    val ref = new Refob[S](child, null)
    // NB: "onCreate" is only updated at the child, not the parent.
    if (!state.canRecordNewActor)
      sendEntry(state, isBusy=true)
    state.recordNewActor(ref)
    ref
  }

  override def onMessageImpl[T](
      msg: GCMessage[T],
      state: State,
      ctx: ActorContext[GCMessage[T]]
  ): Option[T] =
    msg match {
      case AppMsg(payload, _) =>
        if (!state.canRecordMessageReceived)
          sendEntry(state, isBusy=true)
        state.recordMessageReceived()
        Some(payload)
      case _ =>
        None
    }

  override def onIdleImpl[T](
      msg: GCMessage[T],
      state: State,
      ctx: ActorContext[GCMessage[T]]
  ): Engine.TerminationDecision =
    msg match {
      case StopMsg =>
        Engine.ShouldStop
      case WaveMsg =>
        sendEntry(state, isBusy=false)
        for (child <- ctx.children)
          child.unsafeUpcast[GCMessage[Any]].tell(WaveMsg)
        Engine.ShouldContinue
      case _ =>
        if (collectionStyle == OnIdle)
          sendEntry(state, isBusy=false)
        Engine.ShouldContinue
    }

  override def createRefImpl[S, T](
      target: Refob[S],
      owner: Refob[Nothing],
      state: State,
      ctx: ActorContext[GCMessage[T]]
  ): Refob[S] = {
    val ref = new Refob[S](target.target, target.targetShadow)
    if (!state.canRecordNewRefob)
      sendEntry(state, isBusy=true)
    state.recordNewRefob(owner, target)
    ref
  }

  override def releaseImpl[S, T](
      releasing: Iterable[Refob[S]],
      state: State,
      ctx: ActorContext[GCMessage[T]]
  ): Unit =
    for (ref <- releasing) {
      if (!state.canRecordUpdatedRefob(ref))
        sendEntry(state, isBusy=true)
      ref.deactivate()
      state.recordUpdatedRefob(ref)
    }

  private def sendEntry(
      state: State,
      isBusy: Boolean
  ): Unit = {
    val metrics = new EntrySendEvent()
    metrics.begin()
    var entry = CRGC.EntryPool.poll()
    if (entry == null) {
      entry = new Entry(crgcContext)
      metrics.allocatedMemory = true
    }
    state.flushToEntry(isBusy, entry)
    Queue.add(entry)
    metrics.commit()
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
    Engine.Unhandled

  override def sendMessageImpl[T, S](
      ref: Refob[T],
      msg: T,
      refs: Iterable[Refob[Nothing]],
      state: State,
      ctx: ActorContext[GCMessage[S]]
  ): Unit = {
    if (!ref.canIncSendCount || !state.canRecordUpdatedRefob(ref))
      sendEntry(state, isBusy=true)
    ref.incSendCount()
    state.recordUpdatedRefob(ref)

    ref.target ! AppMsg(msg, refs)
  }

  override def spawnEgress(
      in: Inlet[OutboundEnvelope],
      out: Outlet[OutboundEnvelope],
      shape: FlowShape[OutboundEnvelope, OutboundEnvelope],
      system: ExtendedActorSystem,
      adjacent: Address,
      outboundObjectPool: ObjectPool[ReusableOutboundEnvelope]
  ): GraphStageLogic =
    new Egress(in, out, shape, system, adjacent, outboundObjectPool)

  override def spawnIngress(
      in: Inlet[InboundEnvelope],
      out: Outlet[InboundEnvelope],
      shape: FlowShape[InboundEnvelope, InboundEnvelope],
      system: ExtendedActorSystem,
      adjacent: Address
  ): GraphStageLogic =
    new MultiIngress(in, out, shape, system, adjacent)

}
