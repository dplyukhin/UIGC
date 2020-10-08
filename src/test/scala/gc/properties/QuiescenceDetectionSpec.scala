package gc.properties

import gc.QuiescenceDetector
import gc.properties.model.{DummyName, DummyRef, DummySnapshot, DummyToken}
import org.scalacheck.Prop.{collect, forAll}
import org.scalacheck.util.ConsoleReporter
import org.scalacheck.{Properties, Test}

object QuiescenceDetectionSpec extends Properties("Quiescence detection") {
  import gc.properties.model.Generators._

  val executionSize = 1000

  override def overrideParameters(p: Test.Parameters): Test.Parameters =
    p.withMinSuccessfulTests(100)
      // This prevents Scalacheck console output from getting wrapped at 75 chars
      .withTestCallback(ConsoleReporter(1, Int.MaxValue))
      // This prevents Scalacheck from giving up when it has to discard a lot of tests
      .withMaxDiscardRatio(100000)

  property(" Quiescence detector has no false positives") =
    forAll(genConfiguration(executionSize, minAmountOfGarbage = 10)) { config => {
      val q: QuiescenceDetector[DummyName, DummyToken, DummyRef, DummySnapshot] =
        new QuiescenceDetector()

      val detectedGarbage = q.findTerminated(config.snapshots.toMap)
      val approxDetected = (detectedGarbage.size / 5.0).round * 5
      collect(s"~$approxDetected garbage actors detected") {
        detectedGarbage subsetOf config.garbageActors.toSet
      }
    }}

  // TODO: If all garbage actors take snapshots, they will be detected as garbage
}
