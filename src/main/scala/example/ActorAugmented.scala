package example

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.{AbstractBehavior, ActorContext, Behaviors}
import example.ActorAugmented.GCMessage

case class Token(ref: ActorRef[Nothing], n: Int)
case class GCRef[-T](x: Token, from: ActorRef[Nothing], to: ActorRef[T])

object ActorAugmented {
  // message protocol
  /**
   * The type of actor that GCMessage wraps
   * @tparam T
   */
  sealed trait GCMessage[T]
  final case class Release[T](releasing: Iterable[GCRef[T]], creations: Iterable[GCRef[T]]) extends GCMessage[T]
  final case class App[T](payload: T) extends GCMessage[T]
  // a message with a set of references for the recipient to add to its own refs collection
//  final case class ReceiveRefs(receivedRefs: Set[GCRef]) extends InternalCommand
//  object ReceiveRefs {
//    def apply(receiveRefs: ReceiveRefs*) = new ReceiveRefs(receiveRefs.toSet)
//  }


  def apply(creator: ActorRef[Any], token: Token): Behavior[Any] = {
    Behaviors.setup(context => new ActorAugmented(context, creator, token))
  }
}

class ActorAugmented[T](context: ActorContext[GCMessage[T]], creator: ActorRef[Nothing], token: Token)
  extends AbstractBehavior[GCMessage[T]](context) {
  import ActorAugmented._
  private var refs: Set[GCRef[Nothing]] = Set()
  private var created: Set[GCRef[Nothing]] = Set()
  private var owners: Set[GCRef[Nothing]] = Set(GCRef(token, creator, context.self))
  private var released_owners: Set[GCRef[Nothing]] = Set()
  private var tokenCount: Int = 0;

  /**
   *
   * @return
   */
  private def createToken(): Token = {
    val token = Token(context.self, tokenCount)
    tokenCount += 1
    token
  }

  /**
   *
   * @param behavior
   * @tparam T
   * @return
   */
  private def wrap[T](behavior: Behavior[T]): Behavior[GCMessage[T]] = {
    // put something to extend context here??
    // context => Behavior[T]
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
  def createRef[U](target: GCRef[U], recipient: GCRef[Nothing]): GCRef[U] = {
    val token = createToken()
    val sharedRef = GCRef(token, recipient.to, target.to)
    created += sharedRef
    sharedRef
  }

  /**
   * assuming everything in the releasing set points to the same actor
   * @param releasing
   * @tparam T
   */
  def forget[T](releasing: Seq[GCRef[ActorAugmented.Release[T]]]): Unit = {
    import ActorAugmented._
    // for every reference that is being released
    val toForget = releasing.head.to
    val creations = created.filter {
      createdRef =>
        createdRef.to == toForget
    }
    created --= creations // remove those refs from created
    toForget ! Release(releasing, creations) // send the message to the target that we are forgetting it
  }

  override def onMessage(msg: GCMessage[T]): Behavior[GCMessage[T]] = {
    import ActorAugmented._
    msg match {
      case Release(releasing, creations) =>
        releasing.foreach(ref => {
          if (owners.contains(ref)) {
            owners -= ref
          }
          else {
            released_owners += ref
          }
        })
        creations.foreach(ref => {
          if (released_owners.contains(ref)) {
            released_owners -= ref
          }
          else {
            owners += ref
          }
        })
        if (owners.isEmpty && released_owners.isEmpty) {
          // TODO release any refs held by this actor
          Behaviors.stopped
        }
        else {
          Behaviors.same
        }
    }
  }
}