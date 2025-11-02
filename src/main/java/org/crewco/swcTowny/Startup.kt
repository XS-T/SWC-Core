package org.crewco.swcTowny

import com.palmergames.bukkit.towny.`object`.TownBlock
import org.bukkit.plugin.java.JavaPlugin
import org.crewco.common.EventListenerRegistrar
import org.crewco.common.RecipeRegistrar
import org.crewco.common.CommandRegistrar
import com.palmergames.bukkit.towny.`object`.TownyUniverse
import net.milkbowl.vault.economy.Economy
import org.bukkit.Bukkit
import org.bukkit.plugin.RegisteredServiceProvider
import org.bukkit.plugin.ServicePriority
import org.crewco.swcTowny.PCS.commands.claimResources
import org.crewco.swcTowny.PCS.commands.leadingNation
import org.crewco.swcTowny.PCS.commands.listdata
import org.crewco.swcTowny.PCS.commands.swcTownyUpdate
import org.crewco.swcTowny.PCS.listeners.nationUpdate
import org.crewco.common.CommandRegistrar.registerCommands
import org.crewco.common.EventListenerRegistrar.registerListeners
import org.crewco.common.Updater
import org.crewco.swcTowny.Bounties.commands.bounty
import org.crewco.swcTowny.Bounties.commands.swcBountyUpdate
import org.crewco.swcTowny.Bounties.listeners.BountyBoardListener
import org.crewco.swcTowny.Bounties.listeners.BountyListener
import org.crewco.swcTowny.Bounties.utils.managers.BountyManager
import org.crewco.swcTowny.Bounties.utils.api.bounty.BountyAPI
import org.crewco.swcTowny.Bounties.utils.managers.AddonManager
import org.crewco.swcTowny.PCS.utils.NationDBManager
import org.crewco.swcTowny.system.commands.ReloadSwcConfig
import java.io.File


class Startup : JavaPlugin() {
    companion object{
        lateinit var plugin: Startup
            private set
        lateinit var nationDBMgr: NationDBManager
        lateinit var economy: Economy
        lateinit var bountyManager: BountyManager
        lateinit var sysMsg: String
        lateinit var bountyAPI: BountyAPI
        lateinit var addonManager: AddonManager

    }


    override fun onLoad(){
        // Initialize the addon manager
        addonManager = AddonManager()

        // Load addons from the addons dir
        val addonsFolder = File(dataFolder, "addons")
        addonManager.loadAddonsFromDirectory(addonsFolder)

    }


    override fun onEnable() {
        super.onEnable()
        // Plugin startup logic

        //Intilizers

        //Updating Block
        if (!Updater.isUpdated()){
            Updater.checkAndUpdate(this)
        }

        CommandRegistrar.initialize(this)
        EventListenerRegistrar.initialize(this)
        RecipeRegistrar.initialize(this)
        plugin = this
        nationDBMgr = NationDBManager(plugin.dataFolder)


        // Managers
        bountyManager = BountyManager(this.config)
        bountyManager.loadAndScheduleExpiryTasks()
        bountyManager.loadTrackingData()


        // Register API
        bountyAPI = BountyAPI()
        server.servicesManager.register(BountyAPI::class.java, bountyAPI,this,ServicePriority.Normal)
        logger.info("BountyAPI v${description.version} enabled")

        // Init Vault
        if (!setupEconomy()) {
            logger.severe(String.format("[%s] - Disabled due to no Vault dependency found!", description.name));
            server.pluginManager.disablePlugin(this);
            return;
        }

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
        registerCommands(ReloadSwcConfig::class)
        registerCommands(listdata::class)
        registerCommands(leadingNation::class)
        registerCommands(claimResources::class)
        registerCommands(swcTownyUpdate::class)
        registerCommands(bounty::class,swcBountyUpdate::class)
        plugin.logger.info("Registered Commands")

        //register events
        plugin.logger.info("Registering Listeners")
        registerListeners(nationUpdate::class)
        registerListeners(BountyListener::class, BountyBoardListener::class)
        plugin.logger.info("Registered Listeners")


        //Gen Config
        plugin.saveDefaultConfig()

        // Enable all loaded addons
        addonManager.enableAddons()

        // Register commands, listeners etc.
        setupCommands()

        logger.info("${description.name} has been enabled!")
    }

    override fun onDisable() {
        super.onDisable()
        // Plugin shutdown logic
        server.servicesManager.unregisterAll(this)
        importFromTowny(nationDBMgr)
        nationDBMgr.close()

        // Persist tracking state to disk
        bountyManager.saveTrackingData()

        // Cancel all bounty expiry tasks
        bountyManager.expiryTasks.values.forEach { it.cancel() }
        bountyManager.expiryTasks.clear()

        // Cancel all tracking tasks
        bountyManager.trackingTasks.values.forEach { it.cancel() }
        bountyManager.trackingTasks.clear()

        // Close the database connection
        bountyManager.close()
    }


    private fun setupCommands() {
        // Example command to manage addons
        getCommand("addon")?.setExecutor { sender, _, _, args ->
            if (args.isEmpty()) {
                sender.sendMessage("§eLoaded Addons:")
                addonManager.getAddons().forEach { addon ->
                    sender.sendMessage("§7- §a${addon.name} §7v${addon.version} §8(${addon.id})")
                }
                return@setExecutor true
            }

            when (args[0].lowercase()) {
                "list" -> {
                    sender.sendMessage("§eLoaded Addons:")
                    addonManager.getAddons().forEach { addon ->
                        sender.sendMessage("§7- §a${addon.name} §7v${addon.version}")
                        sender.sendMessage("   §8${addon.description}")
                        sender.sendMessage("   §8By: ${addon.authors.joinToString(", ")}")
                    }
                }
                "reload" -> {
                    if (args.size > 1) {
                        val addonId = args[1]
                        if (addonManager.reloadAddon(addonId)) {
                            sender.sendMessage("§aReloaded addon: $addonId")
                        } else {
                            sender.sendMessage("§cFailed to reload addon: $addonId")
                        }
                    } else {
                        addonManager.reloadAddons()
                        sender.sendMessage("§aReloaded all addons")
                    }
                }
                "info" -> {
                    if (args.size < 2) {
                        sender.sendMessage("§cUsage: /addon info <id>")
                        return@setExecutor true
                    }

                    val addon = addonManager.getAddon(args[1])
                    if (addon == null) {
                        sender.sendMessage("§cAddon not found: ${args[1]}")
                        return@setExecutor true
                    }

                    sender.sendMessage("§e=== ${addon.name} ===")
                    sender.sendMessage("§7ID: §f${addon.id}")
                    sender.sendMessage("§7Version: §f${addon.version}")
                    sender.sendMessage("§7Authors: §f${addon.authors.joinToString(", ")}")
                    sender.sendMessage("§7Description: §f${addon.description}")
                    if (addon.dependencies.isNotEmpty()) {
                        sender.sendMessage("§7Dependencies: §f${addon.dependencies.joinToString(", ")}")
                    }
                }
                else -> {
                    sender.sendMessage("§cUnknown subcommand. Use: list, reload, info")
                }
            }

            true
        }
    }


    private fun setupEconomy(): Boolean {
        if (server.pluginManager.getPlugin("Vault") == null) {
            return false
        }
        val rsp: RegisteredServiceProvider<Economy>? = server.servicesManager.getRegistration(Economy::class.java)
        if (rsp == null) {
            return false
        }
        economy = rsp.provider
        return true
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