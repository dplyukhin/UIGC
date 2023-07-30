package edu.illinois.osl.akka.gc.protocols.monotone

import scala.jdk.CollectionConverters._
import akka.{actor => classic}
import akka.actor.{ActorSelectionMessage, Address, ExtendedActorSystem}
import akka.actor.typed.{ActorRef, PostStop, Signal, Terminated}
import com.typesafe.config.ConfigFactory
import edu.illinois.osl.akka.gc.interfaces._
import edu.illinois.osl.akka.gc.protocols.{Protocol, monotone}
import akka.actor.typed.scaladsl.ActorContext
import akka.remote.artery.{InboundEnvelope, ObjectPool, OutboundEnvelope, ReusableOutboundEnvelope}
import akka.stream.stage.GraphStageLogic
import akka.stream.{FlowShape, Inlet, Outlet}
import akka.util.OptionVal

import java.io.{IOException, ObjectInputStream, ObjectOutputStream}
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger

object Monotone extends Protocol {
  trait CollectionStyle
  case object Wave extends CollectionStyle
  case object OnBlock extends CollectionStyle
  case object OnIdle extends CollectionStyle
  val config = ConfigFactory.load("application.conf")
  val collectionStyle: CollectionStyle =
    System.getProperty("gc.crgc.collection-style") match {
      case "wave" => Wave
      case "on-block" => OnBlock
      case "on-idle" => OnIdle
    }

  val EntryPool: ConcurrentLinkedQueue[Entry] = new ConcurrentLinkedQueue[Entry]()

  type GCMessage[+T] = monotone.GCMessage[T]
  type Refob[-T] = monotone.Refob[T]
  type State = monotone.State

  class SpawnInfo(
    var creator: Option[Refob[Nothing]],
  ) extends Serializable

  override def rootMessage[T](payload: T, refs: Iterable[RefobLike[Nothing]]): GCMessage[T] =
    AppMsg(payload, refs.asInstanceOf[Iterable[Refob[Nothing]]])

  override def rootSpawnInfo(): SpawnInfo = 
    new SpawnInfo(None)

  override def initState[T](
    context: ActorContext[GCMessage[T]],
    spawnInfo: SpawnInfo,
  ): State = {
    val self = context.self
    val selfRefob = new Refob[Nothing](self, targetShadow = null)
    val state = new State(selfRefob)
    state.onCreate(selfRefob, selfRefob)
    spawnInfo.creator match {
      case Some(creator) =>
        state.onCreate(creator, selfRefob)
      case None =>
        state.markAsRoot()
    }

    def onBlock(): Unit =
      sendEntry(state.finalizeEntry(false), context)

    if (collectionStyle == OnBlock)
      context.queue.onFinishedProcessingHook = onBlock
    if ((collectionStyle == Wave && state.isRoot) || collectionStyle == OnIdle)
      sendEntry(state.finalizeEntry(false), context)
    state
  }

  override def getSelfRef[T](
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
    state.onSpawn(ref)
    ref
  }

  override def onMessage[T](
    msg: GCMessage[T],
    state: State,
    ctx: ActorContext[GCMessage[T]]
  ): Option[T] =
    msg match {
      case AppMsg(payload, _) =>
        val entry = state.incReceiveCount()
        if (entry != null) sendEntry(entry, ctx)
        Some(payload)
      case _ =>
        None
    }

  override def onIdle[T](
    msg: GCMessage[T],
    state: State,
    ctx: ActorContext[GCMessage[T]]
  ): Protocol.TerminationDecision =
    msg match {
      case StopMsg =>
        state.stopRequested = true
        Protocol.ShouldStop
      case WaveMsg =>
        sendEntry(state.finalizeEntry(false), ctx)
        for (child <- ctx.children) {
          child.unsafeUpcast[GCMessage[Any]].tell(WaveMsg)
        }
        Protocol.ShouldContinue
      case _ =>
        if (collectionStyle == OnIdle)
          sendEntry(state.finalizeEntry(false), ctx)
        Protocol.ShouldContinue
    }

