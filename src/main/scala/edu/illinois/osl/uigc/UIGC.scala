package edu.illinois.osl.uigc

import akka.actor.{ActorSystem, ClassicActorSystemProvider, ExtendedActorSystem, ExtensionId, ExtensionIdProvider}
import edu.illinois.osl.uigc.engines.crgc.CRGC
import edu.illinois.osl.uigc.engines.mac.MAC
import edu.illinois.osl.uigc.engines.{Engine, Manual}

/** The UIGC system extension. */
object UIGC extends ExtensionId[Engine] with ExtensionIdProvider {
  override def lookup: UIGC.type = UIGC

  def createExtension(system: ExtendedActorSystem): Engine = {
    val config = system.settings.config
    config.getString("uigc.engine") match {
      case "crgc"   => new CRGC(system)
      case "mac"    => new MAC(system)
      case "manual" => new Manual(system)
    }
  }

  override def get(system: ActorSystem): Engine = super.get(system)

  override def get(system: ClassicActorSystemProvider): Engine = super.get(system)
}
