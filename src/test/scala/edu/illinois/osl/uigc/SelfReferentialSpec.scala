package edu.illinois.osl.uigc

import akka.actor.testkit.typed.scaladsl.{ScalaTestWithActorTestKit, TestProbe}
import akka.actor.typed.{PostStop, Signal, Behavior => AkkaBehavior}
import org.scalatest.wordspec.AnyWordSpecLike


// class SelfReferentialSpec extends ScalaTestWithActorTestKit with AnyWordSpecLike {
// 
//   trait NoRefsMessage extends Message {
//     override def refs: Iterable[AnyActorRef] = Seq()
//   }
// 
//   sealed trait ActorMessage extends Message
//   case object Init extends ActorMessage with NoRefsMessage
//   case class GetRef(ref: ActorRef[ActorMessage]) extends ActorMessage with Message {
//     override def refs: Iterable[AnyActorRef] = Iterable(ref)
//   }
// 
//   sealed trait ProbeMessage extends Message with NoRefsMessage
//   case class SnapshotMessage(snapshot: ActorSnapshot) extends ProbeMessage
//   case class CreatedRef(ref: AnyActorRef) extends ProbeMessage
// 
// 
//   val probe: TestProbe[ProbeMessage] = testKit.createTestProbe[ProbeMessage]()
// 
//   // In this test, an actor A gives actor B a reference to itself. We check
//   // that A correctly recorded the existence of that reference in its state.
// 
//   "Actors" must {
//     val actorA = testKit.spawn(ActorA(), "actorA")
//     "remember the references to themselves that they give to others" in {
//       actorA ! Init
//       val snapshot1 = probe.expectMessageType[SnapshotMessage].snapshot
//       val createdRef = probe.expectMessageType[CreatedRef].ref
//       val snapshot2 = probe.expectMessageType[SnapshotMessage].snapshot
// 
//       snapshot2.owners.toSet shouldEqual (snapshot1.owners.toSet + createdRef)
//     }
//   }
// 
//   object ActorA {
//     def apply(): AkkaBehavior[ActorMessage] = Behaviors.setupReceptionist(context => new ActorA(context))
//   }
// 
//   object ActorB {
//     def apply(): ActorFactory[ActorMessage] = {
//       Behaviors.setup(context => new ActorB(context))
//     }
//   }
// 
//   class ActorA(context: ActorContext[ActorMessage]) extends AbstractBehavior[ActorMessage](context) {
//     var actorB: ActorRef[ActorMessage] = _
// 
//     override def onMessage(msg: ActorMessage): Behavior[ActorMessage] = {
//       msg match {
//         case Init =>
//           actorB = context.spawn(ActorB(), "actorB")
//           probe.ref ! SnapshotMessage(context.snapshot())
//           val ref = context.createRef(context.self, actorB)
//           actorB ! GetRef(ref)
//           probe.ref ! CreatedRef(ref)
//           probe.ref ! SnapshotMessage(context.snapshot())
//           this
// 
//         case _ => this
//       }
//     }
//   }
// 
//   class ActorB(context: ActorContext[ActorMessage]) extends AbstractBehavior[ActorMessage](context) {
//     var actorA: ActorRef[ActorMessage] = _
// 
//     override def onMessage(msg: ActorMessage): Behavior[ActorMessage] = {
//       msg match {
//         case GetRef(ref) =>
//           actorA = ref
//           this
//         case _ => this
//       }
//     }
//   }
// 
// }
// 