  override def createRef[S,T](
    target: Refob[S],
    owner: Refob[Nothing],
    state: State,
    ctx: ActorContext[GCMessage[T]]
  ): Refob[S] = {
    val ref = new Refob[S](target.target, target.targetShadow)
    val entry = state.onCreate(owner, target)
    if (entry != null) sendEntry(entry, ctx)
    ref
  }

  override def release[S,T](
    releasing: Iterable[Refob[S]],
    state: State,
    ctx: ActorContext[GCMessage[T]]
  ): Unit = {
    for (ref <- releasing) {
      val entry = state.onDeactivate(ref)
      if (entry != null) sendEntry(entry, ctx)
    }
  }

  override def releaseEverything[T](
    state: State,
    ctx: ActorContext[GCMessage[T]]
  ): Unit = ???

  override def onWatch[T](ref: Refob[Nothing], state: State, ctx: ActorContext[GCMessage[T]]): Unit = {
    val entry = state.onMonitor(SomeRefob(ref))
    if (entry != null) sendEntry(entry, ctx)
  }

  override def onUnwatch[T](ref: Refob[Nothing], state: State, ctx: ActorContext[GCMessage[T]]): Unit = {
    val entry = state.onUnmonitor(SomeRefob(ref))
    if (entry != null) sendEntry(entry, ctx)
  }

  override def onThrow[T](e: Throwable, state: State, ctx: ActorContext[GCMessage[T]]): Unit = {
    val entry = state.onThrow()
    if (entry != null) sendEntry(entry, ctx)
  }

  override def preSignal[T](
    signal: Signal, 
    state: State,
    ctx: ActorContext[GCMessage[T]]
  ): Unit = ()

  override def postSignal[T](
    signal: Signal, 
    state: State,
    ctx: ActorContext[GCMessage[T]]
  ): Protocol.TerminationDecision = signal match {
    case Terminated(ref) =>
      val entry = state.onUnmonitor(SomeActorRef(ref))
      if (entry != null) sendEntry(entry, ctx)
      sendEntry(state.finalizeEntry(false), ctx)
      Protocol.Unhandled
    case PostStop =>
      if (!state.stopRequested && !state.isHalted) {
        // This actor was not asked to stop by the garbage collector, and
        // it did not throw an exception either.
        // Either the user asked this actor to stop or an ancestor actor
        // stopped and caused this one to stop.
        state.isHalted = true
        sendEntry(state.finalizeEntry(false), ctx)
      }
      Protocol.Unhandled
    case _ =>
      Protocol.Unhandled
  }

  private def sendEntry[T](
    entry: Entry,
    ctx: ActorContext[GCMessage[T]]
  ): Unit = {
    ActorGC(ctx.system).Queue.add(entry)
  }

  override def sendMessage[T, S](
    ref: Refob[T],
    msg: T,
    refs: Iterable[Refob[Nothing]],
    state: State,
    ctx: ActorContext[GCMessage[S]]
  ): Unit = {
    val entry = state.onSend(ref)
    if (entry != null) sendEntry(entry, ctx)
    ref.target ! AppMsg(msg, refs)
  }


  override def spawnEgress(in: Inlet[OutboundEnvelope], out: Outlet[OutboundEnvelope], shape: FlowShape[OutboundEnvelope, OutboundEnvelope], system: ExtendedActorSystem, adjacent: Address, outboundObjectPool: ObjectPool[ReusableOutboundEnvelope]): GraphStageLogic =
    new Egress(in, out, shape, system, adjacent, outboundObjectPool)

  override def spawnIngress(in: Inlet[InboundEnvelope], out: Outlet[InboundEnvelope], shape: FlowShape[InboundEnvelope, InboundEnvelope], system: ExtendedActorSystem, adjacent: Address): GraphStageLogic =
    new MultiIngress(in, out, shape, system, adjacent)

}