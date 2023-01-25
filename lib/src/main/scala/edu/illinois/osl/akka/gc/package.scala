package edu.illinois.osl.akka

package object gc {

  val protocol: Protocol = protocols.drl.DRL

  /**
   * All messages to garbage-collected actors must implement this interface.
   * It allows the GC middleware to find references inside the message and
   * take necessary action accordingly.
   */
  trait Message {
    def refs: Iterable[ActorRef[Nothing]]
  }

  /**
   * Helper trait to indicate that a message doesn't contain any references.
   */
  trait NoRefs extends Message {
    override def refs: Iterable[ActorRef[Nothing]] = Nil
  }

  type ActorRef[-T <: Message] = protocol.Refob[T]

  type Behavior[T <: Message] = raw.Behavior[protocol.GCMessage[T]]

  type ActorName = raw.ActorRef[Nothing]

  /**
   * A recipe for spawning a garbage-collected actor. Similar to
   * [[AkkaBehavior]], but this recipe can only be used by *GC-aware* actors,
   * i.e. a root actor or another garbage-collected actor.
   */
  type ActorFactory[T <: Message] = protocol.SpawnInfo => Behavior[T]

  object raw {
    import akka.actor.typed
    import akka.actor.typed.scaladsl
    type Ref = typed.ActorRef[Nothing]
    type ActorRef[-T] = typed.ActorRef[T]
    type Behavior[T] = typed.Behavior[T]
    type ActorContext[T] = scaladsl.ActorContext[T]
    type AbstractBehavior[T] = scaladsl.AbstractBehavior[T]
    val Behaviors = scaladsl.Behaviors
  }
}
