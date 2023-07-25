package edu.illinois.osl.akka.gc.protocols.monotone

import akka.actor.{Address, ExtendedActorSystem}
import akka.cluster.Cluster
import akka.remote.artery.{InboundEnvelope, ObjectPool, ReusableOutboundEnvelope}
import edu.illinois.osl.akka.gc.protocols.monotone.Gateway.Msg

import java.util.concurrent.ConcurrentLinkedQueue

object Gateway {
  /**
   * A message handled by a garbage collector. The trait includes Gateway messages because gateway
   * actors are not really actors at all - they're Streams stages. To send a message to a gateway,
   * you send a message from its garbage collector to the destination garbage collector - and the
   * message is intercepted. */
  trait Msg extends Bookkeeper.Msg
}

abstract class Gateway(val system: ExtendedActorSystem) {
  private var seqnum: Int = 0
  val thisAddress: Address = Cluster(system).selfAddress
  var egressAddress: Address
  var ingressAddress: Address
  var currentEntry: IngressEntry

  protected def createEntry(): IngressEntry = {
    val entry = new IngressEntry()
    entry.id = seqnum
    entry.egressAddress = egressAddress
    entry.ingressAddress = ingressAddress
    seqnum += 1
    entry
  }

  def finalizeEntry(): IngressEntry = {
    val entry = currentEntry
    currentEntry = createEntry()
    entry
  }
}

object Egress {
  trait Msg extends Gateway.Msg
  case object FinalizeEgressEntry extends Msg
}

class Egress(system: ExtendedActorSystem, val adjacentAddress: Address, val outboundEnvelopePool: ObjectPool[ReusableOutboundEnvelope])
  extends Gateway(system) {
  override var egressAddress: Address = Cluster(system).selfAddress
  override var ingressAddress: Address = adjacentAddress
  override var currentEntry: IngressEntry = createEntry()

  var isFirstMessage: Boolean = true
  println(s"Spawned egress actor ($ingressAddress, $egressAddress)")
}

object Ingress {
  trait Msg extends Gateway.Msg
  case class GetAdjacentAddress(address: Address) extends Msg
}

class Ingress(system: ExtendedActorSystem)
  extends Gateway(system) {
  // Some fields are null until we learn the ingress address.
  override var egressAddress: Address = _
  override var ingressAddress: Address = Cluster(system).selfAddress
  override var currentEntry: IngressEntry = _
  println(s"Spawned ingress actor at $ingressAddress. Waiting to learn egress address...")

  def setEgressAddress(address: Address): Unit = {
    egressAddress = address
    currentEntry = createEntry()
    println(s"Ingress actor ($ingressAddress, $egressAddress) initialized.")
  }
}