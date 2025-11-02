package org.crewco.common

import org.crewco.swccore.Startup
import java.io.*
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.atomic.AtomicBoolean

object Updater {

    private const val VERSION_URL = "https://git.crewco.org/api/v1/repos/XST99/SWC-Core/releases/latest"
    private const val DOWNLOAD_BASE = "https://git.crewco.org/XST99/SWC-Core/releases/download"
    private const val JAR_NAME = "SWC-Core"

    private val isUpdated = AtomicBoolean(false)

    fun checkAndUpdate(plugin: Startup) {
        if (isUpdated.get()) {
            plugin.logger.info("[Updater] Already on latest version: ${plugin.description.version}")
            return
        }

        val initialVersion = plugin.description.version

        Thread {
            runCatching {
                plugin.logger.info("[Updater] Checking for updates...")

                val latestVersion = fetchLatestVersion() ?: run {
                    plugin.logger.warning("[Updater] Failed to fetch latest version")
                    return@Thread
                }

                plugin.logger.info("[Updater] Latest version: $latestVersion | Current version: $initialVersion")

                if (latestVersion == initialVersion) {
                    plugin.logger.info("[Updater] Already up to date")
                    isUpdated.set(true)
                    return@Thread
                }

                downloadAndInstallUpdate(plugin, initialVersion, latestVersion)
            }.onFailure { exception ->
                plugin.logger.severe("[Updater] Update failed: ${exception.message}")
                exception.printStackTrace()
            }
        }.start()
    }

    private fun fetchLatestVersion(): String? {
        val connection = URL(VERSION_URL).openConnection() as HttpURLConnection
        connection.setRequestProperty("Accept", "application/json")

        return connection.inputStream.bufferedReader().use { reader ->
            val response = reader.readText()
            Regex("\"tag_name\"\\s*:\\s*\"([^\"]+)\"")
                .find(response)
                ?.groupValues
                ?.get(1)
        }
    }

    private fun downloadAndInstallUpdate(plugin: Startup, currentVersion: String, latestVersion: String) {
        val downloadUrl = "$DOWNLOAD_BASE/$latestVersion/$JAR_NAME-$latestVersion.jar"
        val pluginsFolder = plugin.dataFolder.parentFile
        val currentJar = File(pluginsFolder, "$JAR_NAME-$currentVersion.jar")
        val newJar = File(pluginsFolder, "$JAR_NAME-$latestVersion.jar")

        plugin.logger.info("[Updater] Downloading from: $downloadUrl")

        // Download new version
        URL(downloadUrl).openStream().use { input ->
            FileOutputStream(newJar).use { output ->
                input.copyTo(output)
            }
        }

        plugin.logger.info("[Updater] Download complete. Scheduling plugin reload...")
        isUpdated.set(true)

        // Schedule reload on main thread
        plugin.server.scheduler.runTask(plugin, Runnable {
            if (plugin.description.version != currentVersion) {
                plugin.logger.warning("[Updater] Version mismatch detected, aborting reload")
                return@Runnable
            }
            reloadPlugin(plugin, currentJar, newJar)
        })
    }

    private fun reloadPlugin(plugin: Startup, currentJar: File, newJar: File) {
        runCatching {
            val pluginManager = plugin.server.pluginManager
            val pluginName = plugin.name

            plugin.logger.info("[Updater] Disabling $pluginName...")
            pluginManager.disablePlugin(plugin)

            // Backup current JAR
            if (currentJar.exists()) {
                val backupJar = File(currentJar.parent, "${currentJar.name}.bak")
                currentJar.copyTo(backupJar, overwrite = true)
                plugin.logger.info("[Updater] Backed up current JAR")

                currentJar.delete()
            }

            // Verify new JAR exists
            if (!newJar.exists()) {
                plugin.logger.severe("[Updater] Updated JAR not found at ${newJar.path}")
                return
            }

            plugin.logger.info("[Updater] Installed new version, enabling plugin...")

            // Re-enable plugin (Bukkit will load the new JAR)
            pluginManager.getPlugin(pluginName)?.let { reloadedPlugin ->
                pluginManager.enablePlugin(reloadedPlugin)
                plugin.logger.info("[Updater] Successfully reloaded to version ${reloadedPlugin.description.version}")
            } ?: plugin.logger.severe("[Updater] Failed to find plugin after reload")

        }.onFailure { exception ->
            plugin.logger.severe("[Updater] Reload failed: ${exception.message}")
            exception.printStackTrace()
        }
    }

    fun isUpdated(): Boolean = isUpdated.get()
}