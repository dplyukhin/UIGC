package edu.illinois.osl.akka.gc

import org.scalacheck._
import org.scalacheck.Prop.{forAll, forAllNoShrink}

import scala.util.Random
import edu.illinois.osl.akka.gc.protocols.monotone.{RefobInfo, RefobStatus}
import org.scalacheck.Arbitrary.arbitrary

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

  property("RefobStatus should accurately reflect refob status") = {
    trait Status {
      val count: Int
    }
    case class Pending(count: Int) extends Status
    case class Active(count: Int) extends Status
    case class Finishing(count: Int) extends Status
    case object Released extends Status {
      val count = 0
    }

    def compare(status: Status, i: Int): Boolean = {
      status match {
        case Active(count) =>
          RefobStatus.count(i) == count && RefobStatus.isActive(i)
        case Finishing(count) =>
          RefobStatus.count(i) == count && RefobStatus.isFinishing(i)
        case Pending(count) =>
          RefobStatus.count(i) == count && RefobStatus.isPending(i)
        case Released =>
          RefobStatus.isReleased(i)
      }
    }

    trait Op
    case object Inc extends Op
    case object Dec extends Op
    case object Activate extends Op
    case object Deactivate extends Op

    type Execution = Seq[Op]

    // An execution may consist of:
    // 1. Any number of Dec operations, then
    // 2. An activate operation
    // 3. Any number of Inc operations and up to N Dec operations, where N is
    //    the number of Inc operations minus the number of previous Decs
    // 4. A deactivate operation
    // 5. Dec by N, where N is the difference between the number of Inc and
    //    Dec operations
    def genExecution: Gen[Execution] = for {
      num_prev_decs <- Gen.choose(0, 30000)
      num_incs <- Gen.choose(num_prev_decs, 30000)
      num_decs <- Gen.choose(0, num_incs - num_prev_decs)
    } yield {
      val prev_decs = Seq.fill(num_prev_decs)(Dec)
      val incs = Seq.fill(num_incs)(Inc)
      val decs = Seq.fill(num_decs)(Dec)
      val final_decs = Seq.fill(num_incs - num_prev_decs - num_decs)(Dec)
      (prev_decs :+ Activate) ++ (Random.shuffle(incs ++ decs) :+ Deactivate) ++ final_decs
    }

    forAllNoShrink(genExecution) { (execution: Execution) =>
      var realStatus: Status = Pending(0)
      var testStatus = RefobStatus.initialPendingRefob
      val initial = compare(realStatus, testStatus)
      val rest = execution.forall { op =>
        op match {
          case Activate =>
            realStatus = Active(realStatus.count)
            testStatus = RefobStatus.activate(testStatus)
          case Deactivate =>
            realStatus = if (realStatus.count != 0) {
              Finishing(realStatus.count)
            } else {
              Released
            }
            testStatus = RefobStatus.deactivate(testStatus)
          case Dec =>
            realStatus = realStatus match {
              case Pending(n) => Pending(n - 1)
              case Active(n) => Active(n - 1)
              case Finishing(n) => if (n > 1) {
                Finishing(n - 1)
              } else {
                Released
              }
            }
            testStatus = RefobStatus.addToRecvCount(testStatus, 1)
          case Inc =>
            realStatus = realStatus match {
              case Pending(n) => Pending(n + 1)
              case Active(n) => Active(n + 1)
            }
            testStatus = RefobStatus.addToSentCount(testStatus, 1)
        }
        compare(realStatus, testStatus)
      }
      initial && rest
    }
  }
}