package edu.illinois.osl.uigc.streams

import akka.actor.{Address, ExtendedActorSystem}
import akka.remote.artery.{ObjectPool, OutboundEnvelope, ReusableOutboundEnvelope}
import akka.stream.stage.{GraphStage, GraphStageLogic, InHandler, OutHandler}
import akka.stream.{Attributes, FlowShape, Inlet, Outlet}
import edu.illinois.osl.uigc.protocol

class Egress(system: ExtendedActorSystem, adjacentSystem: Address, outboundObjectPool: ObjectPool[ReusableOutboundEnvelope])
  extends GraphStage[FlowShape[OutboundEnvelope, OutboundEnvelope]] {

  val in: Inlet[OutboundEnvelope] = Inlet("Artery.Ingress.in")
  val out: Outlet[OutboundEnvelope] = Outlet("Artery.Ingress.out")
  val shape: FlowShape[OutboundEnvelope, OutboundEnvelope] = FlowShape(in, out)

  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic = {
    protocol.spawnEgress(in, out, shape, system, adjacentSystem, outboundObjectPool)
  }
}

