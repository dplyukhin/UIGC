package edu.illinois.osl.akka

import edu.illinois.osl.akka.gc.unmanaged
import edu.illinois.osl.akka.gc.protocols._
import edu.illinois.osl.akka.gc.interfaces._

package object gc {

  val protocol: Protocol = drl.DRL

  object coerce {
    implicit def gcmessage1[T](msg: protocol.GCMessage[T]): drl.GCMessage[T] =
      msg.asInstanceOf[drl.GCMessage[T]]
    implicit def gcmessage2[T](msg: drl.GCMessage[T]): protocol.GCMessage[T] =
      msg.asInstanceOf[protocol.GCMessage[T]]
    implicit def reflike[T](ref: RefLike[protocol.GCMessage[T]]): RefLike[drl.GCMessage[T]] =
      ref.asInstanceOf[RefLike[drl.GCMessage[T]]]
    implicit def refob[T](ref: drl.Refob[T]): protocol.Refob[T] =
      ref.asInstanceOf[protocol.Refob[T]]
  }

  type ActorRef[-T] = protocol.Refob[T]

  type Behavior[T] = unmanaged.Behavior[protocol.GCMessage[T]]

  type ActorName = unmanaged.ActorRef[Nothing]

  /**
   * A recipe for spawning a garbage-collected actor. Similar to
   * [[AkkaBehavior]], but this recipe can only be used by *GC-aware* actors,
   * i.e. a root actor or another garbage-collected actor.
   */
  type ActorFactory[T] = protocol.SpawnInfo => Behavior[T]

  object unmanaged {
    import akka.actor.typed
    import akka.actor.typed.scaladsl
    type ActorRef[-T] = typed.ActorRef[T]
    type Behavior[T] = typed.Behavior[T]
    type ActorContext[T] = scaladsl.ActorContext[T]
  }
}
