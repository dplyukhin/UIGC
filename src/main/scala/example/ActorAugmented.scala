package example

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.{AbstractBehavior, ActorContext, Behaviors}

case class Token(ref: ActorRef[Any], n: Int)
case class FancyRef(x: Token, from: ActorRef[Any], to: ActorRef[Any])

object ActorAugmented {
  // message protocol
  sealed trait InternalCommand
  final case class Release(releasing: Set[FancyRef], creations: Set[FancyRef]) extends InternalCommand
  // a message with a set of references for the recipient to add to its own refs collection
  final case class ReceiveRefs(receivedRefs: Set[FancyRef]) extends InternalCommand
  object ReceiveRefs {
    def apply(receiveRefs: ReceiveRefs*) = new ReceiveRefs(receiveRefs.toSet)
  }


  final case class RefReply(refs: FancyRef)

  sealed trait ExternalCommand
  final case class Spawn(replyTo: ActorRef[Any]) extends ExternalCommand
  final case class Share(target: FancyRef, recipient: FancyRef) extends ExternalCommand
  final case class Forget(fancyRefs: Set[FancyRef]) extends ExternalCommand
//  final case class Release() extends ExternalCommand


  def apply(creator: ActorRef[Any], token: Token): Behavior[Any] = {
    Behaviors.setup(context => new ActorAugmented(context, creator, token))
  }
}

class ActorAugmented(context: ActorContext[Any], creator: ActorRef[Any], token: Token)
  extends AbstractBehavior[Any](context) {

  private var refs: Set[FancyRef] = Set()
  private var created: Set[FancyRef] = Set()
  private var owners: Set[FancyRef] = Set(FancyRef(token, creator, context.self))
  private var released_owners: Set[FancyRef] = Set()
  private var tokenCount: Int = 0;

  private def createToken(): Token = {
    val token = Token(context.self, tokenCount)
    tokenCount += 1
    token
  }

  private def spawn(): FancyRef = {
    val token = createToken()
    val childRef = context.spawn(ActorAugmented(context.self, token), "child")
    val childFancyRef = FancyRef(token, context.self, childRef)
    refs += childFancyRef
    childFancyRef
  }

  def createRef(target: FancyRef, recipient: FancyRef): FancyRef = {
    val token = createToken()
    val sharedRef = FancyRef(token, recipient.to, target.to)
    created += sharedRef
    sharedRef
  }

  override def onMessage(msg: Any): Behavior[Any] = {
    import ActorAugmented._
    msg match{
      case Spawn(replyTo) =>
        replyTo ! RefReply(spawn())
        Behaviors.same
      case ReceiveRefs(receivedRefs) =>
          refs ++= receivedRefs
        Behaviors.same
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
        // for every reference that is being released
        releasing.foreach((fancyRef => {
          val toForget = fancyRef.to // get the target being released
          val creations: Set[FancyRef] = created.filter {
            createdRef => createdRef.to == toForget
          } // get the refs in the created set that point to the target
          created --= creations // remove those refs from created
          toForget ! Release(releasing, creations) // send the message to the target that we are forgetting it
        }))
        Behaviors.same
    }
  }
}