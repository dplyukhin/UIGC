package edu.illinois.osl.akka.gc.streams

import akka.stream.stage.{GraphStage, GraphStageLogic, InHandler, OutHandler}
import akka.stream.{Attributes, FlowShape, Inlet, Outlet}
import akka.remote.artery.InboundEnvelope

class Ingress()
  extends GraphStage[FlowShape[InboundEnvelope, InboundEnvelope]] {

  val in: Inlet[InboundEnvelope] = Inlet("Artery.Ingress.in")
  val out: Outlet[InboundEnvelope] = Outlet("Artery.Ingress.out")
  val shape: FlowShape[InboundEnvelope, InboundEnvelope] = FlowShape(in, out)

  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic =
    new GraphStageLogic(shape) {
      setHandler(in, new InHandler {
        override def onPush(): Unit = {
          val msg = grab(in)
          //println(msg)
          push(out, msg)
        }
      })
      setHandler(out, new OutHandler {
        override def onPull(): Unit = {
          pull(in)
        }
      })
    }
}

