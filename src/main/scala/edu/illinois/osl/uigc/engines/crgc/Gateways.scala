package edu.illinois.osl.uigc.engines.crgc

import akka.actor.{ActorSelectionMessage, Address, ExtendedActorSystem}
import akka.cluster.Cluster
import akka.remote.artery.OutboundHandshake.HandshakeReq
import akka.remote.artery.{InboundEnvelope, ObjectPool, OutboundEnvelope, ReusableOutboundEnvelope}
import akka.stream.stage.{GraphStageLogic, InHandler, OutHandler}
import akka.stream.{FlowShape, Inlet, Outlet}
import akka.util.OptionVal
import edu.illinois.osl.uigc.UIGC

import scala.collection.mutable
import scala.jdk.CollectionConverters.IterableHasAsJava

object Gateway {

  /** A message handled by a garbage collector. The trait includes Gateway messages because gateway
    * actors are not really actors at all - they're Streams stages. To send a message to a gateway,
    * you send a message from its garbage collector to the destination garbage collector - and the
    * message is intercepted.
    */
  trait Msg extends LocalGC.Msg
}

trait Gateway {
  val thisAddress: Address
  var egressAddress: Address
  var ingressAddress: Address
  var currentEntry: IngressEntry
  private var seqnum: Int = 0

  def location: (Address, Address) = (egressAddress, ingressAddress)

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

  println(s"Spawned egress actor $location")

  setHandler(
    in,
    new InHandler {
      override def onPush(): Unit = {
        val env = grab(in)

        // println(s"Egress $location: ${env.message}")
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
            // println(s"Egress $location finalizing entry, window=${oldEntry.id}")
            push(
              out,
              newOutboundEnvelope(oldEntry)
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
      override def onPull(): Unit =
        pull(in)
    }
  )

  private def newOutboundEnvelope(msg: AnyRef): OutboundEnvelope =
    outboundEnvelopePool
      .acquire()
      .init(recipient = OptionVal.None, message = msg, sender = OptionVal.None)
}

object Ingress {
  trait Msg extends Gateway.Msg
}

class Ingress(system: ExtendedActorSystem, adjacentAddress: Address) extends Gateway {
  override val thisAddress: Address = Cluster(system).selfAddress
  private val gc = UIGC(system).asInstanceOf[CRGC].bookkeeper
  override var egressAddress: Address = adjacentAddress
  override var ingressAddress: Address = Cluster(system).selfAddress
  override var currentEntry: IngressEntry = createEntry()
  println(s"Spawned ingress actor $location.")

  gc ! LocalGC.NewIngressActor(adjacentAddress, () => finalizeAndSendEntry(true))

  def finalizeAndSendEntry(isFinal: Boolean = false): Unit = {
    val oldEntry = finalizeEntry()
    if (isFinal) {
      println(s"Finalizing ingress actor $location")
      oldEntry.isFinal = true
      // This entry shouldn't be used again!
      currentEntry = null
    }
    gc ! LocalGC.LocalIngressEntry(oldEntry)
  }
}

class MultiIngress(
    in: Inlet[InboundEnvelope],
    out: Outlet[InboundEnvelope],
    shape: FlowShape[InboundEnvelope, InboundEnvelope],
    system: ExtendedActorSystem,
    _ignore: Address
) extends GraphStageLogic(shape) {

  private var ingressActors: mutable.Map[Address, Ingress] = mutable.Map()

  setHandler(
    in,
    new InHandler {
      override def onPush(): Unit = {
        val env = grab(in)
        env.originUid

        // println(s"Ingress: ${env.message} from ${env.association.toOption.map(_.remoteAddress)}")
        env.message match {
          case msg: AppMsg[_] =>
            val addr = env.association.get.remoteAddress
            val recipient = env.target.get
            ingressActors(addr).currentEntry.onMessage(recipient, msg.refs.asJava)
            push(out, env)

          case entry: IngressEntry =>
            // println(s"Received egress entry (${entry.egressAddress},${entry.ingressAddress}) window=${entry.id}")
            ingressActors(entry.egressAddress).finalizeAndSendEntry()
            pull(in)

          case msg: HandshakeReq =>
            if (!ingressActors.contains(msg.from.address))
              ingressActors(msg.from.address) = new Ingress(system, msg.from.address)
            push(out, env)

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
