package edu.illinois.osl.akka.gc.properties

import edu.illinois.osl.akka.gc.protocol
import edu.illinois.osl.akka.gc.interfaces._
import edu.illinois.osl.akka.gc

package object model {
  case class Payload()
  type Msg = protocol.GCMessage[Payload]
  type Ref = gc.ActorRef[Payload]
  type Execution = Seq[Event]
  def prettyPrint(config: Configuration): String = 
    "\n" + config.log.map(_.pretty).mkString("\n") + "\n\n" +
    "Execution: " + config.execution + "\n\n"

}
