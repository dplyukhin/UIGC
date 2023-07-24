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

abstract class Gateway(system: ExtendedActorSystem, egressAddress: Address, ingressAddress: Address) {
  private var seqnum: Int = 0

  val queue: ConcurrentLinkedQueue[Ingress.Msg] = new ConcurrentLinkedQueue()
  val thisAddress: Address = Cluster(system).selfAddress
  var currentEntry: IngressEntry = createEntry()

  private def createEntry(): IngressEntry = {
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

class Egress(val system: ExtendedActorSystem, val adjacentAddress: Address, val outboundEnvelopePool: ObjectPool[ReusableOutboundEnvelope])
  extends Gateway(system, Cluster(system).selfAddress, adjacentAddress) {
}

object Ingress {
  trait Msg extends Gateway.Msg
}

class Ingress(val system: ExtendedActorSystem, val adjacentAddress: Address)
  extends Gateway(system, adjacentAddress, Cluster(system).selfAddress) {
}