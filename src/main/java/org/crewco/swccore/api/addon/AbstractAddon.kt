package org.crewco.swccore.api.addon

import org.bukkit.command.CommandExecutor
import org.bukkit.command.TabCompleter
import org.bukkit.plugin.Plugin
import org.crewco.swccore.system.managers.CommandManager
import org.crewco.swccore.system.managers.SimpleCommand
import java.io.File
import java.util.logging.Logger

/**
 * Abstract base class that provides common functionality for addons.
 * Addon developers can extend this class for convenience.
 */
abstract class AbstractAddon(override val plugin: Plugin) : Addon {

    /**
     * Logger instance for this addon
     */
    protected val logger: Logger = Logger.getLogger(name)

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
     */
    protected val commandManager: CommandManager by lazy {
        (plugin as? SWC_Core)?.commandManager
            ?: throw IllegalStateException("Plugin does not provide CommandManager")
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
        registeredCommands.forEach { commandName ->
            commandManager.unregisterCommand(commandName)
        }
        registeredCommands.clear()

        isEnabled = false
    }

    override fun onReload() {
        logger.info("Reloading addon: $name")
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
        val success = commandManager.registerCommand(name, executor, description, usage, aliases, tabCompleter)
        if (success) {
            registeredCommands.add(name)
        }
        return success
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
    protected fun logInfo(message: String) {
        logger.info("[$name] $message")
    }

    /**
     * Helper method to log warning messages
     */
    protected fun logWarning(message: String) {
        logger.warning("[$name] $message")
    }

    /**
     * Helper method to log error messages
     */
    protected fun logError(message: String, throwable: Throwable? = null) {
        logger.severe("[$name] $message")
        throwable?.printStackTrace()
    }
}
