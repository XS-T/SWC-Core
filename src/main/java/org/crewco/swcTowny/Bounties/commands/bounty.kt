package org.crewco.swcTowny.Bounties.commands

import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.crewco.swcTowny.Startup.Companion.bountyManager
import org.crewco.swcTowny.Startup.Companion.economy
import org.crewco.swcTowny.Startup.Companion.plugin
import org.crewco.swcTowny.Startup.Companion.sysMsg
import java.time.Duration
import java.time.Instant
import java.util.UUID

class bounty : CommandExecutor {
    override fun onCommand(p0: CommandSender?, p1: Command?, p2: String?, p3: Array<out String?>?): Boolean {

        val placeCooldowns = mutableMapOf<UUID, Long>()
        val PLACE_COOLDOWN_SECONDS = 60L // 60 seconds cooldown

        if (p1?.name.equals("bounty", ignoreCase = true)) {
            if (p0 !is Player) return false
            val player = p0
            val args = p3
            when (args?.getOrNull(0)) {

                "place" -> {
                    if (!player.hasPermission("swcb.bounty.place")) return false

                    val uuid = player.uniqueId
                    val now = System.currentTimeMillis()
                    val lastUsed = placeCooldowns[uuid] ?: 0L

                    if ((now - lastUsed) < PLACE_COOLDOWN_SECONDS * 1000) {
                        val secondsLeft = ((PLACE_COOLDOWN_SECONDS * 1000 - (now - lastUsed)) / 1000).toInt()
                        player.sendMessage("$sysMsg You must wait $secondsLeft seconds before placing another bounty.")
                        return false
                    }

                    val target = Bukkit.getPlayer(args.getOrNull(1) ?: return false) ?: return false
                    val reward = args.getOrNull(2)?.toDouble() ?: return false

                    if (economy.getBalance(player) < reward) {
                        player.sendMessage("$sysMsg You cannot place this bounty because you don't have enough money.")
                        return false
                    }

                    if (reward < plugin.config.getInt("bounty-amount").toDouble()) {
                        player.sendMessage("$sysMsg The amount you set is less than the default amount of ${plugin.config.getInt("bounty-amount").toDouble()}")
                        return false
                    }

                    val durationStr = args.getOrNull(3)
                    val defaultExpiryStr = plugin.config.getString("default-expiry", "1d")!!
                    val maxExpiryStr = plugin.config.getString("max-expiry", "3d")!!

                    val defaultDuration = parseTimeToDuration(defaultExpiryStr)
                    val maxDuration = parseTimeToDuration(maxExpiryStr)
                    val requestedDuration = durationStr?.let { parseTimeToDuration(it) } ?: defaultDuration

                    if (requestedDuration == null || maxDuration == null) {
                        player.sendMessage("$sysMsg Invalid duration format. Use formats like 1d, 2h30m, 45m, etc.")
                        return false
                    }

                    val safeDuration = requestedDuration.coerceAtMost(maxDuration)

                    bountyManager.placeBounty(player, target, reward, safeDuration)
                    economy.withdrawPlayer(player, reward)
                    placeCooldowns[uuid] = now // ✅ set cooldown

                    val formattedDuration = formatDuration(safeDuration)
                    player.sendMessage("$sysMsg Placed bounty on §e${target.name} §afor §6$$reward§a expiring in §b$formattedDuration")

                    for (onlinePlayer in Bukkit.getOnlinePlayers()) {
                        if (onlinePlayer != player) {
                            onlinePlayer.sendMessage("$sysMsg ${player.name} §7has placed a bounty on §c${target.name} §7for §6$$reward §7expiring in §b$formattedDuration")
                        }
                    }
                }

                "remove" -> {
                    if (!player.hasPermission("swcb.admin.bounty.remove")) return false
                    val target = Bukkit.getPlayer(args.getOrNull(1) ?: return false) ?: return false
                    val success = bountyManager.removeBounty(player, target)
                    if (success) {
                        player.sendMessage("$sysMsg Removed bounty on ${target.name}.")
                        Bukkit.broadcastMessage("$sysMsg The bounty on §c${target.name}§7 has been removed.")
                    } else {
                        player.sendMessage("$sysMsg You do not have permission to remove this bounty or it does not exist.")
                    }
                }

                "track" -> {
                    if (!player.hasPermission("swcb.bounty.track")) return false
                    val target = Bukkit.getPlayer(args.getOrNull(1) ?: return false) ?: return false
                    if (bountyManager.hasBounty(target)) {
                        player.inventory.addItem(bountyManager.getTargetPuck(target))
                        bountyManager.startTracking(player, target)
                        player.sendMessage("$sysMsg Tracking puck added for ${target.name} and started tracking.")
                    }
                }

                "random" -> {
                    if (!player.isOp) return false
                    val reward = args.getOrNull(1)?.toDouble() ?: 100.0
                    val defaultDuration = parseTimeToDuration(plugin.config.getString("default-expiry", "1d")!!) ?: Duration.ofDays(1)
                    val bounty = bountyManager.generateRandomBounty(player, reward, defaultDuration)
                    if (bounty != null) {
                        val formatted = formatDuration(Duration.between(Instant.now(), bounty.expires))
                        Bukkit.broadcastMessage("$sysMsg Random bounty placed on §c${Bukkit.getOfflinePlayer(bounty.target).name} §afor §6$$reward §aexpiring in §b$formatted")
                    } else {
                        player.sendMessage("$sysMsg No valid targets online.")
                    }
                }
                /*
                "board" -> {
                    if (!player.hasPermission("swcb.bounty.board")) return false
                    val bounties = bountyManager.getActiveBounties()
                    if (bounties.isEmpty()) {
                        player.sendMessage("No active bounties")
                        return true
                    }

                    val guiSize = ((bounties.size + 8) / 9).coerceAtMost(6) * 9
                    val inventory = Bukkit.createInventory(null, guiSize, "Active Bounties")

                    bounties.forEach { bounty ->
                        val placerName = Bukkit.getOfflinePlayer(bounty.placer).name ?: "Unknown"
                        val targetName = Bukkit.getOfflinePlayer(bounty.target).name ?: "Unknown"
                        val timeLeft = Duration.between(Instant.now(), bounty.expires)

                        val item = ItemStack(Material.SKULL_ITEM, 1, 3)
                        val meta = item.itemMeta
                        meta?.displayName = "§e$placerName → $targetName"
                        meta?.lore = listOf(
                            "§7Reward: §a$${bounty.reward}",
                            "§7Expires in: §f${formatDuration(timeLeft)}",
                            "§8ID: ${bounty.id}"
                        )
                        item.itemMeta = meta
                        inventory.addItem(item)
                    }

                    player.openInventory(inventory)
                }*/


                "board" -> {
                    if (!player.hasPermission("swcb.bounty.board")) return false
                    val page = args.getOrNull(1)?.toIntOrNull()?.coerceAtLeast(1) ?: 1
                    bountyManager.openBountyBoard(player, page)
                }

                "claim" -> {
                    if (!player.hasPermission("swcb.admin.bounty.claim")) return false
                    val target = Bukkit.getPlayer(args.getOrNull(1) ?: return false) ?: return false
                    val success = bountyManager.completeBounty(player, target)
                    if (success) {
                        Bukkit.broadcastMessage("$sysMsg an Admin §7${player.name} has force-claimed the bounty on §c${target.name}§7.")
                    } else {
                        player.sendMessage("$sysMsg No active bounty found for ${target.name}, or it already expired.")
                    }
                }

                "info" -> {
                    val name = plugin.description.name
                    val version = plugin.description.version
                    val authors = plugin.description.authors.joinToString(", ")
                    player.sendMessage("$sysMsg Plugin: §f$name v$version by $authors")
                }

                "help" -> {
                    player.sendMessage("§6/bounty place <player> <amount> [duration]")
                    player.sendMessage("§6/bounty remove <player>")
                    player.sendMessage("§6/bounty track <player>")
                    player.sendMessage("§6/bounty board")
                    player.sendMessage("§6/bounty claim <player>")
                    player.sendMessage("§6Duration format: §f1d2h, 30m, 1h15m, etc.")
                }

                "planet" -> {
                    if (!player.hasPermission("swcb.admin.plant.info") || player.isOp)
                        player.sendMessage("$sysMsg Your in Dimension ${player.world.name}")
                }

                else -> {
                    player.sendMessage("$sysMsg Unknown subcommand. Use /bounty help")
                }
            }
        }
        return true
    }

    fun parseTimeToDuration(input: String): Duration? {
        val regex = Regex("""(?:(\d+)d)?\s*(?:(\d+)h)?\s*(?:(\d+)m)?\s*(?:(\d+)s)?""", RegexOption.IGNORE_CASE)
        val match = regex.matchEntire(input.trim()) ?: return null

        val (d, h, m, s) = match.destructured
        return Duration.ofDays(d.toLongOrNull() ?: 0) +
                Duration.ofHours(h.toLongOrNull() ?: 0) +
                Duration.ofMinutes(m.toLongOrNull() ?: 0) +
                Duration.ofSeconds(s.toLongOrNull() ?: 0)
    }

    fun formatDuration(duration: Duration): String {
        val days = duration.toDays()
        val hours = duration.toHours() % 24
        val minutes = duration.toMinutes() % 60
        val seconds = duration.seconds % 60

        return buildString {
            if (days > 0) append("${days}d ")
            if (hours > 0) append("${hours}h ")
            if (minutes > 0) append("${minutes}m ")
            if (seconds > 0 || isBlank()) append("${seconds}s")
        }.trim()
    }
}
