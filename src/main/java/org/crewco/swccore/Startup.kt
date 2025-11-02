package org.crewco.swccore

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
import org.crewco.swccore.PCS.commands.claimResources
import org.crewco.swccore.PCS.commands.leadingNation
import org.crewco.swccore.PCS.commands.listdata
import org.crewco.swccore.PCS.commands.swcTownyUpdate
import org.crewco.swccore.PCS.listeners.nationUpdate
import org.crewco.common.CommandRegistrar.registerCommands
import org.crewco.common.EventListenerRegistrar.registerListeners
import org.crewco.common.Updater
import org.crewco.swccore.Bounties.commands.bounty
import org.crewco.swccore.Bounties.commands.swcBountyUpdate
import org.crewco.swccore.Bounties.listeners.BountyBoardListener
import org.crewco.swccore.Bounties.listeners.BountyListener
import org.crewco.swccore.system.managers.BountyManager
import org.crewco.swccore.Bounties.utils.api.bounty.BountyAPI
import org.crewco.swccore.system.managers.AddonManager
import org.crewco.swccore.PCS.utils.NationDBManager
import org.crewco.swccore.system.commands.ReloadSwcConfig
import org.crewco.swccore.system.managers.CommandManager
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
    }

    // Instance properties (not companion) - accessible to addons
    lateinit var addonManager: AddonManager
    lateinit var commandManager: CommandManager

    override fun onLoad(){
        // Initialize command manager FIRST (before addons need it)
        commandManager = CommandManager(this)
        logger.info("CommandManager initialized")

        // Initialize the addon manager
        addonManager = AddonManager(this)
        logger.info("AddonManager initialized")

        // Load addons from the addons dir
        val addonsFolder = File(dataFolder, "addons")
        addonManager.loadAddonsFromDirectory(addonsFolder)
    }

    override fun onEnable() {
        super.onEnable()
        // Plugin startup logic
        //Intilizers
        //Updating Block
        // if (!Updater.isUpdated()){
        //     Updater.checkAndUpdate(this)
        // }

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
        logger.info("Enabling addons...")
        addonManager.enableAddons()
        logger.info("Addons enabled")

        // Register addon management commands
        setupCommands()

        logger.info("${description.name} has been enabled!")
    }

    override fun onDisable() {
        super.onDisable()
        // Plugin shutdown logic

        logger.info("Disabling addons...")
        // Disable all addons
        addonManager.disableAddons()

        // Unregister all dynamically registered commands
        commandManager.unregisterAll()

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

        logger.info("${description.name} has been disabled!")
    }

    private fun setupCommands() {
        getCommand("addon")?.setExecutor { sender, _, _, args ->
            // Base permission check
            if (!sender.hasPermission("swccore.addon")) {
                sender.sendMessage("§cYou don't have permission to manage addons!")
                return@setExecutor true
            }

            if (args.isEmpty()) {
                sender.sendMessage("§e=== SWCCore Addon Manager ===")
                if (sender.hasPermission("swccore.addon.list")) {
                    sender.sendMessage("§7/addon list §f- List all addons")
                }
                if (sender.hasPermission("swccore.addon.info")) {
                    sender.sendMessage("§7/addon info <id> §f- Show addon details")
                }
                if (sender.hasPermission("swccore.addon.load")) {
                    sender.sendMessage("§7/addon load <filename> §f- Load an unloaded addon")
                }
                if (sender.hasPermission("swccore.addon.unload")) {
                    sender.sendMessage("§7/addon unload <id> §f- Unload an addon")
                }
                if (sender.hasPermission("swccore.addon.enable")) {
                    sender.sendMessage("§7/addon enable <id> §f- Enable an addon")
                }
                if (sender.hasPermission("swccore.addon.disable")) {
                    sender.sendMessage("§7/addon disable <id> §f- Disable an addon")
                }
                if (sender.hasPermission("swccore.addon.reload")) {
                    sender.sendMessage("§7/addon reload [id] §f- Reload addon(s)")
                }
                return@setExecutor true
            }

            when (args[0].lowercase()) {
                "list" -> {
                    if (!sender.hasPermission("swccore.addon.list")) {
                        sender.sendMessage("§cYou don't have permission to list addons!")
                        return@setExecutor true
                    }

                    val allAddons = addonManager.getAllAddonInfo()

                    if (allAddons.isEmpty()) {
                        sender.sendMessage("§cNo addons found")
                        return@setExecutor true
                    }

                    sender.sendMessage("§e=== Addons (${allAddons.size}) ===")

                    allAddons.forEach { info ->
                        val (statusColor, statusSymbol, statusText) = when (info.state) {
                            AddonManager.AddonState.ENABLED -> Triple("§a", "●", "ENABLED")
                            AddonManager.AddonState.DISABLED -> Triple("§7", "●", "DISABLED")
                            AddonManager.AddonState.LOADED -> Triple("§e", "●", "LOADED")
                            AddonManager.AddonState.UNLOADED -> Triple("§b", "○", "UNLOADED")
                            AddonManager.AddonState.FAILED -> Triple("§c", "✗", "FAILED")
                        }

                        when (info.state) {
                            AddonManager.AddonState.FAILED -> {
                                sender.sendMessage("$statusColor$statusSymbol §7${info.fileName} §c[$statusText]")
                                sender.sendMessage("   §8Error: ${info.errorMessage}")
                            }
                            AddonManager.AddonState.UNLOADED -> {
                                sender.sendMessage("$statusColor$statusSymbol §f${info.name} §7v${info.version} $statusColor[$statusText]")
                                sender.sendMessage("   §8File: ${info.fileName} §7- Use §e/addon load ${info.fileName}")
                            }
                            else -> {
                                sender.sendMessage("$statusColor$statusSymbol §f${info.name} §7v${info.version} $statusColor[$statusText] §8(${info.id})")
                            }
                        }
                    }
                }

                "load" -> {
                    if (!sender.hasPermission("swccore.addon.load")) {
                        sender.sendMessage("§cYou don't have permission to load addons!")
                        return@setExecutor true
                    }

                    if (args.size < 2) {
                        sender.sendMessage("§cUsage: /addon load <filename>")
                        sender.sendMessage("§7Example: /addon load MyAddon-1.0.jar")
                        return@setExecutor true
                    }

                    val filename = args[1]
                    sender.sendMessage("§7Loading addon from §e$filename§7...")

                    if (addonManager.loadAddonByFilename(filename)) {
                        sender.sendMessage("§aSuccessfully loaded addon from $filename")
                        sender.sendMessage("§7Use §e/addon enable <id> §7to enable it")
                    } else {
                        sender.sendMessage("§cFailed to load addon from $filename")
                        sender.sendMessage("§7Check console for errors")
                    }
                }

                "unload" -> {
                    if (!sender.hasPermission("swccore.addon.unload")) {
                        sender.sendMessage("§cYou don't have permission to unload addons!")
                        return@setExecutor true
                    }

                    if (args.size < 2) {
                        sender.sendMessage("§cUsage: /addon unload <id>")
                        return@setExecutor true
                    }

                    val addonId = args[1]

                    if (!addonManager.isAddonLoaded(addonId)) {
                        sender.sendMessage("§cAddon not found: $addonId")
                        return@setExecutor true
                    }

                    sender.sendMessage("§7Unloading addon §e$addonId§7...")

                    if (addonManager.unloadAddon(addonId)) {
                        sender.sendMessage("§aSuccessfully unloaded addon: $addonId")
                        sender.sendMessage("§7The addon has been removed from memory")
                    } else {
                        sender.sendMessage("§cFailed to unload addon: $addonId")
                        sender.sendMessage("§7Check console for errors")
                    }
                }

                "info" -> {
                    if (!sender.hasPermission("swccore.addon.info")) {
                        sender.sendMessage("§cYou don't have permission to view addon info!")
                        return@setExecutor true
                    }

                    if (args.size < 2) {
                        sender.sendMessage("§cUsage: /addon info <id>")
                        return@setExecutor true
                    }

                    val addon = addonManager.getAddon(args[1])
                    if (addon == null) {
                        sender.sendMessage("§cAddon not found: ${args[1]}")
                        return@setExecutor true
                    }

                    val state = addonManager.getAddonState(addon.id)
                    val stateColor = when (state) {
                        AddonManager.AddonState.ENABLED -> "§a"
                        AddonManager.AddonState.DISABLED -> "§7"
                        AddonManager.AddonState.LOADED -> "§e"
                        AddonManager.AddonState.UNLOADED -> "§b"
                        AddonManager.AddonState.FAILED -> "§c"
                        null -> "§8"
                    }

                    sender.sendMessage("§e=== ${addon.name} ===")
                    sender.sendMessage("§7ID: §f${addon.id}")
                    sender.sendMessage("§7Version: §f${addon.version}")
                    sender.sendMessage("§7Authors: §f${addon.authors.joinToString(", ")}")
                    sender.sendMessage("§7Description: §f${addon.description}")
                    sender.sendMessage("§7Status: $stateColor${state?.name ?: "UNKNOWN"}")
                    if (addon.dependencies.isNotEmpty()) {
                        sender.sendMessage("§7Dependencies: §f${addon.dependencies.joinToString(", ")}")
                    }
                }

                "enable" -> {
                    if (!sender.hasPermission("swccore.addon.enable")) {
                        sender.sendMessage("§cYou don't have permission to enable addons!")
                        return@setExecutor true
                    }

                    if (args.size < 2) {
                        sender.sendMessage("§cUsage: /addon enable <id>")
                        return@setExecutor true
                    }

                    val addonId = args[1]
                    if (!addonManager.isAddonLoaded(addonId)) {
                        sender.sendMessage("§cAddon not found: $addonId")
                        sender.sendMessage("§7Use §e/addon list §7to see available addons")
                        return@setExecutor true
                    }

                    if (addonManager.enableAddon(addonId)) {
                        sender.sendMessage("§aEnabled addon: $addonId")
                    } else {
                        sender.sendMessage("§cFailed to enable addon: $addonId")
                        sender.sendMessage("§7Check console for errors")
                    }
                }

                "disable" -> {
                    if (!sender.hasPermission("swccore.addon.disable")) {
                        sender.sendMessage("§cYou don't have permission to disable addons!")
                        return@setExecutor true
                    }

                    if (args.size < 2) {
                        sender.sendMessage("§cUsage: /addon disable <id>")
                        return@setExecutor true
                    }

                    val addonId = args[1]
                    if (!addonManager.isAddonLoaded(addonId)) {
                        sender.sendMessage("§cAddon not found: $addonId")
                        return@setExecutor true
                    }

                    if (addonManager.disableAddon(addonId)) {
                        sender.sendMessage("§aDisabled addon: $addonId")
                    } else {
                        sender.sendMessage("§cFailed to disable addon: $addonId")
                        sender.sendMessage("§7Check console for errors")
                    }
                }

                "reload" -> {
                    if (!sender.hasPermission("swccore.addon.reload")) {
                        sender.sendMessage("§cYou don't have permission to reload addons!")
                        return@setExecutor true
                    }

                    if (args.size > 1) {
                        val addonId = args[1]
                        if (addonManager.reloadAddon(addonId)) {
                            sender.sendMessage("§aReloaded addon: $addonId")
                        } else {
                            sender.sendMessage("§cFailed to reload addon: $addonId")
                            sender.sendMessage("§7Check console for errors")
                        }
                    } else {
                        sender.sendMessage("§7Reloading all addons...")
                        addonManager.reloadAddons()
                        sender.sendMessage("§aReloaded all addons")
                    }
                }

                else -> {
                    sender.sendMessage("§cUnknown subcommand: ${args[0]}")
                    sender.sendMessage("§7Use §e/addon §7for help")
                }
            }

            true
        }

        // Add tab completion
        getCommand("addon")?.tabCompleter = org.bukkit.command.TabCompleter { sender, _, _, args ->
            if (!sender.hasPermission("swccore.addon")) {
                return@TabCompleter null
            }

            when (args.size) {
                1 -> {
                    val subcommands = mutableListOf<String>()
                    if (sender.hasPermission("swccore.addon.list")) subcommands.add("list")
                    if (sender.hasPermission("swccore.addon.info")) subcommands.add("info")
                    if (sender.hasPermission("swccore.addon.load")) subcommands.add("load")
                    if (sender.hasPermission("swccore.addon.unload")) subcommands.add("unload")
                    if (sender.hasPermission("swccore.addon.enable")) subcommands.add("enable")
                    if (sender.hasPermission("swccore.addon.disable")) subcommands.add("disable")
                    if (sender.hasPermission("swccore.addon.reload")) subcommands.add("reload")

                    subcommands.filter { it.startsWith(args[0].lowercase()) }
                }
                2 -> when (args[0].lowercase()) {
                    "info", "enable", "disable", "reload" -> {
                        if (sender.hasPermission("swccore.addon.${args[0].lowercase()}")) {
                            addonManager.getAddons().map { it.id }
                                .filter { it.startsWith(args[1].lowercase()) }
                        } else null
                    }
                    "unload" -> {
                        if (sender.hasPermission("swccore.addon.unload")) {
                            addonManager.getAddons().map { it.id }
                                .filter { it.startsWith(args[1].lowercase()) }
                        } else null
                    }
                    "load" -> {
                        if (sender.hasPermission("swccore.addon.load")) {
                            addonManager.scanForNewAddons().map { it.name }
                                .filter { it.startsWith(args[1].lowercase()) }
                        } else null
                    }
                    else -> null
                }
                else -> null
            }
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