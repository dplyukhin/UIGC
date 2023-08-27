package edu.illinois.osl

import edu.illinois.osl.uigc.engines._

package object uigc {

  val protocol: Engine =
    System.getProperty("gc.engine") match {
      case "manual" => engines.Manual
      case "crgc" => engines.crgc.CRGC
      case "wrc" => engines.WRC
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
