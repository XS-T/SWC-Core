package org.crewco.swcTowny.PCS.listeners

import com.palmergames.bukkit.towny.event.DeleteNationEvent
import com.palmergames.bukkit.towny.event.NationAddTownEvent
import com.palmergames.bukkit.towny.event.NationRemoveTownEvent
import com.palmergames.bukkit.towny.event.NewNationEvent
import com.palmergames.bukkit.towny.event.TownClaimEvent
import com.palmergames.bukkit.towny.event.TownUnclaimEvent
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.crewco.swcTowny.Startup.Companion.nationDBMgr
import org.crewco.swcTowny.Startup.Companion.plugin

class nationUpdate: Listener {
    @EventHandler
    fun onExecute(e: NewNationEvent){
        plugin.logger.info("Adding nation ${e.nation.name} to towny Nation Database")
        plugin.importFromTowny(nationDBMgr)
        nationDBMgr.updateLeadingNations()
        plugin.logger.info("Finished Updating towny Nation Database")
    }

    @EventHandler
    fun onExec(e: DeleteNationEvent){
        plugin.logger.info("Removing nation ${e.nationName} from database")
        nationDBMgr.deleteNation(e.nationName)
        nationDBMgr.updateLeadingNations()
        plugin.logger.info("Removed nation from database")
    }

    @EventHandler
    fun onClaim(e: TownClaimEvent){
        plugin.logger.info("Updating Towny info for town ${e.townBlock.town.name}")
        plugin.importFromTowny(nationDBMgr)
        nationDBMgr.updateLeadingNations()
    }

    @EventHandler
    fun onUnClaim(e: TownUnclaimEvent){
        plugin.logger.info("Updating Towny info for town ${e.town.name}")
        plugin.importFromTowny(nationDBMgr)
        nationDBMgr.updateLeadingNations()
    }

    @EventHandler
    fun onNationTownAdd(e: NationAddTownEvent){
        plugin.logger.info("Updating Nation info for nation ${e.nation.name}")
        plugin.importFromTowny(nationDBMgr)
        nationDBMgr.updateLeadingNations()
    }

    @EventHandler
    fun onNationTownRemove(e: NationRemoveTownEvent){
        plugin.logger.info("Updating Nation info for nation ${e.nation.name}")
        plugin.importFromTowny(nationDBMgr)
        nationDBMgr.updateLeadingNations()
    }
}