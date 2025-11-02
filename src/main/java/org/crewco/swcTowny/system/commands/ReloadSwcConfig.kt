// RecipeReloadCommand.kt
package org.crewco.swcTowny.system.commands

import org.bukkit.ChatColor
import org.bukkit.command.Command
import org.bukkit.command.CommandException
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.crewco.swcTowny.Startup.Companion.plugin

class ReloadSwcConfig : CommandExecutor {
    override fun onCommand(sender: CommandSender, cmd: Command, label: String, args: Array<out String>): Boolean {
        if (!sender.hasPermission("swc.towny.admin.reload")){return true}
        if (cmd.name.equals("reloadconfig", ignoreCase = true)) {
            plugin.reloadConfig()
            sender.sendMessage(ChatColor.GREEN.toString() + "Config reloaded.")
            try {
                sender.sendMessage("Commands work")
            }catch (e:CommandException){
                sender.sendMessage("Error in Recipe")
            }
            return true
        }
        return true
    }
}