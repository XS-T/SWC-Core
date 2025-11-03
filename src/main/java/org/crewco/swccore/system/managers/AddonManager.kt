package org.crewco.swccore.system.managers

import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.plugin.Plugin
import org.crewco.swccore.Startup
import org.crewco.swccore.api.addon.Addon
import java.io.File
import java.io.InputStreamReader
import java.net.URLClassLoader
import java.util.jar.JarFile

/**
 * Manages the loading, enabling, disabling, and unloading of addons.
 */
class AddonManager(private val plugin: Startup) {

    private val addons = mutableMapOf<String, Addon>()
    private val addonFiles = mutableMapOf<String, File>()
    private val failedAddons = mutableMapOf<String, String>() // filename -> error message
    private val addonStates = mutableMapOf<String, AddonState>() // addon id -> state
    private var addonsDirectory: File? = null

    enum class AddonState {
        UNLOADED,  // JAR exists but not loaded yet
        LOADED,    // Loaded but not enabled
        ENABLED,   // Fully enabled
        DISABLED,  // Was enabled, now disabled
        FAILED     // Failed to load/enable
    }

    data class AddonInfo(
        val name: String,
        val version: String,
        val id: String,
        val state: AddonState,
        val fileName: String? = null,
        val errorMessage: String? = null,
        val description: String? = null,
        val authors: List<String>? = null
    )

    /**
     * Get all registered addons
     */
    fun getAddons(): Collection<Addon> = addons.values

    /**
     * Get an addon by its ID
     */
    fun getAddon(id: String): Addon? = addons[id]

    /**
     * Check if an addon is loaded
     */
    fun isAddonLoaded(id: String): Boolean = addons.containsKey(id)

    /**
     * Get the state of an addon
     */
    fun getAddonState(id: String): AddonState? = addonStates[id]

    /**
     * Scan for new addons in the directory
     */
    fun scanForNewAddons(): List<File> {
        val directory = addonsDirectory ?: return emptyList()

        if (!directory.exists() || !directory.isDirectory) {
            return emptyList()
        }

        val jarFiles = directory.listFiles { file ->
            file.isFile && file.extension.equals("jar", ignoreCase = true)
        } ?: return emptyList()

        // Find JARs that haven't been loaded yet
        val loadedFiles = addonFiles.values.toSet()
        val failedFiles = failedAddons.keys.map { File(directory, it) }.toSet()

        return jarFiles.filter { it !in loadedFiles && it !in failedFiles }
    }

    /**
     * Get all addon information including unloaded and failed ones
     */
    fun getAllAddonInfo(): List<AddonInfo> {
        val infoList = mutableListOf<AddonInfo>()

        // Add registered addons
        addons.values.forEach { addon ->
            infoList.add(
                AddonInfo(
                    name = addon.name,
                    version = addon.version,
                    id = addon.id,
                    state = addonStates[addon.id] ?: AddonState.LOADED,
                    fileName = addonFiles[addon.id]?.name,
                    description = addon.description,
                    authors = addon.authors
                )
            )
        }

        // Add failed addons
        failedAddons.forEach { (fileName, error) ->
            infoList.add(
                AddonInfo(
                    name = fileName.removeSuffix(".jar"),
                    version = "Unknown",
                    id = "unknown",
                    state = AddonState.FAILED,
                    fileName = fileName,
                    errorMessage = error
                )
            )
        }

        // Add unloaded addons (new JARs in the folder)
        val newAddons = scanForNewAddons()
        newAddons.forEach { file ->
            // Try to read basic info from manifest
            try {
                val jarFile = JarFile(file)
                val manifestEntry = jarFile.getEntry("manifest.yml")

                if (manifestEntry != null) {
                    val manifestStream = jarFile.getInputStream(manifestEntry)
                    val manifestConfig = YamlConfiguration.loadConfiguration(InputStreamReader(manifestStream))

                    val name = manifestConfig.getString("name") ?: file.nameWithoutExtension
                    val version = manifestConfig.getString("version") ?: "Unknown"
                    val description = manifestConfig.getString("description")
                    val authors = manifestConfig.getStringList("authors").takeIf { it.isNotEmpty() }
                        ?: listOf(manifestConfig.getString("author") ?: "Unknown")

                    infoList.add(
                        AddonInfo(
                            name = name,
                            version = version,
                            id = "unloaded-${file.name}",
                            state = AddonState.UNLOADED,
                            fileName = file.name,
                            description = description,
                            authors = authors
                        )
                    )
                    jarFile.close()
                } else {
                    infoList.add(
                        AddonInfo(
                            name = file.nameWithoutExtension,
                            version = "Unknown",
                            id = "unloaded-${file.name}",
                            state = AddonState.UNLOADED,
                            fileName = file.name,
                            errorMessage = "No manifest.yml found"
                        )
                    )
                }
            } catch (e: Exception) {
                infoList.add(
                    AddonInfo(
                        name = file.nameWithoutExtension,
                        version = "Unknown",
                        id = "unloaded-${file.name}",
                        state = AddonState.UNLOADED,
                        fileName = file.name,
                        errorMessage = "Failed to read: ${e.message}"
                    )
                )
            }
        }

        return infoList.sortedBy { it.state.ordinal }
    }

    /**
     * Load a specific addon by filename
     */
    fun loadAddonByFilename(filename: String): Boolean {
        val directory = addonsDirectory ?: return false
        val file = File(directory, filename)

        if (!file.exists() || !file.isFile) {
            plugin.logger.warning("Addon file not found: $filename")
            return false
        }

        // Check if already loaded
        if (addonFiles.values.any { it.name == filename }) {
            plugin.logger.warning("Addon already loaded: $filename")
            return false
        }

        // Remove from failed list if it was there
        failedAddons.remove(filename)

        // Load it
        loadAddonFromJar(file)

        return addonFiles.values.any { it.name == filename }
    }

