package org.crewco.common

import org.bukkit.Bukkit
import org.bukkit.plugin.Plugin
import java.util.logging.Logger

object PluginReloader {

    private val logger: Logger = Bukkit.getLogger()

    fun reloadPlugin(pluginName: String): Boolean {
        val plugin = Bukkit.getPluginManager().getPlugin(pluginName)

        if (plugin == null) {
            logger.severe("[Updater] Plugin '$pluginName' not found")
            return false
        }

        return reloadPlugin(plugin)
    }

    fun reloadPlugin(plugin: Plugin): Boolean {
        return runCatching {
            val pluginManager = Bukkit.getPluginManager()

            logger.info("[Updater] Disabling ${plugin.name}...")
            pluginManager.disablePlugin(plugin)

            logger.info("[Updater] Enabling ${plugin.name}...")
            pluginManager.enablePlugin(plugin)

            logger.info("[Updater] Plugin ${plugin.name} reloaded successfully")
            true
        }.getOrElse { exception ->
            logger.severe("[Updater] Failed to reload ${plugin.name}: ${exception.message}")
            false
        }
    }
}