package org.crewco.common

import org.bukkit.command.CommandExecutor
import org.crewco.swcTowny.Startup
import kotlin.reflect.KClass

object CommandRegistrar {

    private lateinit var plugin: Startup

    fun initialize(plugin: Startup) {
        this.plugin = plugin
    }

    fun registerCommands(vararg commandClasses: KClass<out CommandExecutor>) {
        for (commandClass in commandClasses) {
            try {
                val instance = commandClass.java.newInstance()
                if (instance is CommandExecutor) {
                    val commandName = getCommandName(instance)
                    plugin.getCommand(commandName)?.executor = instance
                } else {
                    plugin.logger.warning("Class $commandClass does not implement CommandExecutor.")
                }
            } catch (e: Exception) {
                plugin.logger.warning("Error while registering command: ${e.message}")
            }
        }
    }

    private fun getCommandName(commandExecutor: CommandExecutor): String {
        // You can implement a logic to derive the command name from the executor class
        // For simplicity, let's assume the class name is the command name
        return commandExecutor.javaClass.simpleName
    }
}