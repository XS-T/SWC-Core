package org.crewco.swccore.api.addon

import org.bukkit.command.CommandExecutor
import org.bukkit.command.TabCompleter
import org.bukkit.event.Listener
import org.bukkit.plugin.Plugin
import org.crewco.swccore.system.managers.CommandManager
import org.crewco.swccore.system.managers.subclasses.SimpleCommand
import java.io.File
import java.util.logging.Logger

/**
 * Abstract base class that provides common functionality for addons.
 * Addon developers can extend this class for convenience.
 */
abstract class AbstractAddon(override val plugin: Plugin) : Addon {

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
}