package edu.illinois.osl.uigc

import akka.Done
import akka.actor.typed.{Dispatchers, Extension, ExtensionId, Settings}
import akka.actor.{Address, DynamicAccess, typed}
import com.typesafe.config.Config
import edu.illinois.osl.uigc.interfaces.GCMessage
import org.slf4j.Logger

import java.util.concurrent.{CompletionStage, ThreadFactory}
import scala.concurrent.{ExecutionContextExecutor, Future}

object ActorSystem {
  def apply[T](guardianBehavior: ActorFactory[T], name: String): ActorSystem[T] =
    ActorSystem(
      typed.ActorSystem(setup(guardianBehavior), name)
    )

  def apply[T](guardianBehavior: ActorFactory[T], name: String, config: Config): ActorSystem[T] =
    ActorSystem(
      typed.ActorSystem(setup(guardianBehavior), name, config)
    )

  private def setup[T](guardianBehavior: ActorFactory[T]) =
    unmanaged.Behaviors.setup[GCMessage[T]] { ctx =>
      guardianBehavior(UIGC(ctx.system).rootSpawnInfo())
    }
}

case class ActorSystem[S](typedSystem: typed.ActorSystem[Nothing]) {
  def name: String = typedSystem.name

  def settings: Settings = typedSystem.settings

  def logConfiguration(): Unit = typedSystem.logConfiguration()

  def log: Logger = typedSystem.log

  def startTime: Long = typedSystem.startTime

  def uptime: Long = typedSystem.uptime

  def threadFactory: ThreadFactory = typedSystem.threadFactory

  def dynamicAccess: DynamicAccess = typedSystem.dynamicAccess

  // def scheduler: Scheduler = typedSystem.scheduler

  def dispatchers: Dispatchers = typedSystem.dispatchers

  implicit def executionContext: ExecutionContextExecutor = typedSystem.executionContext

  def terminate(): Unit = typedSystem.terminate()

  def whenTerminated: Future[Done] = typedSystem.whenTerminated

  def getWhenTerminated: CompletionStage[Done] = typedSystem.getWhenTerminated

  // def deadLetters[U]: typed.ActorRef[U] = typedSystem.deadLetters

  // def ignoreRef[U]: typed.ActorRef[U] = typedSystem.ignoreRef

  def printTree: String = typedSystem.printTree

  // def systemActorOf[U](behavior: typed.Behavior[U], name: String, props: Props): typed.ActorRef[U] = ???

  def address: Address = typedSystem.address

  def registerExtension[T <: Extension](ext: ExtensionId[T]): T = typedSystem.registerExtension(ext)

  def extension[T <: Extension](ext: ExtensionId[T]): T = typedSystem.extension(ext)

  def hasExtension(ext: ExtensionId[_ <: Extension]): Boolean = typedSystem.hasExtension(ext)

  // def tell(msg: T): Unit = ???

  // def narrow[U <: T]: typed.ActorRef[U] = ???

  // def unsafeUpcast[U >: T]: typed.ActorRef[U] = ???

  // def path: ActorPath = ???

  // def compareTo(o: typed.ActorRef[_]): Int = ???

  // def receptionist: ActorRef[Command]
}
