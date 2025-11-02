package org.crewco.swccore.system.managers

import org.bukkit.command.*
import org.bukkit.plugin.Plugin
import org.bukkit.plugin.SimplePluginManager
import java.lang.reflect.Constructor
import java.lang.reflect.Field

/**
 * Manages dynamic command registration for addons.
 * Allows addons to register commands without needing them in plugin.yml
 */
class CommandManager(private val plugin: Plugin) {

    private val commandMap: CommandMap by lazy {
        val pluginManager = plugin.server.pluginManager
        val field: Field = SimplePluginManager::class.java.getDeclaredField("commandMap")
        field.isAccessible = true
        field.get(pluginManager) as CommandMap
    }

    private val registeredCommands = mutableMapOf<String, Command>()

    /**
     * Register a command dynamically
     *
     * @param name The command name
     * @param executor The command executor
     * @param description Optional command description
     * @param usage Optional usage string
     * @param aliases Optional list of aliases
     * @param tabCompleter Optional tab completer
     * @return true if registration was successful
     */
    fun registerCommand(
        name: String,
        executor: CommandExecutor,
        description: String = "",
        usage: String = "/$name",
        aliases: List<String> = emptyList(),
        tabCompleter: TabCompleter? = null
    ): Boolean {
        try {
            // Check if command already exists
            if (registeredCommands.containsKey(name.lowercase())) {
                plugin.logger.warning("Command '$name' is already registered!")
                return false
            }

            // Create a dynamic command
            val command = createPluginCommand(name, plugin)
            command.description = description
            command.usage = usage
            command.aliases = aliases
            command.setExecutor(executor)

            if (tabCompleter != null) {
                command.tabCompleter = tabCompleter
            }

            // Register the command
            commandMap.register(plugin.description.name.lowercase(), command)
            registeredCommands[name.lowercase()] = command

            return true
        } catch (e: Exception) {
            plugin.logger.severe("Failed to register command '$name': ${e.message}")
            e.printStackTrace()
            return false
        }
    }

    /**
     * Unregister a command
     *
     * @param name The command name
     * @return true if unregistration was successful
     */
    fun unregisterCommand(name: String): Boolean {
        try {
            val command = registeredCommands.remove(name.lowercase()) ?: return false

            // Unregister from command map
            command.unregister(commandMap)

            // Remove from known commands
            val knownCommandsField = commandMap.javaClass.getDeclaredField("knownCommands")
            knownCommandsField.isAccessible = true
            @Suppress("UNCHECKED_CAST")
            val knownCommands = knownCommandsField.get(commandMap) as MutableMap<String, Command>

            knownCommands.remove(name.lowercase())
            command.aliases.forEach { alias ->
                knownCommands.remove(alias.lowercase())
            }

            return true
        } catch (e: Exception) {
            plugin.logger.severe("Failed to unregister command '$name': ${e.message}")
            e.printStackTrace()
            return false
        }
    }

    /**
     * Unregister all commands registered by this manager
     */
    fun unregisterAll() {
        val commands = registeredCommands.keys.toList()
        commands.forEach { unregisterCommand(it) }
    }

    /**
     * Check if a command is registered
     */
    fun isCommandRegistered(name: String): Boolean {
        return registeredCommands.containsKey(name.lowercase())
    }

    /**
     * Get all registered commands
     */
    fun getRegisteredCommands(): Collection<Command> {
        return registeredCommands.values
    }

    /**
     * Create a PluginCommand instance using reflection
     */
    private fun createPluginCommand(name: String, plugin: Plugin): PluginCommand {
        val constructor: Constructor<PluginCommand> = PluginCommand::class.java
            .getDeclaredConstructor(String::class.java, Plugin::class.java)
        constructor.isAccessible = true
        return constructor.newInstance(name, plugin)
    }
}
