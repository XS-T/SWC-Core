package org.crewco.swccore.api.addon

import org.bukkit.configuration.file.FileConfiguration
import org.bukkit.configuration.file.YamlConfiguration
import java.io.File
import java.io.InputStream

/**
 * Helper class for managing addon configurations
 * Automatically copies config.yml from addon JAR to data folder
 */
open class AddonConfig(private val addon: Addon) {

    private val configFile: File by lazy {
        File((addon as? AbstractAddon)?.dataFolder ?: File("addons/${addon.id}"), "config.yml")
    }

    private var config: FileConfiguration? = null

    /**
     * Load or reload the configuration
     * Automatically copies default config from resources if file doesn't exist
     */
    fun load() {
        // Ensure parent directory exists
        configFile.parentFile.mkdirs()

        // If config doesn't exist, copy from resources
        if (!configFile.exists()) {
            saveDefaultConfig()
        }

        config = YamlConfiguration.loadConfiguration(configFile)
    }

    /**
     * Save the configuration to disk
     */
    fun save() {
        config?.save(configFile)
    }

    /**
     * Get the file configuration
     */
    fun get(): FileConfiguration {
        if (config == null) {
            load()
        }
        return config!!
    }

    /**
     * Reload the configuration from disk
     */
    fun reload() {
        config = YamlConfiguration.loadConfiguration(configFile)
    }

    /**
     * Copy the default config.yml from the addon's JAR resources to the data folder
     */
    fun saveDefaultConfig() {
        try {
            // Get the addon's classloader
            val classLoader = addon.javaClass.classLoader

            // Try to get config.yml from resources
            val resourceStream: InputStream? = classLoader.getResourceAsStream("config.yml")

            if (resourceStream != null) {
                // Copy from resources to data folder
                configFile.outputStream().use { output ->
                    resourceStream.copyTo(output)
                }

                if (addon is AbstractAddon) {
                    addon.logInfo("Generated config.yml from default template")
                }
            } else {
                // No default config in resources, generate from code defaults
                generateDefaultConfig()
            }
        } catch (e: Exception) {
            if (addon is AbstractAddon) {
                addon.logError("Failed to save default config: ${e.message}", e)
            }

            // Fallback to code-generated defaults
            generateDefaultConfig()
        }
    }

    /**
     * Generate a config file from code-defined defaults
     * Used as fallback if no config.yml exists in resources
     */
    private fun generateDefaultConfig() {
        val defaults = getDefaults()

        if (defaults.isEmpty()) {
            // Create empty config file
            configFile.createNewFile()
            if (addon is AbstractAddon) {
                addon.logInfo("Created empty config.yml")
            }
            return
        }

        // Create new config with defaults
        val newConfig = YamlConfiguration()

        // Apply all default values
        defaults.forEach { (key, value) ->
            newConfig.set(key, value)
        }

        // Save to file
        newConfig.save(configFile)

        if (addon is AbstractAddon) {
            addon.logInfo("Generated config.yml with ${defaults.size} default entries")
        }
    }

    /**
     * Add missing default values to existing config
     * Useful for adding new config options without overwriting existing ones
     */
    fun addMissingDefaults() {
        // First try to load defaults from resource
        val classLoader = addon.javaClass.classLoader
        val resourceStream: InputStream? = classLoader.getResourceAsStream("config.yml")

        if (resourceStream != null) {
            val defaultConfig = YamlConfiguration.loadConfiguration(resourceStream.reader())
            val currentConfig = get()
            var added = 0

            defaultConfig.getKeys(true).forEach { key ->
                if (!currentConfig.contains(key) && !defaultConfig.isConfigurationSection(key)) {
                    currentConfig.set(key, defaultConfig.get(key))
                    added++
                }
            }

            if (added > 0) {
                save()
                if (addon is AbstractAddon) {
                    addon.logInfo("Added $added missing default config values")
                }
            }
        } else {
            // Fallback to code defaults
            val defaults = getDefaults()
            if (defaults.isEmpty()) return

            val cfg = get()
            var added = 0

            defaults.forEach { (key, value) ->
                if (!cfg.contains(key)) {
                    cfg.set(key, value)
                    added++
                }
            }

            if (added > 0) {
                save()
                if (addon is AbstractAddon) {
                    addon.logInfo("Added $added missing default config values")
                }
            }
        }
    }

    /**
     * Override this method to provide fallback default configuration values
     * Only used if no config.yml exists in the JAR's resources folder
     *
     * Example:
     * ```
     * override fun getDefaults(): Map<String, Any> {
     *     return mapOf(
     *         "feature.enabled" to true,
     *         "feature.delay" to 20
     *     )
     * }
     * ```
     */
    protected open fun getDefaults(): Map<String, Any> = emptyMap()

    // ============= Getter Methods =============

    /**
     * Get a string from config
     */
    fun getString(path: String, default: String = ""): String {
        return get().getString(path, default) ?: default
    }

    /**
     * Get an int from config
     */
    fun getInt(path: String, default: Int = 0): Int {
        return get().getInt(path, default)
    }

    /**
     * Get a boolean from config
     */
    fun getBoolean(path: String, default: Boolean = false): Boolean {
        return get().getBoolean(path, default)
    }

    /**
     * Get a double from config
     */
    fun getDouble(path: String, default: Double = 0.0): Double {
        return get().getDouble(path, default)
    }

    /**
     * Get a long from config
     */
    fun getLong(path: String, default: Long = 0L): Long {
        return get().getLong(path, default)
    }

    /**
     * Get a float from config
     */
    fun getFloat(path: String, default: Float = 0f): Float {
        return get().getDouble(path, default.toDouble()).toFloat()
    }

    /**
     * Get a string list from config
     */
    fun getStringList(path: String): List<String> {
        return get().getStringList(path)
    }

    /**
     * Get an int list from config
     */
    fun getIntList(path: String): List<Int> {
        return get().getIntegerList(path)
    }

    /**
     * Get a double list from config
     */
    fun getDoubleList(path: String): List<Double> {
        return get().getDoubleList(path)
    }

    // ============= Setter Methods =============

    /**
     * Set a value in config
     */
    fun set(path: String, value: Any?) {
        get().set(path, value)
    }

    // ============= Utility Methods =============

    /**
     * Check if a path exists in config
     */
    fun contains(path: String): Boolean {
        return get().contains(path)
    }

    /**
     * Get all keys in config
     */
    fun getKeys(deep: Boolean = false): Set<String> {
        return get().getKeys(deep)
    }

    /**
     * Get a configuration section
     */
    fun getSection(path: String): org.bukkit.configuration.ConfigurationSection? {
        return get().getConfigurationSection(path)
    }

    /**
     * Reset config to defaults (deletes and regenerates from resources)
     */
    fun reset() {
        if (configFile.exists()) {
            configFile.delete()
        }
        config = null
        load()

        if (addon is AbstractAddon) {
            addon.logInfo("Config reset to defaults")
        }
    }
}