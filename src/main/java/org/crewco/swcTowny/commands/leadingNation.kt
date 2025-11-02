package org.crewco.swcTowny.commands

import com.palmergames.bukkit.towny.db.TownyDataSource
import com.palmergames.bukkit.towny.`object`.TownyUniverse
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.crewco.swcTowny.Startup.Companion.nationDBMgr

class leadingNation : CommandExecutor {
    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (sender !is Player) {
            sender.sendMessage("§cThis command can only be run by a player.")
            return true
        }

        if (!sender.hasPermission("swc.towny.leadingNation")){return true}

        sender.sendMessage("§e[Leading Nations] Fetching leading nations for all worlds...")

        // Get all worlds present in the leading_nations table
        val worlds = getAllWorlds(sender)

        if (worlds.isEmpty()) {
            sender.sendMessage("§cNo leading nation data found.")
            return true
        }

        for (world in worlds) {
            val leader = nationDBMgr.getLeadingNationForWorld(world)
            if (leader != null) {
                sender.sendMessage("§6World: §b${leader.worldName}")
                sender.sendMessage("§aLeading Nation: §e${leader.nationName}")
                sender.sendMessage("§7Towns: §f${leader.townCount} §7| Total Blocks: §f${leader.totalBlocks}")
                sender.sendMessage("§r") // blank line
            } else {
                sender.sendMessage("§6World: §b$world §7has no leading nation data.")
            }
        }

        val ldr = nationDBMgr.getOverallLeadingNation()
        if (ldr != null) {
            println("🌍 Overall Leading Nation: ${ldr.nationName} (${ldr.townCount} towns, ${ldr.totalBlocks} blocks), Leader: ${nationDBMgr.getKingNameOfNation(ldr.nationName)}")
        } else {
            println("⚠ No overall leading nation could be determined.")
        }

        return true
    }

    private fun getAllWorlds(sender: CommandSender): List<String> {
        val worlds = mutableListOf<String>()
        var stmt: java.sql.Statement? = null
        var rs: java.sql.ResultSet? = null

        try {
            val connectionField = nationDBMgr.javaClass.getDeclaredField("connection").apply { isAccessible = true }
            val connection = connectionField.get(nationDBMgr) as? java.sql.Connection ?: return emptyList()

            stmt = connection.createStatement()
            rs = stmt.executeQuery("SELECT world_name FROM leading_nations;")

            while (rs.next()) {
                val worldName = rs.getString("world_name")
                worlds.add(worldName)
            }
        } catch (e: Exception) {
            sender.sendMessage("§cError fetching worlds: ${e.message}")
            e.printStackTrace()
        } finally {
            try { rs?.close() } catch (_: Exception) {}
            try { stmt?.close() } catch (_: Exception) {}
        }

        return worlds
    }
}
