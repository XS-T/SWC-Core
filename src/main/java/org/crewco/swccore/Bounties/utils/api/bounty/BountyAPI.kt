package org.crewco.swccore.Bounties.utils.api.bounty

import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.crewco.swccore.Bounties.utils.managers.BountyManager
import org.crewco.swccore.Startup.Companion.bountyManager
import java.time.Duration

class BountyAPI : BountyAPIInt {
    override fun getAllBounties(): List<BountyManager.Bounty> {
        return bountyManager.getActiveBounties()

    }

    override fun placeBounty(placer: Player, target: Player, reward: Double, duration: Duration): Boolean {
        return bountyManager.placeBounty(placer,target,reward,duration)
    }

    override fun completeBounty(hunter: Player, target: Player): Boolean {
        return bountyManager.completeBounty(hunter,target)
    }

    override fun getTrackingItem(target: Player): ItemStack {
        return bountyManager.getTargetPuck(target)
    }
}