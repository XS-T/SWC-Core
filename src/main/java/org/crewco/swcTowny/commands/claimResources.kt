package org.crewco.swcTowny.commands

import com.palmergames.bukkit.towny.`object`.TownyUniverse
import org.bukkit.Material
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.craftbukkit.v1_7_R4.inventory.CraftItemStack
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.crewco.swcTowny.Startup.Companion.nationDBMgr
import org.crewco.swcTowny.Startup.Companion.plugin
import net.minecraft.server.v1_7_R4.NBTTagCompound
import net.minecraft.server.v1_7_R4.NBTTagList
import net.minecraft.server.v1_7_R4.NBTTagString
import org.bukkit.material.Chest
import java.lang.System.currentTimeMillis

class claimResources : CommandExecutor {
    private val nbtStorage = NBTStorageHelper()

    override fun onCommand(sender: CommandSender?, cmd: Command?, label: String?, args: Array<out String>?): Boolean {
        if (sender !is Player) {
            sender?.sendMessage("Only players can use this command.")
            return true
        }

        val player = sender
        val isAdmin = player.isOp || player.hasPermission("swc.towny.claimResources.admin")

        plugin.logger.info("[DEBUG] ${player.name} ran /claimresources (isAdmin=$isAdmin)")

        if (!isAdmin && !player.hasPermission("swc.towny.claimResources")) {
            player.sendMessage("§cYou don't have permission.")
            return true
        }

        val universe = TownyUniverse.getDataSource()
        val resident = try {
            universe.getResident(player.name)
        } catch (e: Exception) {
            player.sendMessage("Could not retrieve Towny data.")
            plugin.logger.warning("[DEBUG] Failed to get resident for ${player.name}: ${e.message}")
            return true
        } ?: run {
            player.sendMessage("You are not a Towny resident.")
            plugin.logger.warning("[DEBUG] ${player.name} is not a Towny resident.")
            return true
        }

        val nation = try {
            resident.town.nation
        } catch (e: Exception) {
            null
        }

        plugin.logger.info("[DEBUG] Nation resolved: ${nation?.name ?: "None"}")

        val cooldownString = plugin.config.getString("claimCooldown") ?: "1W"
        val cooldownMillis = try {
            parseDuration(cooldownString)
        } catch (e: Exception) {
            player.sendMessage("Invalid cooldown format in config.")
            plugin.logger.warning("[DEBUG] Invalid cooldown format: '$cooldownString'")
            return true
        }

        val now = currentTimeMillis()
        val claimsMade = mutableListOf<String>()
        val cooldownBlocked = mutableListOf<String>()
        val notLeading = mutableListOf<String>()

        val resourceWorlds = plugin.config.getConfigurationSection("resources")?.getKeys(false) ?: emptySet()
        plugin.logger.info("[DEBUG] Resource worlds found: $resourceWorlds")

        for (worldName in resourceWorlds) {
            plugin.logger.info("[DEBUG] Checking world '$worldName' for ${nation?.name ?: "Admin"}")

            val leadingNation = nationDBMgr.getLeadingNationForWorld(worldName)
            plugin.logger.info("[DEBUG] Leading nation for $worldName: ${leadingNation?.nationName ?: "None"}")

            if (!isAdmin && leadingNation?.nationName != nation?.name) {
                notLeading.add(worldName)
                continue
            }

            val lastClaim = nationDBMgr.getLastClaim(nation?.name ?: "Admin", worldName)
            if (!isAdmin && now - lastClaim < cooldownMillis) {
                cooldownBlocked.add(worldName)
                continue
            }

            val itemsSection = plugin.config.getConfigurationSection("resources.$worldName.items")
            if (itemsSection == null || itemsSection.getKeys(false).isEmpty()) {
                plugin.logger.warning("[DEBUG] No items found for $worldName in config.")
                continue
            }

            val containerType = getContainerType(worldName)
            val itemsToStore = ArrayList<ItemStack>()

            for (key in itemsSection.getKeys(false)) {
                val amount = itemsSection.getInt(key, 0)
                if (amount <= 0) continue

                val item = parseItemStack(key, amount)
                if (item != null) {
                    itemsToStore.add(item)
                    plugin.logger.info("[DEBUG] Added item '$key' x$amount")
                } else {
                    plugin.logger.warning("[DEBUG] Could not parse item key: $key")
                }
            }

            if (itemsToStore.isEmpty()) {
                plugin.logger.warning("[DEBUG] No valid items to store for $worldName.")
                continue
            }

            // ✅ Instead of giving a chest item, spawn a filled chest block in front of the player
            val chestLocation = player.location.clone().add(0.0, 0.0, 1.0) // one block in front
            val block = chestLocation.block

            // Make sure the spot is clear
            if (block.type != Material.AIR) {
                player.sendMessage("§cPlease clear the space in front of you first!")
                return true
            }

            block.type = containerType // usually Material.CHEST

            // Get the chest state and fill it
            val chestState = block.state
            if (chestState is org.bukkit.block.Chest) {
                try {
                    val inv = chestState.inventory  // ✅ correct for 1.7.10
                    for (item in itemsToStore) {
                        inv.addItem(item)
                    }

                    inv.contents = itemsToStore.toArray() as Array<out ItemStack>

                    chestState.update(true)
                    chestState.update()
                    player.sendMessage("§aA filled chest has appeared in front of you with your resources!")
                    plugin.logger.info("[DEBUG] Placed filled chest at ${chestLocation.blockX},${chestLocation.blockY},${chestLocation.blockZ} with ${itemsToStore.size} items.")
                }catch (_:Exception){}
            } else {
                player.sendMessage("§cFailed to create a container block!")
                plugin.logger.warning("[DEBUG] Failed to cast block to Chest at $chestLocation")
            }

            nationDBMgr.setLastClaim(nation?.name ?: "Admin", worldName, now)
            claimsMade.add(worldName)
        }

        if (claimsMade.isEmpty()) {
            val msg = buildString {
                if (notLeading.isNotEmpty()) append("§cNot leading in: ${notLeading.joinToString(", ")}.\n")
                if (cooldownBlocked.isNotEmpty()) append("§eCooldown active for: ${cooldownBlocked.joinToString(", ")}.\n")
                if (notLeading.isEmpty() && cooldownBlocked.isEmpty()) append("§7No eligible worlds to claim.")
            }
            player.sendMessage(msg)
        } else {
            player.sendMessage("§aSuccessfully claimed: ${claimsMade.joinToString(", ")}.")
        }

        return true
    }

