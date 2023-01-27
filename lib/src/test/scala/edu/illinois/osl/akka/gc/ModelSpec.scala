package edu.illinois.osl.akka.gc

import org.scalatest._
import edu.illinois.osl.akka.gc.properties.model._

class ModelSpec extends wordspec.AnyWordSpec {

  "Terminated actors" should {
    "not be unblocked" in {
      Configuration.check(6){ (config, execution) => 
        assert(config.terminated.forall(config.blocked(_)) === true, pretty(execution))
      }
    }
    "not have children" in {
      Configuration.check(6){ (config, execution) => 
        assert(config.terminated.forall(config.children(_).isEmpty) === true, pretty(execution))
      }
    }
  }
}