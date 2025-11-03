package org.crewco.swccore.api.addon

import net.milkbowl.vault.economy.Economy
import org.bukkit.command.CommandExecutor
import org.bukkit.command.TabCompleter
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.event.Listener
import org.bukkit.plugin.Plugin
import org.crewco.swccore.Startup
import org.crewco.swccore.system.managers.CommandManager
import org.crewco.swccore.system.managers.subclasses.SimpleCommand
import java.io.File
import java.io.InputStreamReader
import java.util.logging.Logger

/**
 * Abstract base class that provides common functionality for addons.
 * Addon developers can extend this class for convenience.
 * Automatically reads addon metadata from manifest.yml
 */
abstract class AbstractAddon(override val plugin: Plugin) : Addon {

    /**
     * Automatically load manifest.yml from the addon JAR
     */
    private val manifest: YamlConfiguration by lazy {
        try {
            val classLoader = this::class.java.classLoader
            val manifestStream = classLoader.getResourceAsStream("manifest.yml")
                ?: throw IllegalStateException("manifest.yml not found in addon JAR")

            YamlConfiguration.loadConfiguration(InputStreamReader(manifestStream))
        } catch (e: Exception) {
            throw IllegalStateException("Failed to load manifest.yml: ${e.message}", e)
        }
    }

    /**
     * Addon ID - must be provided by subclass
     * Can optionally be read from manifest.yml if specified there
     */
    override val id: String by lazy {
        manifest.getString("id") ?: generateIdFromName()
    }

    /**
     * Auto-populate name from manifest.yml
     */
    override val name: String by lazy {
        manifest.getString("name") ?: this::class.java.simpleName
    }

    /**
     * Auto-populate version from manifest.yml
     */
    override val version: String by lazy {
        manifest.getString("version") ?: "Unknown"
    }

    /**
     * Auto-populate description from manifest.yml
     */
    override val description: String by lazy {
        manifest.getString("description") ?: ""
    }

    /**
     * Auto-populate authors from manifest.yml
     * Supports both 'authors' list and single 'author' field
     */
    override val authors: List<String> by lazy {
        manifest.getStringList("authors").takeIf { it.isNotEmpty() }
            ?: listOf(manifest.getString("author") ?: "Unknown")
    }

    /**
     * Auto-populate dependencies from manifest.yml
     */
    override val dependencies: List<String> by lazy {
        manifest.getStringList("dependencies")
    }

    /**
     * Generate ID from name if not specified in manifest
     * Converts "My Addon Name" to "my-addon-name"
     */
    private fun generateIdFromName(): String {
        return manifest.getString("name")
            ?.lowercase()
            ?.replace(Regex("[^a-z0-9]+"), "-")
            ?.trim('-')
            ?: this::class.java.simpleName.lowercase()
    }

    /**
     * Logger instance for this addon
     * Lazily initialized to avoid NPE during construction
     */
    protected val logger: Logger by lazy {
        Logger.getLogger(name)
    }

    /**
     * Data folder for this addon's files
     */
    val dataFolder: File by lazy {
        File(plugin.dataFolder, "addons/$id").apply {
            if (!exists()) mkdirs()
        }
    }

    /**
     * Command manager for registering commands dynamically
     * Uses reflection to avoid classloader issues
     */
    protected val commandManager: CommandManager
        get() {
            return try {
                val field = plugin.javaClass.getDeclaredField("commandManager")
                field.isAccessible = true
                field.get(plugin) as CommandManager
            } catch (e: NoSuchFieldException) {
                throw IllegalStateException("Plugin does not have commandManager field", e)
            } catch (e: Exception) {
                throw IllegalStateException("Failed to access commandManager: ${e.message}", e)
            }
        }

    /**
     * Whether this addon is currently enabled
     */
    var isEnabled: Boolean = false
        private set

    private val registeredCommands = mutableListOf<String>()

    override fun onLoad() {
        logger.info("Loading addon: $name v$version")
    }

    override fun onEnable() {
        logger.info("Enabling addon: $name v$version")
        isEnabled = true
    }

    override fun onDisable() {
        logger.info("Disabling addon: $name v$version")

        // Unregister all commands registered by this addon
        try {
            registeredCommands.forEach { commandName ->
                commandManager.unregisterCommand(commandName)
            }
            registeredCommands.clear()
        } catch (e: Exception) {
            logger.warning("Failed to unregister commands: ${e.message}")
        }

        isEnabled = false
    }

