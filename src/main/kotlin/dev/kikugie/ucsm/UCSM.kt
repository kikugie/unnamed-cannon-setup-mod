package dev.kikugie.ucsm

import dev.kikugie.ucsm.cannon.CannonInstance
import dev.kikugie.ucsm.command.ModCommand
import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback
import net.fabricmc.loader.api.FabricLoader
import org.slf4j.LoggerFactory
object UCSM : ClientModInitializer {
    val LOGGER = LoggerFactory.getLogger("ECSM")
    val CONFIG = FabricLoader.getInstance().configDir.resolve("ucsm")
    var cannon: CannonInstance? = null
    override fun onInitializeClient() {
        ClientCommandRegistrationCallback.EVENT.register(ModCommand::register)
    }
}