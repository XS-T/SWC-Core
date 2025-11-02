package org.crewco.swccore.api.addon

import org.bukkit.configuration.file.FileConfiguration
import org.bukkit.configuration.file.YamlConfiguration
import java.io.File

/**
 * Helper class for managing addon configurations
 */
class AddonConfig(private val addon: Addon) {

    private val configFile: File by lazy {
        File((addon as? AbstractAddon)?.dataFolder ?: File("addons/${addon.id}"), "config.yml")
    }

    private var config: FileConfiguration? = null

    /**
     * Load or reload the configuration
     */
    fun load() {
        if (!configFile.exists()) {
            configFile.parentFile.mkdirs()
            configFile.createNewFile()

            // Save default config if available
            saveDefaults()
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
     * Override this to provide default configuration values
     */
    protected open fun saveDefaults() {
        // Addon developers can override this
        val defaults = getDefaults()
        if (defaults.isNotEmpty()) {
            val cfg = get()
            defaults.forEach { (key, value) ->
                if (!cfg.contains(key)) {
                    cfg.set(key, value)
                }
            }
            save()
        }
    }

    /**
     * Override this to provide default configuration
     */
    protected open fun getDefaults(): Map<String, Any> = emptyMap()

    /**
     * Helper method to get a string from config
     */
    fun getString(path: String, default: String = ""): String {
        return get().getString(path, default) ?: default
    }

    /**
     * Helper method to get an int from config
     */
    fun getInt(path: String, default: Int = 0): Int {
        return get().getInt(path, default)
    }

    /**
     * Helper method to get a boolean from config
     */
    fun getBoolean(path: String, default: Boolean = false): Boolean {
        return get().getBoolean(path, default)
    }

    /**
     * Helper method to get a list from config
     */
    fun getStringList(path: String): List<String> {
        return get().getStringList(path)
    }

    /**
     * Helper method to set a value in config
     */
    fun set(path: String, value: Any?) {
        get().set(path, value)
    }
}