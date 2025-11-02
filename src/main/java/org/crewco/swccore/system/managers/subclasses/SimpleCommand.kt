package org.crewco.swccore.system.managers.subclasses

import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender

/**
 * Simple wrapper command for easy registration
 */
class SimpleCommand(
    private val onExecute: (sender: CommandSender, command: Command, label: String, args: Array<out String>) -> Boolean
) : CommandExecutor {
    override fun onCommand(
        sender: CommandSender,
        command: Command,
        label: String,
        args: Array<out String>
    ): Boolean {
        return onExecute(sender, command, label, args)
    }
}