    private fun getContainerType(worldName: String): Material {
        val name = plugin.config.getString("resources.$worldName.containerType", "CHEST")
        return Material.matchMaterial(name) ?: Material.CHEST
    }

    private fun parseItemStack(itemKey: String, amount: Int): ItemStack? {
        return if (itemKey.contains(":")) {
            val parts = itemKey.split(":")
            val modId = parts[0]
            val modItem = parts.getOrNull(1) ?: return null
            val item = ItemStack(Material.matchMaterial(itemKey), amount)
            nbtStorage.setItemNBT(item, "modId", modId)
            nbtStorage.setItemNBT(item, "modItem", modItem)
            item
        } else {
            val mat = Material.matchMaterial(itemKey)
            mat?.let { ItemStack(it, amount) }
        }
    }

    private fun parseDuration(str: String): Long {
        val regex = Regex("""(\d+)([smhdwSMHDW])""")
        val match = regex.matchEntire(str.trim()) ?: throw IllegalArgumentException()
        val (amountStr, unit) = match.destructured
        val amount = amountStr.toLong()
        return when (unit.lowercase()) {
            "s" -> amount * 1000
            "m" -> amount * 60_000
            "h" -> amount * 3_600_000
            "d" -> amount * 86_400_000
            "w" -> amount * 604_800_000
            else -> throw IllegalArgumentException()
        }
    }

    inner class NBTStorageHelper {
        fun setItemNBT(item: ItemStack, key: String, value: String): ItemStack {
            val nms = CraftItemStack.asNMSCopy(item)
            val tag = nms.tag ?: NBTTagCompound()
            tag.setString(key, value)
            nms.tag = tag
            return CraftItemStack.asBukkitCopy(nms)
        }
    }
}
