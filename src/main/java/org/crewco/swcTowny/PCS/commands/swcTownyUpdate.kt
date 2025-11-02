package org.crewco.swcTowny.PCS.commands

import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.crewco.swcTowny.Startup.Companion.plugin
import org.crewco.common.Updater

class swcTownyUpdate : CommandExecutor {
    override fun onCommand(sender: CommandSender, cmd: Command, label: String, args: Array<out String>): Boolean {
        if (!sender.hasPermission("swc.towny.admin.update")){return true}
        val update = Updater
        if (!update.isUpdated()){
            update.checkAndUpdate(plugin)
            sender.sendMessage("Updating the plugin...server will need a restart on completion")
        }else{
            sender.sendMessage("Plugin is up to date current version is ${plugin.description.version}")
        }
        return true
    }
}