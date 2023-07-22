package edu.illinois.osl.akka

import edu.illinois.osl.akka.gc.unmanaged
import edu.illinois.osl.akka.gc.protocols._
import edu.illinois.osl.akka.gc.interfaces._

package object gc {

  val protocol: Protocol =
    System.getProperty("gc.protocol") match {
      case "NoProtocol" => protocols.NoProtocol
      case "Monotone" => protocols.monotone.Monotone
      case "wrc" => protocols.wrc.WRC
    }

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
    val Behaviors: scaladsl.Behaviors.type = scaladsl.Behaviors
  }

  object RemoteSpawner {
    trait Command[T] extends Serializable
    case class Spawn[T](
      factory: String,
      info: protocol.SpawnInfo,
      replyTo: unmanaged.ActorRef[unmanaged.ActorRef[protocol.GCMessage[T]]]
    ) extends Command[T]

    def apply[T](factories: Map[String, ActorContext[T] => Behavior[T]]): unmanaged.Behavior[Command[T]] =
      unmanaged.Behaviors.receive { (ctx, msg) => msg match {
        case Spawn(key, info, replyTo) =>
          val refob = ctx.spawnAnonymous(Behaviors.setup(factories(key))(info))
          replyTo ! refob
          unmanaged.Behaviors.same
      }}
  }
}
