package example

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.{AbstractBehavior, ActorContext, Behaviors}

case class Token(ref: ActorRef[Nothing], n: Int)
case class GCRef[-T](x: Token, from: ActorRef[Nothing], to: ActorRef[T])

object ActorAugmented {
  // message protocol
  sealed trait InternalCommand
  final case class Release(releasing: Set[GCRef[Nothing]], creations: Set[GCRef[Nothing]]) extends InternalCommand
  // a message with a set of references for the recipient to add to its own refs collection
//  final case class ReceiveRefs(receivedRefs: Set[GCRef]) extends InternalCommand
//  object ReceiveRefs {
//    def apply(receiveRefs: ReceiveRefs*) = new ReceiveRefs(receiveRefs.toSet)
//  }


  final case class RefReply(refs: GCRef[Nothing])

  sealed trait ExternalCommand
  final case class Spawn[T](replyTo: GCRef[Nothing], actorType: Behavior[T]) extends ExternalCommand
  final case class Share(target: GCRef[Nothing], recipient: GCRef[Nothing]) extends ExternalCommand
  final case class Forget(fancyRefs: Set[GCRef[Nothing]]) extends ExternalCommand
//  final case class Release() extends ExternalCommand


  def apply(creator: ActorRef[Any], token: Token): Behavior[Any] = {
    Behaviors.setup(context => new ActorAugmented(context, creator, token))
  }
}

class ActorAugmented(context: ActorContext[Any], creator: ActorRef[Any], token: Token)
  extends AbstractBehavior[Any](context) {

  private var refs: Set[GCRef[Nothing]] = Set()
  private var created: Set[GCRef[Nothing]] = Set()
  private var owners: Set[GCRef[Nothing]] = Set(GCRef(token, creator, context.self))
  private var released_owners: Set[GCRef[Nothing]] = Set()
  private var tokenCount: Int = 0;

  private def createToken(): Token = {
    val token = Token(context.self, tokenCount)
    tokenCount += 1
    token
  }

  private def spawn[U](behavior_callback: (ActorRef[Nothing], Token) => Behavior[U]): GCRef[Nothing] = {
    val token = createToken()
    val childRef = context.spawn(behavior_callback(context.self, token), "child")
    val childGCRef = GCRef(token, context.self, childRef)
    refs += childGCRef
    childGCRef
  }

  /**
   * Creates a reference to an actor, to be sent to another actor.
   * @param target The actor the reference being created points to.
   * @param recipient The actor the reference is being sent to.
   * @return A [[GCRef]] pointing from the recipient to the target
   */
  def createRef[T](target: GCRef[T], recipient: GCRef[Nothing]): GCRef[T] = {
    val token = createToken()
    val sharedRef = GCRef(token, recipient.to, target.to)
    created += sharedRef
    sharedRef
  }

  def forget(releasing: Set[GCRef[Nothing]]): Unit = {
    import ActorAugmented._
    // for every reference that is being released
    releasing.foreach((gcRef => {
      val toForget = gcRef.to // get the target being released
      val creations: Set[GCRef[Nothing]] = created.filter {
        createdRef => createdRef.to == toForget
      } // get the refs in the created set that point to the target
      created --= creations // remove those refs from created
      toForget ! Release(releasing, creations) // send the message to the target that we are forgetting it
    }))
  }

  override def onMessage(msg: Any): Behavior[Any] = {
    import ActorAugmented._
    msg match{
//      case Spawn[T](replyTo: GCRef[T], actorType: Behavior[T]) =>
//        replyTo ! RefReply(spawn(actorType))
//        Behaviors.same
//      case ReceiveRefs(receivedRefs) =>
//          refs ++= receivedRefs
//        Behaviors.same
      case Release(releasing, creations) =>
        // for each ref in releasing
        owners --= releasing intersect owners // if the ref is in owners, remove it
        released_owners ++= releasing diff owners // else, add to released_owners
        // for each ref in creations
        released_owners --= creations intersect released_owners // if the ref is in releaseD_owners, remove it
        owners ++= creations diff owners // else, add to owners
        if (owners.isEmpty && released_owners.isEmpty) {
          Behaviors.stopped
        }
        else {
          Behaviors.same
        }
      case Forget(releasing) =>
        forget(releasing)
        Behaviors.same
    }
  }
}