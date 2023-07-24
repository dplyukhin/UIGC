package edu.illinois.osl.akka.gc.protocols.monotone

import akka.actor.{Address, ExtendedActorSystem}
import akka.cluster.Cluster
import akka.remote.artery.{InboundEnvelope, OutboundEnvelope}

import java.util.concurrent.ConcurrentLinkedQueue

object Egress {
  trait Msg
}

class Egress(system: ExtendedActorSystem, adjacentAddress: Address) {
  val queue: ConcurrentLinkedQueue[Ingress.Msg] = new ConcurrentLinkedQueue()
  var seqnum: Int = 0
  val thisAddress: Address = Cluster(system).selfAddress
  var currentEntry: IngressEntry = createEntry()

  def createEntry(): IngressEntry = {
    val entry = new IngressEntry()
    entry.id = seqnum
    entry.egressAddress = thisAddress
    entry.ingressAddress = adjacentAddress
    seqnum += 1
    entry
  }
}

object Ingress {
  trait Msg
}

class Ingress(system: ExtendedActorSystem, adjacentAddress: Address) {
  val queue: ConcurrentLinkedQueue[Ingress.Msg] = new ConcurrentLinkedQueue()
  var seqnum: Int = 0
  val thisAddress: Address = Cluster(system).selfAddress
  var currentEntry: IngressEntry = createEntry()

  def createEntry(): IngressEntry = {
    val entry = new IngressEntry()
    entry.id = seqnum
    entry.egressAddress = adjacentAddress
    entry.ingressAddress = thisAddress
    seqnum += 1
    entry
  }
}