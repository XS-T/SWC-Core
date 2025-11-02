// RecipeReloadCommand.kt
package org.crewco.swcTowny.commands

import com.palmergames.bukkit.towny.`object`.TownyUniverse
import org.bukkit.ChatColor
import org.bukkit.command.Command
import org.bukkit.command.CommandException
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.crewco.swcTowny.Startup.Companion.plugin
import org.crewco.swcTowny.sysCommon.RecipeRegistrar.loadRecipesFromConfig
import org.crewco.swcTowny.Startup.Companion.nationDBMgr
import org.crewco.swcTowny.utils.NationDBManager
import kotlin.math.sign

class reloadconfig : CommandExecutor {
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