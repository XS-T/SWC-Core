package org.crewco.swcTowny.sysCommon

import org.bukkit.plugin.java.JavaPlugin
import org.crewco.swcTowny.Startup
import java.io.*
import java.net.HttpURLConnection
import java.net.URL

object Updater {

    private const val VERSION_URL = "https://git.crewco.org/api/v1/repos/crewco-admin/SWC-TownyHook/releases/latest"
    private const val DOWNLOAD_BASE = "https://git.crewco.org/crewco-admin/SWC-TownyHook/releases/download"
    private var isUpdated: Boolean = false

    fun checkAndUpdate(plugin: Startup) {
        val initialVersion = plugin.description.version

        Thread {
            try {
                if (isUpdated){
                    plugin.logger.info("[Updater]: Version ${plugin.description.version}")
                    return@Thread
                }
                plugin.logger.info("[Updater] Checking for updates...")

                val connection = URL(VERSION_URL).openConnection() as HttpURLConnection
                connection.setRequestProperty("Accept", "application/json")
                val reader = BufferedReader(InputStreamReader(connection.inputStream))
                val response = reader.readText()
                reader.close()

                val versionRegex = Regex("\"tag_name\"\\s*:\\s*\"([^\"]+)\"")
                val match = versionRegex.find(response)
                val latestVersion = match?.groupValues?.get(1) ?: return@Thread

                plugin.logger.info("[Updater] Latest version found: $latestVersion")

                val currentVersion = plugin.description.version
                if (latestVersion == currentVersion) {
                    plugin.logger.info("[Updater] Plugin was already updated to $currentVersion, skipping download.")
                    isUpdated = true
                    return@Thread
                }

                val downloadUrl = "$DOWNLOAD_BASE/$latestVersion/SWCTownyHook-${latestVersion}.jar"
                plugin.logger.info("[Updater] Download URL: $downloadUrl")
                plugin.logger.info("[Updater] New version detected! Downloading update...")

                val pluginsFolder = plugin.dataFolder.parentFile
                val finalJar = File(pluginsFolder, "SWCTownyHook-$currentVersion.jar")
                val tempJar = File(pluginsFolder, "SWCTownyHook-$latestVersion.jar")

                // Download new jar
                URL(downloadUrl).openStream().use { input -> FileOutputStream(tempJar).use { output -> input.copyTo(output)}}

                plugin.logger.info("[Updater] Download complete. Scheduling plugin reload...")

                isUpdated = true

                plugin.server.scheduler.runTask(plugin, Runnable {
                    val versionNow = plugin.description.version
                    if (versionNow != initialVersion) {
                        plugin.logger.warning("[Updater] Detected version change during update process ($versionNow ≠ $initialVersion), skipping reload.")
                        return@Runnable
                    }
                    safeReloadAfterUpdate(plugin, tempJar, finalJar)
                }

                )} catch (e: Exception) {
                    plugin.logger.severe("[Updater] Failed to check/download update: ${e.message}");e.printStackTrace()
                }
        }.start()
    }

    private fun safeReloadAfterUpdate(plugin: Startup, tempJar: File, finalJar: File) {
        try {
            val pluginManager = plugin.server.pluginManager
            val pluginName = plugin.name

            plugin.logger.info("[Updater] Disabling plugin: $pluginName")
            pluginManager.disablePlugin(plugin)

            val backupJar = File(finalJar.parent, finalJar.name + ".bak")


            if (finalJar.exists()) {
                finalJar.copyTo(backupJar, overwrite = true)
                plugin.logger.info("[Updater] Backed up current JAR to ${backupJar.name}")
            }

            if (tempJar.exists()) {
                if (finalJar.exists()) finalJar.delete()
                //tempJar.renameTo(finalJar)
                plugin.logger.info("[Updater] Replaced plugin JAR with updated version.")
                isUpdated = true
            } else {
                plugin.logger.warning("[Updater] Updated JAR not found!")
                return
            }

            val newPlugin = pluginManager.getPlugin(pluginName)
            pluginManager.enablePlugin(newPlugin)

            plugin.logger.info("[Updater] Plugin reloaded successfully.")
            backupJar.delete()

        } catch (e: Exception) {
            plugin.logger.severe("[Updater] Reload failed: ${e.message}")
            e.printStackTrace()
        }
    }
    fun isUpdated(): Boolean{
        return isUpdated
    }
}
