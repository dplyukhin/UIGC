package edu.illinois.osl

import edu.illinois.osl.uigc.interfaces._

package object uigc {

  type ActorRef[-T] = Refob[T]

  type Behavior[T] = unmanaged.Behavior[GCMessage[T]]

  type ActorName = unmanaged.ActorRef[Nothing]

  /** A recipe for spawning a garbage-collected actor. Similar to [[Behavior]], but this recipe can
    * only be used by *GC-aware* actors,
    * i.e. a root actor or another garbage-collected actor.
    */
  type ActorFactory[T] = SpawnInfo => Behavior[T]

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
        info: SpawnInfo,
        replyTo: unmanaged.ActorRef[unmanaged.ActorRef[GCMessage[T]]]
    ) extends Command[T]

    def apply[T](
        factories: Map[String, ActorContext[T] => Behavior[T]]
    ): unmanaged.Behavior[Command[T]] =
      unmanaged.Behaviors.receive { (ctx, msg) =>
        msg match {
          case Spawn(key, info, replyTo) =>
            val refob = ctx.spawnAnonymous(Behaviors.setup(factories(key))(info))
            replyTo ! refob
            unmanaged.Behaviors.same
        }
      }
  }
}
