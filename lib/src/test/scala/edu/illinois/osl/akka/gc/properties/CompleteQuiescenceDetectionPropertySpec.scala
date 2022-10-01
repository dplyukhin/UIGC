package edu.illinois.osl.akka.gc.properties

import edu.illinois.osl.akka.gc.detector.CompleteQuiescenceDetector
import edu.illinois.osl.akka.gc.properties.model.{BecomeIdle, Configuration, DummyName, DummyRef, DummySnapshot, DummyToken, Execution, Snapshot}
import org.scalacheck.Prop.{collect, forAll, propBoolean}
import org.scalacheck.Prop
import org.scalacheck.util.ConsoleReporter
import org.scalacheck.{Properties, Test}

object CompleteQuiescenceDetectionPropertySpec extends Properties("Complete quiescence detection") {
  import edu.illinois.osl.akka.gc.properties.model.Generators._

  val executionSize = 1000

  override def overrideParameters(p: Test.Parameters): Test.Parameters =
    p.withMinSuccessfulTests(500)
      // This prevents Scalacheck console output from getting wrapped at 75 chars
      .withTestCallback(ConsoleReporter(1, Int.MaxValue))
      // This prevents Scalacheck from giving up when it has to discard a lot of tests
      .withMaxDiscardRatio(100000)

  val detective: CompleteQuiescenceDetector[DummyName, DummyToken, DummyRef, DummySnapshot] =
    new CompleteQuiescenceDetector()

  property(" Quiescence detector has no false positives") =
    forAll(genConfiguration(executionSize, minAmountOfGarbage = 1)) { config => {
      val detectedGarbage = detective.findGarbage(config.snapshots.toMap)
      val approxDetected = (detectedGarbage.size / 5.0).round * 5
      collect(s"~$approxDetected garbage actors detected") {
        s"Detected: $detectedGarbage\nActual: ${config.garbageActors}" |:
          (detectedGarbage.toSet subsetOf config.garbageActors.toSet)
      }
    }}

  property(" If all non-terminated actors take snapshots and terminated snapshots are ignored, then all garbage is detected") =
    forAll(genExecutionAndConfiguration(executionSize, minAmountOfGarbage = 1)) {
      case (execution, config) =>
        // Tell all busy actors to become idle, then tell all non-terminated actors to take a snapshot
        val snapshotters = (config.busyActors ++ config.idleActors).toSet -- config.terminatedActors
        val execution2 = execution ++ config.busyActors.map(BecomeIdle) ++ snapshotters.map(Snapshot)
        val config2 = Configuration.fromExecution(execution2)

        // Use the latest snapshots from every actor, excluding any stale snapshots from terminated actors
        val detectedGarbage = detective.findGarbage(config2.snapshots.toMap -- config2.terminatedActors)

        // Notice that this set is different from `config.garbageActors.toSet` because some busy actors became idle,
        // possibly therefore becoming garbage.
        val garbage = config2.garbageActors.toSet

        s"Detected garbage: $detectedGarbage  must equal actual garbage: $garbage" |:
          detectedGarbage == garbage
    }

  // This is a stronger version of the property above: Only the garbage actors
  // should need to take snapshots in order to be detected.
  val garbageActorsTakeSnapshots: ((Execution, Configuration)) => Prop = {
    case (execution, config) =>
      val execution2 = execution ++ config.garbageActors.map(Snapshot)
      val config2 = Configuration.fromExecution(execution2)

      val detectedGarbage = detective.findGarbage(config2.snapshots.toMap)
      val garbage = config2.garbageActors.toSet

      s"Detected garbage: $detectedGarbage  must equal actual garbage: $garbage" |:
        detectedGarbage == garbage
  }

  // Here is a test for a specific execution that fails the property.
  property(" If all garbage actors take snapshots, then they will all be detected") = {
    import model._
    val execution: Execution = Seq(Spawn(DummyName(0),DummyName(1),DummyRef(Some(DummyToken(1)),Some(DummyName(0)),DummyName(1)),DummyRef(Some(DummyToken(2)),Some(DummyName(1)),DummyName(1))), Send(DummyName(1),DummyRef(Some(DummyToken(2)),Some(DummyName(1)),DummyName(1)),List(),List()), Spawn(DummyName(1),DummyName(2),DummyRef(Some(DummyToken(3)),Some(DummyName(1)),DummyName(2)),DummyRef(Some(DummyToken(4)),Some(DummyName(2)),DummyName(2))), BecomeIdle(DummyName(1)), Send(DummyName(2),DummyRef(Some(DummyToken(4)),Some(DummyName(2)),DummyName(2)),List(),List()), BecomeIdle(DummyName(2)), Receive(DummyName(2),DummyName(2)), Spawn(DummyName(2),DummyName(3),DummyRef(Some(DummyToken(5)),Some(DummyName(2)),DummyName(3)),DummyRef(Some(DummyToken(6)),Some(DummyName(3)),DummyName(3))), BecomeIdle(DummyName(3)), Receive(DummyName(1),DummyName(1)), Deactivate(DummyName(1),DummyRef(Some(DummyToken(3)),Some(DummyName(1)),DummyName(2))), Send(DummyName(2),DummyRef(Some(DummyToken(5)),Some(DummyName(2)),DummyName(3)),List(DummyRef(Some(DummyToken(7)),Some(DummyName(3)),DummyName(3))),List(DummyRef(Some(DummyToken(5)),Some(DummyName(2)),DummyName(3)))), Receive(DummyName(3),DummyName(2)), Spawn(DummyName(3),DummyName(4),DummyRef(Some(DummyToken(8)),Some(DummyName(3)),DummyName(4)),DummyRef(Some(DummyToken(9)),Some(DummyName(4)),DummyName(4))), Deactivate(DummyName(3),DummyRef(Some(DummyToken(7)),Some(DummyName(3)),DummyName(3))), BecomeIdle(DummyName(2)), Send(DummyName(4),DummyRef(Some(DummyToken(9)),Some(DummyName(4)),DummyName(4)),List(),List()), Send(DummyName(3),DummyRef(Some(DummyToken(8)),Some(DummyName(3)),DummyName(4)),List(DummyRef(Some(DummyToken(10)),Some(DummyName(4)),DummyName(3)), DummyRef(Some(DummyToken(11)),Some(DummyName(4)),DummyName(4))),List(DummyRef(Some(DummyToken(6)),Some(DummyName(3)),DummyName(3)), DummyRef(Some(DummyToken(8)),Some(DummyName(3)),DummyName(4)))), Snapshot(DummyName(2)), BecomeIdle(DummyName(3)), Receive(DummyName(3),DummyName(3)), BecomeIdle(DummyName(4)), Receive(DummyName(2),DummyName(1)), Receive(DummyName(4),DummyName(4)), BecomeIdle(DummyName(4)), Receive(DummyName(3),DummyName(2)), Receive(DummyName(4),DummyName(3)), BecomeIdle(DummyName(4)), Snapshot(DummyName(3)), Snapshot(DummyName(4)))
    val config = Configuration.fromExecution(execution)

    garbageActorsTakeSnapshots((execution, config))
  }

  // Here is a general test for that property.
  property(" If all garbage actors take snapshots, then they will all be detected") =
    forAll(genExecutionAndConfiguration(executionSize, minAmountOfGarbage = 2))(garbageActorsTakeSnapshots)
}
