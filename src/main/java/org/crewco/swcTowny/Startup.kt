package org.crewco.swcTowny

import com.palmergames.bukkit.towny.`object`.TownBlock
import org.bukkit.plugin.java.JavaPlugin
import org.crewco.swcTowny.sysCommon.EventListenerRegistrar
import org.crewco.swcTowny.sysCommon.RecipeRegistrar
import org.crewco.swcTowny.sysCommon.CommandRegistrar
import com.palmergames.bukkit.towny.`object`.TownyUniverse
import org.bukkit.Bukkit
import org.crewco.swcTowny.commands.*
import org.crewco.swcTowny.listeners.nationUpdate
import org.crewco.swcTowny.sysCommon.CommandRegistrar.registerCommands
import org.crewco.swcTowny.sysCommon.EventListenerRegistrar.registerListeners
import org.crewco.swcTowny.sysCommon.Updater
import org.crewco.swcTowny.utils.NationDBManager


class Startup : JavaPlugin() {
    companion object{
        lateinit var plugin: Startup
            private set
        lateinit var nationDBMgr: NationDBManager

    }
    override fun onEnable() {
        super.onEnable()
        // Plugin startup logic

        //Intilizers
        CommandRegistrar.initialize(this)
        EventListenerRegistrar.initialize(this)
        RecipeRegistrar.initialize(this)
        plugin = this
        nationDBMgr = NationDBManager(plugin.dataFolder)

        //Updating Block

        //if (!Updater.isUpdated()){
        //    Updater.checkAndUpdate(this)
        //}

        // Check if Towny is loaded before importing
        val townyPlugin = Bukkit.getPluginManager().getPlugin("Towny")
        if (townyPlugin != null && townyPlugin.isEnabled) {
            logger.info("Towny API successfully hooked.")
            importFromTowny(nationDBMgr)
            nationDBMgr.updateLeadingNations()
        } else {
            logger.warning("Towny not found or not enabled — skipping Towny import.")
        }

        //register commands
        plugin.logger.info("Registering Commands")
        registerCommands(reloadconfig::class)
        registerCommands(listdata::class)
        registerCommands(leadingNation::class)
        registerCommands(claimResources::class)
        registerCommands(swcTownyUpdate::class)
        plugin.logger.info("Registered Commands")

        //register events
        plugin.logger.info("Registering Listeners")
        registerListeners(nationUpdate::class)
        plugin.logger.info("Registered Listeners")


        //Gen Config
        plugin.saveDefaultConfig()
    }

    override fun onDisable() {
        super.onDisable()
        // Plugin shutdown logic
        importFromTowny(nationDBMgr)
        nationDBMgr.close()
    }


    fun importFromTowny(db: NationDBManager) {
        val universe = TownyUniverse.getDataSource()
        println("[TownyImport] Starting import of nations...")

        val nations = universe.nations.map { nation ->
            println("[TownyImport] Nation: ${nation.name}")

            val towns = nation.towns.map { town ->
                val townBlocks = try {
                    town.townBlocks
                } catch (e: Exception) {
                    println("[TownyImport] Failed to get townBlocks for town ${town.name}: ${e.message}")
                    emptyList<TownBlock>()
                }

                val world = townBlocks.firstOrNull()?.world?.name ?: run {
                    println("[TownyImport] Town ${town.name} has no townBlocks, defaulting world to 'unknown'")
                    "unknown"
                }

                val blocks = try {
                    town.totalBlocks
                } catch (e: Exception) {
                    println("[TownyImport] Failed to get totalBlocks for ${town.name}: ${e.message}")
                    townBlocks.size // fallback
                }

                println("[TownyImport] Town: ${town.name}, World: $world, Blocks: $blocks")

                NationDBManager.TownData(
                    name = town.name,
                    world = world,
                    totalBlocks = blocks
                )
            }

            NationDBManager.NationData(nation.name, towns)
        }

        nations.forEach {
            println("[TownyImport] Saving nation ${it.name} with ${it.towns.size} towns")
            db.saveNation(it)
        }
    }
}