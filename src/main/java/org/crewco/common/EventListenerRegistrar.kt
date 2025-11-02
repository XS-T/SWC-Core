package org.crewco.common

import org.bukkit.event.Listener
import org.bukkit.plugin.java.JavaPlugin
import kotlin.reflect.KClass

object EventListenerRegistrar {

    private lateinit var plugin: JavaPlugin

    fun initialize(plugin: JavaPlugin) {
        this.plugin = plugin
    }

    fun registerListeners(vararg listenerClasses: KClass<out Listener>) {
        for (listenerClass in listenerClasses) {
            try {
                val instance = listenerClass.java.newInstance()
                if (instance is Listener) {
                    plugin.server.pluginManager.registerEvents(instance, plugin)
                } else {
                    plugin.logger.warning("Class $listenerClass does not implement Listener.")
                }
            } catch (e: Exception) {
                plugin.logger.warning("Error while registering listener: ${e.message}")
            }
        }
    }

    // You can add more methods for handling specific event registration if needed
}