package gc.properties

import gc.properties.model.Configuration
import org.scalacheck.Gen.oneOf
import org.scalacheck.Prop.{all, collect, forAll, forAllNoShrink, propBoolean}
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

  // Check that any generated execution can be replayed on a fresh Configuration;
  // this is useful for shrinking and reproducibility of bugs.
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

  // Uncomment below to check that non-causal executions are possible; the test should fail on some executions

  // import gc.properties.model.{Receive, Send}
  //
  // property(" Message delivery is not causal; this test should fail") =
  //   forAll(genExecution(100)) { execution =>
  //     val actors = Configuration.fromExecution(execution).actors
  //
  //     forAll(oneOf(actors)) { actor =>
  //       // The sequence of actors that have sent messages to `actor`
  //       val sends = execution.flatMap {
  //         case Send(sender, ref, _, _) if ref.target == actor => Seq(sender)
  //         case _ => Seq()
  //       }
  //       // The sequence of actors from which `actor` has received a message
  //       val receives = execution.flatMap {
  //         case Receive(recipient, sender) if recipient == actor => Seq(sender)
  //         case _ => Seq()
  //       }
  //       // In causal delivery, actors receive messages in the same order they are sent;
  //       // hence `sends` is a prefix of `receives`.
  //       sends.zip(receives).forall{ case (sender1, sender2) => sender1 == sender2 } :|
  //       s"Messages sent to actor ${actor} in this order: ${sends}\nbut received in this order: ${receives}"
  //     }
  //   }

  property(" Actors are either blocked or unblocked, never both") =
    forAll(genConfiguration(executionSize)) { config =>
      config.actors.forall { actor =>
        (config.blocked(actor) && !config.unblocked(actor)) ||
        (!config.blocked(actor) && config.unblocked(actor))
      }
    }

  property(" Blocked actors are idle") =
    forAll(genConfiguration(executionSize)) { config =>
      config.blockedActors.forall(config.idle)
    }

  property(" Blocked actors have no undelivered messages") =
    forAll(genConfiguration(executionSize)) { config =>
      config.blockedActors.forall(config.pendingMessages(_).isEmpty)
    }

  property(" The initial actor is a receptionist") =
    forAll(genConfiguration(executionSize)) { config =>
      config.receptionist(config.initialActor)
    }

  property(" Receptionists are unblocked") =
    forAll(genConfiguration(executionSize)) { config =>
      config.actors.filter(config.receptionist).forall(config.unblocked)
    }

  property(" Terminated actors are blocked") =
    forAll(genConfiguration(executionSize)) { config =>
      config.terminatedActors.forall(config.blocked)
    }

  property(" Terminated actors have no nontrivial inverse acquaintances") =
    forAll(genConfiguration(executionSize)) { config =>
      config.terminatedActors.forall { actor =>
        config.potentialInverseAcquaintances(actor).toSet == Set(actor)
      }
    }

  property(" Garbage actors are blocked") =
    forAll(genConfiguration(executionSize, minAmountOfGarbage = 10)) { config => {
      config.garbageActors.forall(config.blocked)
    }}

  property(" Garbage actors are not already terminated") =
    forAll(genConfiguration(executionSize, minAmountOfGarbage = 10)) { config => {
      config.garbageActors.forall(!config.terminated(_))
    }}

  property(" Potential inverse acquaintances of garbage are blocked") =
    forAll(genConfiguration(executionSize, minAmountOfGarbage = 10)) { config => {
      config.garbageActors.forall { actor =>
        config.potentialInverseAcquaintances(actor).forall(config.garbage)
      }
    }}

  property(" Garbage actors are not reachable from a receptionist") =
    forAll(genConfiguration(executionSize, minAmountOfGarbage = 10)) { config => {
      config.garbageActors.forall { actor =>
        config.canPotentiallyReach(actor).forall(!config.receptionist(_))
      }
    }}

  // By the preceding tests, this implies that garbage actors remain idle
  // and their mailbox stays empty
  property(" Garbage actors remain garbage") =
    forAll(genConfiguration(executionSize, minAmountOfGarbage = 10)) { config => {
      // Compute the set of garbage actors in `config`, run the system for a
      // few more steps, and then check that those actors have remained garbage.
      // Note that `laterConfig` is the same object as `config`, but mutated.
      val garbage = config.garbageActors.toSet

      forAll(genConfiguration(100, initialConfig = config)) { laterConfig => {
        garbage subsetOf laterConfig.garbageActors.toSet
      }}
    }}

  // For debugging purposes, log how many garbage actors are typically generated
  property(s" Garbage statistics (execution size $executionSize)") =
    forAll(genConfiguration(executionSize, minAmountOfGarbage = 10)) { config => {
      val approxGarbage = (config.garbageActors.size / 5.0).round * 5
      collect(s"~$approxGarbage garbage actors generated") {
        true
      }
    }}
}
