package edu.illinois.osl.akka.gc

// import edu.illinois.osl.akka.gc.detector.CompleteQuiescenceDetector
// import edu.illinois.osl.akka.gc.properties.model.{DummyName, DummyRef, DummySnapshot, DummyToken}
// import org.scalatest.wordspec.AnyWordSpecLike
// 
// import scala.collection.mutable

// class CompleteQuiescenceDetectionSpec extends AnyWordSpecLike {
// 
//   val detective: CompleteQuiescenceDetector[DummyName, DummyToken, DummyRef, DummySnapshot] =
//     new CompleteQuiescenceDetector()
// 
//   "Quiescence detector" must {
// 
//     "identify externally owned refobs as irrelevant" in {
//       assert(!detective.isRelevant(DummyRef(None, None, DummyName(1)), Map()))
//     }
// 
//     "identify refobs as irrelevant if their owner has not taken a snapshot" in {
//       assert(!detective.isRelevant(DummyRef(Some(DummyToken(0)), Some(DummyName(0)), DummyName(1)), Map()))
//     }
// 
//     "add reachable actors to the irrelevant set" in {
//       val irrelevant = mutable.Set[DummyName]()
//       detective.collectReachable(DummyName(0), irrelevant,
//         Map(
//           DummyName(0) -> Set(DummyName(1), DummyName(2)),
//           DummyName(1) -> Set(DummyName(3)),
//           DummyName(3) -> Set(DummyName(0)),
//           DummyName(4) -> Set(DummyName(1))
//         )
//       )
//       assert(irrelevant == mutable.Set(DummyName(0), DummyName(1), DummyName(2), DummyName(3)))
//     }
// 
//     "mark an actor as irrelevant if it has sent messages to itself" in {
//       val self = DummyRef(Some(DummyToken(0)), Some(DummyName(0)), DummyName(0))
//       val result = detective.possiblyRelevantPotentialInverseAcquaintances(DummyName(0), Map(
//         DummyName(0) -> DummySnapshot(refs = Set(self), owners = Set(self),
//           recvCounts = Map(DummyToken(0) -> 1))
//       ))
//       assert(result.isEmpty)
//     }
// 
//     "mark an actor as relevant if it has received all messages sent to itself" in {
//       val self = DummyRef(Some(DummyToken(0)), Some(DummyName(0)), DummyName(0))
//       val result = detective.possiblyRelevantPotentialInverseAcquaintances(DummyName(0), Map(
//         DummyName(0) -> DummySnapshot(refs = Set(self), owners = Set(self),
//           recvCounts = Map(DummyToken(0) -> 1), sentCounts = Map(DummyToken(0) -> 1))
//       ))
//       assert(result.contains(Set(DummyName(0))))
//     }
// 
//     "mark an actor as irrelevant if it has any immediate incoming refobs from an external actor" in {
//       val irrelevant = DummyRef(None, None, DummyName(0))
//       val result = detective.possiblyRelevantPotentialInverseAcquaintances(DummyName(0), Map(
//         DummyName(0) -> DummySnapshot(owners = Set(irrelevant))
//       ))
//       assert(result.isEmpty)
//     }
// 
//     "mark an actor as irrelevant if it has any immediate irrelevant incoming refobs" in {
//       val ref = DummyRef(Some(DummyToken(0)), Some(DummyName(1)), DummyName(0))
// 
//       val result1 = detective.possiblyRelevantPotentialInverseAcquaintances(DummyName(0), Map(
//         DummyName(0) -> DummySnapshot(owners = Set(ref), recvCounts = Map(DummyToken(0) -> 1)),
//         DummyName(1) -> DummySnapshot(refs = Set(ref))
//       ))
//       assert(result1.isEmpty)
// 
//       val result2 = detective.possiblyRelevantPotentialInverseAcquaintances(DummyName(0), Map(
//         DummyName(0) -> DummySnapshot(owners = Set(ref)),
//       ))
//       assert(result2.isEmpty)
//     }
// 
//     "mark an actor as relevant if its immediate incoming refobs are all relevant" in {
//       val ref = DummyRef(Some(DummyToken(0)), Some(DummyName(1)), DummyName(0))
// 
//       val result = detective.possiblyRelevantPotentialInverseAcquaintances(DummyName(0), Map(
//         DummyName(0) -> DummySnapshot(owners = Set(ref)),
//         DummyName(1) -> DummySnapshot(refs = Set(ref)),
//       ))
//       assert(result.contains(Set(DummyName(1))))
// 
//       val result2 = detective.possiblyRelevantPotentialInverseAcquaintances(DummyName(0), Map(
//         DummyName(0) -> DummySnapshot(owners = Set(ref), recvCounts = Map(DummyToken(0) -> 1)),
//         DummyName(1) -> DummySnapshot(refs = Set(ref), sentCounts = Map(DummyToken(0) -> 1)),
//       ))
//       assert(result2.contains(Set(DummyName(1))))
//     }
// 
//     "mark an actor as irrelevant if it has any non-immediate irrelevant incoming refobs" in {
//       val ref = DummyRef(Some(DummyToken(0)), Some(DummyName(1)), DummyName(0))
//       val irrelevant = DummyRef(None, None, DummyName(0))
//       val result = detective.possiblyRelevantPotentialInverseAcquaintances(DummyName(0), Map(
//         DummyName(0) -> DummySnapshot(owners = Set(ref)),
//         DummyName(1) -> DummySnapshot(refs = Set(ref), created = Set(irrelevant))
//       ))
//       assert(result.isEmpty)
//     }
// 
//     "mark an actor as relevant if all incoming refobs are relevant" in {
//       val ref  = DummyRef(Some(DummyToken(0)), Some(DummyName(1)), DummyName(0))
//       val ref2 = DummyRef(Some(DummyToken(1)), Some(DummyName(2)), DummyName(0))
//       val result = detective.possiblyRelevantPotentialInverseAcquaintances(DummyName(0), Map(
//         DummyName(0) -> DummySnapshot(owners = Set(ref)),
//         DummyName(1) -> DummySnapshot(refs = Set(ref), created = Set(ref2)),
//         DummyName(2) -> DummySnapshot(refs = Set(ref2))
//       ))
//       assert(result.contains(Set(DummyName(1), DummyName(2))))
//     }
// 
//     "mark an actor as relevant when all irrelevant incoming refobs are released" in {
//       val ref = DummyRef(Some(DummyToken(0)), Some(DummyName(1)), DummyName(0))
//       val irrelevant = DummyRef(None, None, DummyName(0))
//       val result = detective.possiblyRelevantPotentialInverseAcquaintances(DummyName(0), Map(
//         DummyName(0) -> DummySnapshot(owners = Set(ref), releasedRefs = Set(irrelevant)),
//         DummyName(1) -> DummySnapshot(refs = Set(ref), created = Set(irrelevant))
//       ))
//       assert(result.contains(Set(DummyName(1))))
//     }
// 
//     "not mark an actor as garbage if it has an irrelevant incoming refob" in {
//       val irrelevant = DummyRef(None, None, DummyName(0))
//       val self = DummyRef(Some(DummyToken(0)), Some(DummyName(0)), DummyName(0))
// 
//       val garbage1 = detective.findGarbage(Map(
//         DummyName(0) -> DummySnapshot(refs = Set(self), owners = Set(irrelevant, self))
//       ))
//       assert(garbage1.isEmpty)
// 
//       val ref = DummyRef(Some(DummyToken(1)), Some(DummyName(0)), DummyName(1))
//       val garbage2 = detective.findGarbage(Map(
//         DummyName(0) -> DummySnapshot(refs = Set(self, ref), owners = Set(irrelevant, self)),
//         DummyName(1) -> DummySnapshot(owners = Set(ref))
//       ))
//       assert(garbage2.isEmpty)
//     }
//   }
// }
// 