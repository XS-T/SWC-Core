package org.crewco.swcTowny.PCS.commands

import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.crewco.swcTowny.Startup.Companion.nationDBMgr
import java.sql.SQLException

class listdata : CommandExecutor {

    override fun onCommand(
        sender: CommandSender,
        command: Command,
        label: String,
        args: Array<out String>
    ): Boolean {
        if (sender !is Player) {
            sender.sendMessage("§cThis command can only be run by a player.")
            return true
        }

        if (!sender.hasPermission("swc.towny.admin.listData")){return true}

        sender.sendMessage("§e[NationDB] Listing all nation data...")

        try {
            val nations = getAllNationNames()

            if (nations.isEmpty()) {
                sender.sendMessage("§cNo nation data found.")
                println("[NationDB] No nation data found.")
                return true
            }

            for (nationName in nations) {
                val nation = nationDBMgr.getNation(nationName)

                if (nation == null) {
                    sender.sendMessage("§cNation '$nationName' could not be loaded.")
                    println("[NationDB] Failed to load nation: $nationName")
                    continue
                }

                sender.sendMessage("§6Nation: §e${nation.name}")
                println("[NationDB] Nation: ${nation.name} (${nation.towns.size} towns)")

                for (town in nation.towns) {
                    sender.sendMessage("§7 - §a${town.name} §7in world §b${town.world} §7with §f${town.totalBlocks} blocks")
                    println("  [Town] ${town.name} in ${town.world}, blocks=${town.totalBlocks}")
                }
            }

        } catch (e: Exception) {
            sender.sendMessage("§cAn error occurred while listing data. Check console.")
            println("[NationDB] Error listing data: ${e.message}")
            e.printStackTrace()
        }

        return true
    }

    private fun getAllNationNames(): List<String> {
        val result = mutableListOf<String>()
        var stmt: java.sql.Statement? = null
        var rs: java.sql.ResultSet? = null

        try {
            // Access the private connection field via reflection
            val connectionField = nationDBMgr.javaClass.getDeclaredField("connection").apply { isAccessible = true }
            val connection = connectionField.get(nationDBMgr) as? java.sql.Connection ?: return emptyList()

            stmt = connection.createStatement()
            rs = stmt.executeQuery("SELECT name FROM nations;")

            while (rs.next()) {
                result.add(rs.getString("name"))
            }
        } catch (e: SQLException) {
            println("[NationDB] SQL Exception in getAllNationNames: ${e.message}")
            e.printStackTrace()
        } finally {
            try { rs?.close() } catch (_: Exception) {}
            try { stmt?.close() } catch (_: Exception) {}
        }

        return result
    }
}
