package org.crewco.swccore.Bounties.commands

import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.crewco.common.Updater
import org.crewco.swccore.Startup.Companion.plugin


class swcBountyUpdate : CommandExecutor {
    override fun onCommand(sender: CommandSender, cmd: Command, label: String, args: Array<out String>): Boolean {
        if (!sender.hasPermission("swc.bounty.admin.update")){return true}
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