package edu.illinois.osl.akka.gc

import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import akka.actor.typed
import org.scalatest.wordspec.AnyWordSpecLike

// class BehaviorsSpec extends ScalaTestWithActorTestKit with AnyWordSpecLike {
// 
//   sealed trait BehaviorMsg extends Message
//   case object SayHello extends BehaviorMsg with NoRefs
//   case object Stop extends BehaviorMsg with NoRefs
//   case class GetRef(ref: ActorRef[Nothing]) extends BehaviorMsg {
//     override def refs = Seq(ref)
//   }
// 
//   private val probe = testKit.createTestProbe[BehaviorMsg]()
//   private val target = testKit.createTestProbe[GCMessage[Nothing]]()
// 
//   // Create a couple of tokens for the references we create
//   private val token1 = Token(probe.ref, 1)
//   private val token2 = Token(probe.ref, 2)
// 
//   "Stopped actors" must {
//     // Create an actor and send it a reference to `target`, then ask it to stop.
//     val actor = testKit.spawn[BehaviorMsg](HelloActor(), "HelloActor")
//     actor ! GetRef(ActorRef[Nothing](Some(token1), None, target.ref))
//     actor ! Stop
// 
//     "release their references when they stop" in {
//       target.expectMessageType[ReleaseMsg[Nothing]]
//     }
//     "ignore messages" in {
//       actor ! SayHello
//       probe.expectNoMessage()
//     }
//     "release any references they receive after they stop" in {
//       actor ! GetRef(ActorRef[Nothing](Some(token2), None, target.ref))
//       target.expectMessageType[ReleaseMsg[Nothing]]
//     }
//   }
// 
//   object HelloActor {
//     def apply(): typed.Behavior[BehaviorMsg] = Behaviors.setupReceptionist(context =>
//       new HelloActor(context)
//     )
//   }
//   class HelloActor(context: ActorContext[BehaviorMsg]) extends AbstractBehavior[BehaviorMsg](context) {
// 
//     override def onMessage(msg: BehaviorMsg): Behavior[BehaviorMsg] = {
//       msg match {
//         case SayHello =>
//           probe.ref ! SayHello
//           this
//         case _: GetRef =>
//           this
//         case Stop =>
//           Behaviors.stopped(context)
//       }
//     }
//   }
// }
// 
// 