package edu.illinois.osl.akka.gc

import org.scalatest._
import edu.illinois.osl.akka.gc.properties.model._
import edu.illinois.osl.akka.gc.interfaces.RefLike

class ModelSpec extends wordspec.AnyWordSpec {

  "Terminated actors" should {
    // "test" in {
    //   import coerce._
    //   import protocols.drl._
    //   val config = Configuration.execute(
    //     // Execution goes here
    //   )
    //   assert(???)
    // }
    "not be unblocked" in {
      Configuration.check(5){ (config, execution) => 
        assert(config.terminated.forall(config.blocked(_)), prettyPrint(config))
      }
    }
    "not have children" in {
      Configuration.check(5){ (config, execution) => 
        assert(config.terminated.forall(config.children(_).isEmpty), prettyPrint(config))
      }
    }
  }
}