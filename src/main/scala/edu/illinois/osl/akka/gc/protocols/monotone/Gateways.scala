package edu.illinois.osl.akka.gc.protocols.monotone

import akka.actor.{ActorSelectionMessage, Address, ExtendedActorSystem}
import akka.cluster.Cluster
import akka.remote.artery.{InboundEnvelope, ObjectPool, OutboundEnvelope, ReusableOutboundEnvelope}
import akka.stream.stage.{GraphStageLogic, InHandler, OutHandler}
import akka.stream.{FlowShape, Inlet, Outlet}
import akka.util.OptionVal

import scala.jdk.CollectionConverters.IterableHasAsJava

object Gateway {

  /** A message handled by a garbage collector. The trait includes Gateway messages because gateway
    * actors are not really actors at all - they're Streams stages. To send a message to a gateway,
    * you send a message from its garbage collector to the destination garbage collector - and the
    * message is intercepted.
    */
  trait Msg extends Bookkeeper.Msg
}

trait Gateway {
  val thisAddress: Address
  var egressAddress: Address
  var ingressAddress: Address
  var currentEntry: IngressEntry
  private var seqnum: Int = 0

  def finalizeEntry(): IngressEntry = {
    val entry = currentEntry
    currentEntry = createEntry()
    entry
  }

  protected def createEntry(): IngressEntry = {
    val entry = new IngressEntry()
    entry.id = seqnum
    entry.egressAddress = egressAddress
    entry.ingressAddress = ingressAddress
    seqnum += 1
    entry
  }
}

object Egress {
  trait Msg extends Gateway.Msg
  case object FinalizeEgressEntry extends Msg
}

class Egress(
    in: Inlet[OutboundEnvelope],
    out: Outlet[OutboundEnvelope],
    shape: FlowShape[OutboundEnvelope, OutboundEnvelope],
    system: ExtendedActorSystem,
    adjacentAddress: Address,
    outboundEnvelopePool: ObjectPool[ReusableOutboundEnvelope]
) extends GraphStageLogic(shape)
    with Gateway {

  override val thisAddress: Address = Cluster(system).selfAddress
  override var egressAddress: Address = Cluster(system).selfAddress
  override var ingressAddress: Address = adjacentAddress
  override var currentEntry: IngressEntry = createEntry()

  var isFirstMessage: Boolean = true
  println(s"Spawned egress actor ($egressAddress, $ingressAddress)")

  setHandler(
    in,
    new InHandler {
      override def onPush(): Unit = {
        val env = grab(in)

        env.message match {
          case msg: AppMsg[_] =>
            // Set the window, update the entry, and push it on through
            val recipient = env.target.get
            msg.windowID = currentEntry.id
            currentEntry.onMessage(recipient, msg.refs.asJava)
            push(out, env)

          case ActorSelectionMessage(Egress.FinalizeEgressEntry, _, _) =>
            // Being asked to finalize the entry. Push it to the ingress.
            val oldEntry = finalizeEntry()
            println(s"Egress ($thisAddress) finalizing entry for $adjacentAddress, window=${oldEntry.id}")
            push(
              out, newOutboundEnvelope(oldEntry)
            )

          case _ =>
            // Non-GC message, ignore.
            push(out, env)
        }
      }
    }
  )
  setHandler(
    out,
    new OutHandler {
      override def onPull(): Unit = {
        if (isFirstMessage) {
          push(out,
            newOutboundEnvelope(Ingress.GetAdjacentAddress(thisAddress))
          )
          isFirstMessage = false
        }
        else
          pull(in)
      }
    }
  )

  private def newOutboundEnvelope(msg: AnyRef): OutboundEnvelope =
    outboundEnvelopePool
      .acquire()
      .init(
        recipient = OptionVal.None,
        message = msg,
        sender = OptionVal.None)
}

object Ingress {
  trait Msg extends Gateway.Msg
  case class GetAdjacentAddress(address: Address) extends Msg
}

class Ingress(
    in: Inlet[InboundEnvelope],
    out: Outlet[InboundEnvelope],
    shape: FlowShape[InboundEnvelope, InboundEnvelope],
    system: ExtendedActorSystem,
    _ignore: Address
) extends GraphStageLogic(shape)
    with Gateway {

  // Some fields are null until we learn the ingress address.
  override val thisAddress: Address = Cluster(system).selfAddress
  override var egressAddress: Address = _
  override var ingressAddress: Address = Cluster(system).selfAddress
  override var currentEntry: IngressEntry = _
  println(s"Spawned ingress actor at $thisAddress. Waiting to learn egress address...")

  def setEgressAddress(address: Address): Unit = {
    egressAddress = address
    currentEntry = createEntry()
    println(s"Ingress actor ($egressAddress, $ingressAddress) initialized.")
  }

  setHandler(
    in,
    new InHandler {
      override def onPush(): Unit = {
        val env = grab(in)

        env.message match {
          case msg: AppMsg[_] =>
            val recipient = env.target.get
            currentEntry.onMessage(recipient, msg.refs.asJava)
            push(out, env)

          case entry: IngressEntry =>
            println(s"Ingress ($thisAddress) got egress entry from $egressAddress, window=${entry.id}")
            val oldEntry = finalizeEntry()
            ActorGC(system).bookkeeper ! Bookkeeper.LocalIngressEntry(egressAddress, oldEntry)
            pull(in)

          case Ingress.GetAdjacentAddress(address) =>
            setEgressAddress(address)
            pull(in)

          case _ =>
            push(out, env)
        }
      }
    }
  )
  setHandler(
    out,
    new OutHandler {
      override def onPull(): Unit =
        pull(in)
    }
  )
}
