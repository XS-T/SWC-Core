package org.crewco.swccore.Bounties.utils.api.bounty

import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.crewco.swccore.Bounties.utils.managers.BountyManager
import java.time.Duration

interface BountyAPIInt {
    fun getAllBounties(): List<BountyManager.Bounty>
    fun placeBounty(placer: Player, target: Player, reward: Double, duration: Duration): Boolean
    fun completeBounty(hunter: Player, target: Player): Boolean
    fun getTrackingItem(target: Player): ItemStack
}