    /**
     * Register an addon manually (useful for internal addons)
     */
    fun registerAddon(addon: Addon): Boolean {
        if (addons.containsKey(addon.id)) {
            plugin.logger.warning("Addon ${addon.id} is already registered!")
            return false
        }

        // Check addon dependencies
        for (dependency in addon.dependencies) {
            if (!isAddonLoaded(dependency)) {
                plugin.logger.severe("Cannot load ${addon.id}: missing addon '$dependency'")
                return false
            }
        }

        // Check plugin dependencies
        for (pluginDep in addon.pluginDependencies) {
            val depPlugin = plugin.server.pluginManager.getPlugin(pluginDep)
            if (depPlugin == null || !depPlugin.isEnabled) {
                plugin.logger.severe("Cannot load ${addon.id}: missing plugin '$pluginDep'")
                return false
            }
        }

        try {
            addon.onLoad()
            addons[addon.id] = addon
            addonStates[addon.id] = AddonState.LOADED
            plugin.logger.info("Registered addon: ${addon.name} v${addon.version} by ${addon.authors.joinToString(", ")}")
            return true
        } catch (e: Exception) {
            plugin.logger.severe("Failed to load addon ${addon.id}: ${e.message}")
            e.printStackTrace()
            addonStates[addon.id] = AddonState.FAILED
            return false
        }
    }

    /**
     * Load addons from a directory
     */
    fun loadAddonsFromDirectory(directory: File) {
        addonsDirectory = directory

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

            // First, try to find manifest.yml
            val manifestEntry = jarFileObj.getEntry("manifest.yml")

            if (manifestEntry == null) {
                val error = "does not have a manifest.yml file"
                plugin.logger.warning("${jarFile.name} $error")
                failedAddons[jarFile.name] = error
                return
            }

            // Read and parse manifest.yml
            val manifestStream = jarFileObj.getInputStream(manifestEntry)
            val manifestConfig = YamlConfiguration.loadConfiguration(InputStreamReader(manifestStream))

            val mainClass = manifestConfig.getString("main")
            if (mainClass == null) {
                val error = "manifest.yml does not have a 'main' entry"
                plugin.logger.warning("${jarFile.name} $error")
                failedAddons[jarFile.name] = error
                return
            }

            val classLoader = URLClassLoader(arrayOf(jarFile.toURI().toURL()), plugin::class.java.classLoader)
            val addonClass = Class.forName(mainClass, true, classLoader)

            if (!Addon::class.java.isAssignableFrom(addonClass)) {
                val error = "Main class $mainClass does not implement Addon interface"
                plugin.logger.warning("${jarFile.name}: $error")
                failedAddons[jarFile.name] = error
                return
            }

            // Try to find a constructor that takes Plugin parameter
            val constructor = try {
                addonClass.getConstructor(Plugin::class.java)
            } catch (e: NoSuchMethodException) {
                val error = "Addon main class must have a constructor that takes Plugin as parameter: $mainClass"
                plugin.logger.severe("${jarFile.name}: $error")
                failedAddons[jarFile.name] = error
                return
            }

            val addon = constructor.newInstance(plugin) as Addon

            if (registerAddon(addon)) {
                addonFiles[addon.id] = jarFile
            } else {
                failedAddons[jarFile.name] = "Failed to register addon"
            }

        } catch (e: Exception) {
            val error = e.message ?: "Unknown error"
            plugin.logger.severe("Failed to load addon from ${jarFile.name}: $error")
            e.printStackTrace()
            failedAddons[jarFile.name] = error
        }
    }

    /**
     * Enable all loaded addons
     */
    fun enableAddons() {
        for (addon in addons.values) {
            try {
                addon.onEnable()
                addonStates[addon.id] = AddonState.ENABLED
                plugin.logger.info("Enabled addon: ${addon.name}")
            } catch (e: Exception) {
                plugin.logger.severe("Failed to enable addon ${addon.id}: ${e.message}")
                e.printStackTrace()
                addonStates[addon.id] = AddonState.FAILED
            }
        }
    }

    /**
     * Enable a specific addon
     */
    fun enableAddon(id: String): Boolean {
        val addon = addons[id] ?: return false

        if (addonStates[id] == AddonState.ENABLED) {
            plugin.logger.warning("Addon $id is already enabled")
            return false
        }

        return try {
            addon.onEnable()
            addonStates[id] = AddonState.ENABLED
            plugin.logger.info("Enabled addon: ${addon.name}")
            true
        } catch (e: Exception) {
            plugin.logger.severe("Failed to enable addon $id: ${e.message}")
            e.printStackTrace()
            addonStates[id] = AddonState.FAILED
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
                addonStates[addon.id] = AddonState.DISABLED
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

        if (addonStates[id] == AddonState.DISABLED) {
            plugin.logger.warning("Addon $id is already disabled")
            return false
        }

        return try {
            addon.onDisable()
            addonStates[id] = AddonState.DISABLED
            plugin.logger.info("Disabled addon: ${addon.name}")
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

    /**
     * Unload a specific addon completely
     * This disables it and removes it from memory
     */
    fun unloadAddon(id: String): Boolean {
        val addon = addons[id] ?: return false

        return try {
            // Disable first if enabled
            if (addonStates[id] == AddonState.ENABLED) {
                addon.onDisable()
            }

            // Remove from tracking
            addons.remove(id)
            addonStates.remove(id)
            val file = addonFiles.remove(id)

            plugin.logger.info("Unloaded addon: ${addon.name}")
            true
        } catch (e: Exception) {
            plugin.logger.severe("Failed to unload addon $id: ${e.message}")
            e.printStackTrace()
            false
        }
    }
}