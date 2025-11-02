package org.crewco.swccore.Bounties.listeners

import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.entity.PlayerDeathEvent
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.player.PlayerDropItemEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.inventory.ItemStack
import org.crewco.swccore.Startup.Companion.bountyManager
import org.crewco.swccore.Startup.Companion.sysMsg

class BountyListener : Listener{
    @EventHandler
    fun onPlayerDeath(event: PlayerDeathEvent) {
        val victim = event.entity
        val killer = victim.killer ?: return
        if (bountyManager.hasBounty(victim)) {
            bountyManager.completeBounty(killer, victim)
        }
    }

    @EventHandler
    fun onInventoryClick(e: InventoryClickEvent) {
        val clickedItem = e.currentItem ?: return
        val whoClicked = e.whoClicked as? Player ?: return

        if (isTrackingPuck(clickedItem)) {
            // Only allow moving within player's own inventory
            // e.view.topInventory = open inventory (e.g., chest)
            // e.view.bottomInventory = player inventory

            val clickedInventory = e.clickedInventory
            val topInventory = e.view.topInventory
            val bottomInventory = e.view.bottomInventory

            // Prevent moving into any inventory other than player's own (bottomInventory)
            if (clickedInventory != bottomInventory || e.inventory != bottomInventory) {
                e.isCancelled = true
                whoClicked.sendMessage("$sysMsg You cannot move the tracking Puck outside your inventory.")
                return
            }

            // Also block shift-click trying to move it out
            if (e.isShiftClick) {
                e.isCancelled = true
                whoClicked.sendMessage("$sysMsg You cannot move the Puck compass outside your inventory.")
                return
            }
        }
    }

    @EventHandler
    fun onPlayerDropItem(e: PlayerDropItemEvent) {
        val item = e.itemDrop.itemStack
        if (isTrackingPuck(item)) {
            e.isCancelled = true
            e.player.sendMessage("$sysMsg You cannot drop the tracking compass.")
        }
    }


    @EventHandler
    fun onItemClick(e: PlayerInteractEvent) {
        val updateCooldowns = mutableMapOf<Player, Long>()
        val cooldownSeconds = 10
        val item = e.item ?: return
        val player = e.player
        if (!isTrackingPuck(item)) return

        val now = System.currentTimeMillis()
        val lastUsed = updateCooldowns[player] ?: 0
        val secondsSinceLast = (now - lastUsed) / 1000

        if (secondsSinceLast < cooldownSeconds) {
            val remaining = cooldownSeconds - secondsSinceLast
            val minutes = remaining / 60
            val seconds = remaining % 60

            val readableTime = buildString {
                if (minutes > 0) append("${minutes}m ")
                append("${seconds}s")
            }.trim()

            player.sendMessage("$sysMsg You must wait $readableTime to refresh the location.")
            return
        }

        val meta = item.itemMeta ?: return
        val targetName = meta.displayName.replace("§eTracking: ", "")
        val target = Bukkit.getPlayer(targetName)
        if (!bountyManager.hasBounty(target)){
            player.sendMessage("$sysMsg Old Tracking puck removing..")
            val inv = player.inventory
            for (slot in 0 until inv.size) {
                val item = inv.getItem(slot)
                if (isTrackingPuck(item)) {
                    inv.clear(slot)
                }
            }
            player.sendMessage("$sysMsg Old Tracking puck removed")
        }

        if (target == null || !target.isOnline) {
            player.sendMessage("$sysMsg Target not found or offline.")
            return
        }


        // Update DB location
        bountyManager.updateBountyLocation(target)

        val loc = target.location
        val newLore = mutableListOf<String>()
        newLore.add("§7Tracking: $targetName")
        newLore.add("§7World: ${bountyManager.getWorldNameIfDIM(target)}")
        newLore.add("§7X: %.1f".format(loc.x))
        newLore.add("§7Y: %.1f".format(loc.y))
        newLore.add("§7Z: %.1f".format(loc.z))

        meta.lore = newLore
        item.itemMeta = meta
        player.sendMessage("$sysMsg Tracking puck updated to ${target.name}'s new location.")
        updateCooldowns[player] = now
    }

    fun isTrackingPuck(item: ItemStack?): Boolean {
        if (item == null || item.type != Material.valueOf("swc:fob")) return false
        val meta = item.itemMeta ?: return false
        val lore = meta.lore ?: return false
        return lore.any { it.contains("Tracking:") }
    }
}