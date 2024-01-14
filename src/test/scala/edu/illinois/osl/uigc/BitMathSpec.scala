package edu.illinois.osl.uigc

import org.scalacheck._
import org.scalacheck.Prop.forAllNoShrink

import scala.util.Random
import edu.illinois.osl.uigc.engines.crgc.RefobInfo

object BitMathSpec extends Properties("Bitmath") {

  property("RefobInfo should accurately reflect refob info") = {
    trait Info {
      val count: Int
    }
    case class Active(count: Int) extends Info
    case class Deactivated(count: Int) extends Info

    trait Op
    case object Inc extends Op
    case object Reset extends Op
    case object Deactivate extends Op

    def compare(info: Info, i: Short): Boolean = {
      info match {
        case Active(count) => RefobInfo.isActive(i) && RefobInfo.count(i) == count
        case Deactivated(count) => !RefobInfo.isActive(i) && RefobInfo.count(i) == count
      }
    }

    type Execution = Seq[Op]

    def genExecution: Gen[Execution] = for {
      num_incs <- Gen.choose(0,1000)
      num_resets <- Gen.choose(0,1000)
    } yield {
      val incs = Seq.fill(num_incs)(Inc)
      val resets = Seq.fill(num_resets)(Reset)
      Random.shuffle(incs ++ resets) :+ Deactivate
    }

    forAllNoShrink(genExecution) { (execution: Execution) =>
      var realInfo: Info = Active(0)
      var testInfo = RefobInfo.activeRefob
      val initial = compare(realInfo, testInfo)
      val rest = execution.forall { op =>
        op match {
          case Inc =>
            realInfo = Active(realInfo.count + 1)
            testInfo = RefobInfo.incSendCount(testInfo)
          case Reset =>
            realInfo = Active(0)
            testInfo = RefobInfo.resetCount(testInfo)
          case Deactivate =>
            realInfo = Deactivated(realInfo.count)
            testInfo = RefobInfo.deactivate(testInfo)
        }
        compare(realInfo, testInfo)
      }
      initial && rest
    }
  }
}
