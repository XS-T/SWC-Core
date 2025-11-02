package org.crewco.swcTowny.sysCommon

import org.bukkit.Bukkit

class PluginReloader {
    fun reloadPluginByName(pluginName: String) {
        val pluginManager = Bukkit.getPluginManager()
        val plugin = pluginManager.getPlugin(pluginName)
        if (plugin != null) {
            Bukkit.getLogger().info("[Updater] Disabling plugin: $pluginName")
            pluginManager.disablePlugin(plugin)

            Bukkit.getLogger().info("[Updater] Enabling plugin: $pluginName")
            pluginManager.enablePlugin(plugin)

            Bukkit.getLogger().info("[Updater] Plugin reloaded successfully.")
        } else {
            Bukkit.getLogger().severe("[Updater] Plugin $pluginName not found.")
        }
    }
}