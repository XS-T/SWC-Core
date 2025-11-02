package org.crewco.swccore.Bounties.listeners


import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import org.crewco.swccore.Startup.Companion.bountyManager


class BountyBoardListener:Listener {
    /*
private val GUI_TITLE = "Active Bounties"

@EventHandler
fun onInventoryClick(event: InventoryClickEvent) {
    val player = event.whoClicked as? Player ?: return
    val inventory = event.view.title
    val clickedItem: ItemStack = event.currentItem ?: return

    if (inventory != GUI_TITLE) return
    event.isCancelled = true

    if (event.isLeftClick) {
        if (clickedItem.type != Material.SKULL_ITEM || !clickedItem.hasItemMeta()) return
        val lore = clickedItem.itemMeta?.lore ?: return
        val idLine = lore.find { it.startsWith("§8ID: ") } ?: return
        val bountyId = idLine.removePrefix("§8ID: ").toIntOrNull() ?: return

        val bounty = bountyManager.getActiveBounties().find { it.id == bountyId } ?: return
        val target = Bukkit.getPlayer(bounty.target) ?: run {
            player.sendMessage("${ChatColor.RED}That target is not currently online.")
            return
        }

        player.inventory.addItem(bountyManager.getTargetPuck(target))
        bountyManager.startTracking(player, target)
        player.sendMessage("${ChatColor.GREEN}Tracking puck added for ${target.name} and tracking started.")
    }
}
 */

    @EventHandler
    fun onInventoryClick(e: InventoryClickEvent) {
        val player = e.whoClicked as? Player ?: return
        val view = e.view
        val title = view.title

        if (!title.startsWith("Bounties Page")) return
        e.isCancelled = true

        val clicked = e.currentItem ?: return
        val name = clicked.itemMeta?.displayName ?: return

        when {
            name.contains("Previous Page") -> {
                val currentPage = title.removePrefix("Bounties Page ").toIntOrNull() ?: 1
                bountyManager.openBountyBoard(player, currentPage - 1)
            }

            name.contains("Next Page") -> {
                val currentPage = title.removePrefix("Bounties Page ").toIntOrNull() ?: 1
                bountyManager.openBountyBoard(player, currentPage + 1)
            }

            name.contains("→") && player.hasPermission("swcb.admin.bounty.claim") -> {
                val lore = clicked.itemMeta?.lore ?: return
                val idLine = lore.find { it.contains("ID:") } ?: return
                val id = idLine.removePrefix("§8ID: ").toIntOrNull() ?: return

                val targetBounty = bountyManager.getActiveBounties().find { it.id == id } ?: return
                val target = Bukkit.getPlayer(targetBounty.target) ?: return

                if (bountyManager.completeBounty(player, target)) {
                    Bukkit.getOnlinePlayers().forEach {
                        it.sendMessage("§a[Admin] ${player.name} has forcefully claimed the bounty on ${target.name}.")
                    }
                    player.closeInventory()
                } else {
                    player.sendMessage("§cBounty no longer available.")
                }
            }
        }
    }
}