    override fun onReload() {
        logger.info("Reloading addon: $name")
    }


    override val pluginDependencies: List<String> by lazy {
        manifest.getStringList("plugin-dependencies")
    }


    /**
     * Helper method to register multiple event listeners at once
     *
     * @param listeners Vararg of Listener instances to register
     */
    protected fun registerEvents(vararg listeners: Listener) {
        listeners.forEach { listener ->
            try {
                plugin.server.pluginManager.registerEvents(listener, plugin)
                logInfo("Registered listener: ${listener::class.java.simpleName}")
            } catch (e: Exception) {
                logError("Failed to register listener: ${listener::class.java.simpleName}", e)
            }
        }
    }

    /**
     * Helper method to register a command
     * Commands are automatically unregistered when the addon is disabled
     *
     * @param name Command name
     * @param executor Command executor
     * @param description Command description
     * @param usage Usage string
     * @param aliases List of aliases
     * @param tabCompleter Tab completer (optional)
     * @return true if registration was successful
     */
    protected fun registerCommand(
        name: String,
        executor: CommandExecutor,
        description: String = "",
        usage: String = "/$name",
        aliases: List<String> = emptyList(),
        tabCompleter: TabCompleter? = null
    ): Boolean {
        return try {
            val success = commandManager.registerCommand(name, executor, description, usage, aliases, tabCompleter)
            if (success) {
                registeredCommands.add(name)
                logInfo("Registered command: /$name")
            } else {
                logWarning("Failed to register command: /$name")
            }
            success
        } catch (e: Exception) {
            logError("Error registering command /$name", e)
            false
        }
    }

    /**
     * Helper method to register a simple command with a lambda
     */
    protected fun registerCommand(
        name: String,
        description: String = "",
        usage: String = "/$name",
        aliases: List<String> = emptyList(),
        tabCompleter: TabCompleter? = null,
        executor: (sender: org.bukkit.command.CommandSender, command: org.bukkit.command.Command, label: String, args: Array<out String>) -> Boolean
    ): Boolean {
        return registerCommand(name, SimpleCommand(executor), description, usage, aliases, tabCompleter)
    }

    /**
     * Helper method to log info messages
     */
    fun logInfo(message: String) {
        logger.info("[$name] $message")
    }

    /**
     * Helper method to log warning messages
     */
    fun logWarning(message: String) {
        logger.warning("[$name] $message")
    }

    /**
     * Helper method to log error messages
     */
    fun logError(message: String, throwable: Throwable? = null) {
        logger.severe("[$name] $message")
        throwable?.printStackTrace()
    }

    // Deps Handling
    protected val swcCore: Startup by lazy {
        plugin as Startup
    }

    // Plugin API getters with null safety

    /**
     * Get a plugin instance safely
     */
    protected fun <T : Plugin> getPluginDependency(pluginName: String, clazz: Class<T>): T? {
        val pluginInstance = plugin.server.pluginManager.getPlugin(pluginName)
        return if (pluginInstance != null && pluginInstance.isEnabled && clazz.isInstance(pluginInstance)) {
            clazz.cast(pluginInstance)
        } else {
            null
        }
    }

    /**
     * Check if a plugin dependency is loaded and enabled
     * This checks the actual server state, not the manifest
     */
    protected fun hasPluginDependency(addon: Plugin,pluginName: String): Boolean {
        val pluginInstance = plugin.server.pluginManager.getPlugin(pluginName)
        return pluginInstance != null && pluginInstance.isEnabled
    }

    /**
     * Get all available plugin dependencies
     * Returns list of plugins declared in manifest that are currently available
     */
    protected fun getAvailablePluginDependencies(addon:Plugin): List<String> {
        return pluginDependencies.filter { hasPluginDependency(addon,it) }
    }

    /**
     * Get all missing plugin dependencies
     * Returns list of plugins declared in manifest but not available on server
     */
    protected fun getMissingPluginDependencies(addon:Plugin): List<String> {
        return pluginDependencies.filter { !hasPluginDependency(addon,it) }
    }


    /**
     * Get PlaceholderAPI if available
     */
    protected fun getVaultAPI(addon:Plugin): Economy? {
        return if (hasPluginDependency(addon,"Vault")) {
            try {
                Economy::class.java.newInstance()
            } catch (e: Exception) {
                null
            }
        } else null
    }


}