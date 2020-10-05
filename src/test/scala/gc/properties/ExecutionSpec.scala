package gc.properties

import gc.properties.model.Configuration
import org.scalacheck.Prop.{collect, forAll, forAllNoShrink}
import org.scalacheck.util.ConsoleReporter
import org.scalacheck.{Properties, Test}

object ExecutionSpec extends Properties("Properties of executions") {
  import gc.properties.model.Generators._

  val executionSize = 1000

  override def overrideParameters(p: Test.Parameters): Test.Parameters =
    p.withMinSuccessfulTests(100)
      // This prevents Scalacheck console output from getting wrapped at 75 chars
      .withTestCallback(ConsoleReporter(1, Int.MaxValue))
      // This prevents Scalacheck from giving up when it has to discard a lot of tests
      .withMaxDiscardRatio(100000)

  property(" Executions should be reproducible") =
    forAllNoShrink(genExecution(executionSize)) { execution =>
      val c = new Configuration()
      // This throws an exception if the execution is invalid, failing the test
      for (event <- execution) c.transition(event)
      true
    }

  // Uncomment below to test the testcase shrinker; should produce an empty execution

  // property(" Test") =
  //   forAll(genExecution(executionSize)) { execution =>
  //     false
  //   }

  property(" Blocked actors are idle") =
    forAll(genConfiguration(executionSize)) { config =>
      config.blockedActors.forall(config.idle)
    }

  property(" Blocked actors have no undelivered messages") =
    forAll(genConfiguration(executionSize)) { config =>
      config.blockedActors.forall(config.pendingMessages(_).isEmpty)
    }

  property(" Garbage actors must also be blocked") =
    forAll(genConfiguration(executionSize, minAmountOfGarbage = 10)) { config =>
      config.garbageActors subsetOf config.blockedActors.toSet
    }

  property(" Potential inverse acquaintances of garbage must also be blocked") =
    forAll(genConfiguration(executionSize, minAmountOfGarbage = 10)) { config => {
      val garbage = config.garbageActors
      val blocked = config.blockedActors.toSet
      garbage.forall(config.potentialInverseAcquaintances(_).toSet subsetOf blocked)
    }}

  // By the preceding tests, this implies that garbage actors remain idle
  // and their mailbox stays empty
  property(" Garbage actors remain garbage") =
    forAll(genConfiguration(executionSize, minAmountOfGarbage = 10)) { config => {
      // Compute the set of garbage actors in `config`, run the system for a
      // few more steps, and then check that those actors have remained garbage.
      // Note that `laterConfig` is the same object as `config`, but mutated.
      val garbage = config.garbageActors

      forAll(genConfiguration(executionSize, initialConfig = config)) { laterConfig => {
        garbage subsetOf laterConfig.garbageActors
      }}
    }}
}
