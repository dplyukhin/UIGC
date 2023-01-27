package edu.illinois.osl.akka.gc

import org.scalatest._
import edu.illinois.osl.akka.gc.properties.model.Configuration

class ModelSpec extends wordspec.AnyWordSpec {

  "The model" should {
    "not throw errors" in {
      Configuration.check(6)
    }
  }
}