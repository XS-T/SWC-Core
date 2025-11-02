package org.crewco.swccore.system.managers.subclasses

import org.bukkit.command.Command
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter

/**
 * Simple wrapper tab completer for easy registration
 */
class SimpleTabCompleter(
    private val onTabComplete: (sender: CommandSender, command: Command, label: String, args: Array<out String>) -> List<String>?
) : TabCompleter {
    override fun onTabComplete(
        sender: CommandSender,
        command: Command,
        label: String,
        args: Array<out String>
    ): List<String>? {
        return onTabComplete(sender, command, label, args)
    }
}