package edu.illinois.osl.uigc.streams

import akka.actor.{Address, ExtendedActorSystem}
import akka.stream.stage.{GraphStage, GraphStageLogic, InHandler, OutHandler}
import akka.stream.{Attributes, FlowShape, Inlet, Outlet}
import akka.remote.artery.InboundEnvelope
import edu.illinois.osl.uigc.protocol

class Ingress(system: ExtendedActorSystem, adjacentSystem: Address)
  extends GraphStage[FlowShape[InboundEnvelope, InboundEnvelope]] {

  val in: Inlet[InboundEnvelope] = Inlet("Artery.Ingress.in")
  val out: Outlet[InboundEnvelope] = Outlet("Artery.Ingress.out")
  val shape: FlowShape[InboundEnvelope, InboundEnvelope] = FlowShape(in, out)

  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic = {
    protocol.spawnIngress(in, out, shape, system, adjacentSystem)
  }
}

