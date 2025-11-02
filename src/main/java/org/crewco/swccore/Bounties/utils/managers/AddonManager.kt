package org.crewco.swccore.Bounties.utils.managers


import org.crewco.swccore.api.addon.BaseAddon
import org.crewco.swccore.Startup.Companion.plugin
import org.bukkit.plugin.Plugin
import java.io.File
import java.net.URLClassLoader
import java.util.jar.JarFile

/**
 * Manages the loading, enabling, disabling, and unloading of addons.
 */

class AddonManager {

    private val addons = mutableMapOf<String, BaseAddon>()
    private val addonFiles = mutableMapOf<String, File>()

    /**
     * Get all registered addons
     */
    fun getAddons(): Collection<BaseAddon> = addons.values

    /**
     * Get an addon by its ID
     */
    fun getAddon(id: String): BaseAddon? = addons[id]

    /**
     * Check if an addon is loaded
     */
    fun isAddonLoaded(id: String): Boolean = addons.containsKey(id)

    /**
     * Register an addon manually (useful for internal addons)
     */
    fun registerAddon(addon: BaseAddon): Boolean {
        if (addons.containsKey(addon.id)) {
            plugin.logger.warning("Addon ${addon.id} is already registered!")
            return false
        }

        // Check dependencies
        for (dependency in addon.dependencies) {
            if (!isAddonLoaded(dependency)) {
                plugin.logger.severe("Cannot load addon ${addon.id}: missing dependency $dependency")
                return false
            }
        }

        try {
            addon.onLoad()
            addons[addon.id] = addon
            plugin.logger.info("Registered addon: ${addon.name} v${addon.version} by ${addon.authors.joinToString(", ")}")
            return true
        } catch (e: Exception) {
            plugin.logger.severe("Failed to load addon ${addon.id}: ${e.message}")
            e.printStackTrace()
            return false
        }
    }

    /**
     * Load addons from a directory
     */
    fun loadAddonsFromDirectory(directory: File) {
        if (!directory.exists()) {
            directory.mkdirs()
            return
        }

        if (!directory.isDirectory) {
            plugin.logger.warning("${directory.path} is not a directory!")
            return
        }

        val jarFiles = directory.listFiles { file ->
            file.isFile && file.extension.equals("jar", ignoreCase = true)
        } ?: return

        plugin.logger.info("Found ${jarFiles.size} addon jar(s)")

        for (jarFile in jarFiles) {
            loadAddonFromJar(jarFile)
        }
    }

    /**
     * Load an addon from a JAR file
     */
    private fun loadAddonFromJar(jarFile: File) {
        try {
            val jarFileObj = JarFile(jarFile)
            val manifest = jarFileObj.manifest
            val mainClass = manifest?.mainAttributes?.getValue("Addon-Main")

            if (mainClass == null) {
                plugin.logger.warning("${jarFile.name} does not have an Addon-Main attribute in MANIFEST.MF")
                return
            }

            val classLoader = URLClassLoader(arrayOf(jarFile.toURI().toURL()), plugin::class.java.classLoader)
            val addonClass = Class.forName(mainClass, true, classLoader)

            if (!BaseAddon::class.java.isAssignableFrom(addonClass)) {
                plugin.logger.warning("Main class $mainClass in ${jarFile.name} does not implement Addon interface")
                return
            }

            // Try to find a constructor that takes Plugin parameter
            val constructor = try {
                addonClass.getConstructor(Plugin::class.java)
            } catch (e: NoSuchMethodException) {
                plugin.logger.severe("Addon main class must have a constructor that takes Plugin as parameter: $mainClass")
                return
            }

            val addon = constructor.newInstance(plugin) as BaseAddon

            if (registerAddon(addon)) {
                addonFiles[addon.id] = jarFile
            }

        } catch (e: Exception) {
            plugin.logger.severe("Failed to load addon from ${jarFile.name}: ${e.message}")
            e.printStackTrace()
        }
    }

    /**
     * Enable all loaded addons
     */
    fun enableAddons() {
        for (addon in addons.values) {
            try {
                addon.onEnable()
                plugin.logger.info("Enabled addon: ${addon.name}")
            } catch (e: Exception) {
                plugin.logger.severe("Failed to enable addon ${addon.id}: ${e.message}")
                e.printStackTrace()
            }
        }
    }

    /**
     * Enable a specific addon
     */
    fun enableAddon(id: String): Boolean {
        val addon = addons[id] ?: return false
        return try {
            addon.onEnable()
            true
        } catch (e: Exception) {
            plugin.logger.severe("Failed to enable addon $id: ${e.message}")
            e.printStackTrace()
            false
        }
    }

    /**
     * Disable all addons
     */
    fun disableAddons() {
        for (addon in addons.values.reversed()) {
            try {
                addon.onDisable()
                plugin.logger.info("Disabled addon: ${addon.name}")
            } catch (e: Exception) {
                plugin.logger.severe("Failed to disable addon ${addon.id}: ${e.message}")
                e.printStackTrace()
            }
        }
    }

    /**
     * Disable a specific addon
     */
    fun disableAddon(id: String): Boolean {
        val addon = addons[id] ?: return false
        return try {
            addon.onDisable()
            true
        } catch (e: Exception) {
            plugin.logger.severe("Failed to disable addon $id: ${e.message}")
            e.printStackTrace()
            false
        }
    }

    /**
     * Reload all addons
     */
    fun reloadAddons() {
        for (addon in addons.values) {
            try {
                addon.onReload()
            } catch (e: Exception) {
                plugin.logger.severe("Failed to reload addon ${addon.id}: ${e.message}")
                e.printStackTrace()
            }
        }
    }

    /**
     * Reload a specific addon
     */
    fun reloadAddon(id: String): Boolean {
        val addon = addons[id] ?: return false
        return try {
            addon.onReload()
            true
        } catch (e: Exception) {
            plugin.logger.severe("Failed to reload addon $id: ${e.message}")
            e.printStackTrace()
            false
        }
    }
}