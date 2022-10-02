package edu.illinois.osl.akka.gc.properties

import edu.illinois.osl.akka.gc.detector.{CompleteQuiescenceDetector, SimpleQuiescenceDetector}
import edu.illinois.osl.akka.gc.properties.model.{BecomeIdle, Configuration, DroppedMessage, DummyName, DummyRef, DummySnapshot, DummyToken, Execution, Snapshot}
import org.scalacheck.Prop.{collect, forAll, propBoolean}
import org.scalacheck.util.ConsoleReporter
import org.scalacheck.{Gen, Properties, Test}

object DroppedMessagesSpec extends Properties("Dropped messages spec") {

  import edu.illinois.osl.akka.gc.properties.model.Generators._

  val executionSize = 1000

  override def overrideParameters(p: Test.Parameters): Test.Parameters =
    p.withMinSuccessfulTests(50)
      // This prevents Scalacheck console output from getting wrapped at 75 chars
      .withTestCallback(ConsoleReporter(1, Int.MaxValue))
      // This prevents Scalacheck from giving up when it has to discard a lot of tests
      .withMaxDiscardRatio(100000)


  property(s"Dropped message statistics (execution size $executionSize)") =
    forAll(genExecution(executionSize, minAmountOfGarbage = 1, probability = defaultWithDroppedMessages))
    { execution => {
      val numDrops = execution.count { _.isInstanceOf[DroppedMessage] }
      val approx = (numDrops / 5.0).round * 5
      collect(s"~$approx messages dropped") {
        true
      }
    }}

  // For debugging purposes, log how many garbage actors are typically generated
  property(s" Garbage statistics (execution size $executionSize)") =
    forAll(genConfiguration(executionSize, minAmountOfGarbage = 1, probability = defaultWithDroppedMessages))
    { config => {
      val approxGarbage = (config.garbageActors.size / 5.0).round * 5
      collect(s"~$approxGarbage garbage actors generated") {
        true
      }
    }}


  // Check that self-terminating actors are indeed "simple" garbage, even when messages are dropped

  property(" Terminated actors are blocked") =
    forAll(genConfiguration(executionSize, minAmountOfGarbage = 1, probability = defaultWithDroppedMessages))
    { config => {
      config.terminatedActors.forall(config.blocked)
    }}

  property(" Terminated actors have no nontrivial inverse acquaintances") =
    forAll(genConfiguration(executionSize, minAmountOfGarbage = 1, probability = defaultWithDroppedMessages))
    { config => {
      config.terminatedActors.forall { actor =>
        config.potentialInverseAcquaintances(actor).toSet == Set(actor)
      }
    }}


  // Check that quiescence detection remains safe in the face of message loss

  property(" Simple quiescence detector has no false positives") =
    forAll(genConfiguration(executionSize, minAmountOfGarbage = 1, probability = defaultWithDroppedMessages))
    { config => {
      val detective =
        new SimpleQuiescenceDetector[DummyName, DummyToken, DummyRef, DummySnapshot]()

      val detectedGarbage = detective.findGarbage(config.snapshots.toMap)
      val approxDetected = (detectedGarbage.size / 5.0).round * 5
      collect(s"~$approxDetected garbage actors detected") {
        detectedGarbage subsetOf config.garbageActors.toSet
      }
    }}

  property(" Complete quiescence detector has no false positives") =
    forAll(genConfiguration(executionSize, minAmountOfGarbage = 1, probability = defaultWithDroppedMessages))
    { config => {
      val detective =
        new CompleteQuiescenceDetector[DummyName, DummyToken, DummyRef, DummySnapshot]()

      val detectedGarbage = detective.findGarbage(config.snapshots.toMap)
      val approxDetected = (detectedGarbage.size / 5.0).round * 5
      collect(s"~$approxDetected garbage actors detected") {
        detectedGarbage.toSet subsetOf config.garbageActors.toSet
      }
    }}
}