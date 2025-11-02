package org.crewco.swccore.system.managers

import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.OfflinePlayer
import org.bukkit.configuration.file.FileConfiguration
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.scheduler.BukkitRunnable

import org.crewco.swccore.Startup.Companion.economy
import org.crewco.swccore.Startup.Companion.plugin
import org.crewco.swccore.Startup.Companion.sysMsg
import java.io.File
import java.sql.Connection
import java.sql.DriverManager
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.Duration
import java.time.Instant
import java.util.*

class BountyManager(private val config: FileConfiguration) {

    data class Bounty(
        val id: Int,
        val placer: UUID,
        val target: UUID,
        val reward: Double,
        val expires: Instant,
        val location: Location? = null
    )

    private val conn: Connection
    val expiryTasks = mutableMapOf<Int, BukkitRunnable>()
    val trackingTasks = mutableMapOf<UUID, BukkitRunnable>()
    private val trackingTargets = mutableMapOf<UUID, UUID>()

    private val trackingFile = File(plugin.dataFolder, "tracking.yml")
    private val trackingConfig = if (trackingFile.exists()) YamlConfiguration.loadConfiguration(trackingFile) else YamlConfiguration()

    init {
        val dbFile = File(plugin.dataFolder, "bounties.db")
        dbFile.parentFile.mkdirs()
        Class.forName("org.sqlite.JDBC")
        conn = DriverManager.getConnection("jdbc:sqlite:${dbFile.path}")
        conn.createStatement().execute("""
    CREATE TABLE IF NOT EXISTS bounties (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        placer TEXT NOT NULL,
        target TEXT NOT NULL,
        reward REAL NOT NULL,
        expires TIMESTAMP NOT NULL,
        world TEXT,
        x REAL,
        y REAL,
        z REAL
    )
""".trimIndent())
    }

    fun placeBounty(placer: Player, target: Player, reward: Double, duration: Duration): Boolean {
        val expires = Instant.now().plus(duration.coerceAtMost(Duration.ofDays(3)))
        val loc = target.location

        val stmt: PreparedStatement = conn.prepareStatement("""
        INSERT INTO bounties (placer, target, reward, expires, world, x, y, z) 
        VALUES (?, ?, ?, ?, ?, ?, ?, ?)
    """)
        stmt.setString(1, placer.uniqueId.toString())
        stmt.setString(2, target.uniqueId.toString())
        stmt.setDouble(3, reward)
        stmt.setTimestamp(4, Timestamp.from(expires))
        stmt.setString(5, loc.world.name)
        stmt.setDouble(6, loc.x)
        stmt.setDouble(7, loc.y)
        stmt.setDouble(8, loc.z)
        stmt.executeUpdate()

        val rs = conn.createStatement().executeQuery("SELECT last_insert_rowid() AS id")
        if (rs.next()) {
            val bountyId = rs.getInt("id")
            val bounty = Bounty(bountyId, placer.uniqueId, target.uniqueId, reward, expires, loc)
            scheduleBountyExpiry(bounty)
        }
        return true
    }

    fun getTargetPuck(target: Player): ItemStack {
        val atlX = plugin.config?.getInt("atl-amount")?.let { target.location.x + it } ?: 10.0
        val atlY = plugin.config?.getInt("atl-amount")?.let { target.location.y + it } ?: 10.0
        val atlZ = plugin.config?.getDouble("atl-amount")?.let { target.location.z + it } ?: 10.0
        val material = try {
            Material.valueOf("swc:fob")
        } catch (e: IllegalArgumentException) {
            Material.COMPASS
        }

        return ItemStack(material).apply {
            itemMeta = itemMeta?.apply {
                displayName = "§eTracking: ${target.name}"
                lore = listOf(
                    "§7Tracking: ${target.name}",
                    "§7World: ${getWorldNameIfDIM(target)}",
                    "§7X: $atlX",
                    "§7Y: $atlY",
                    "§7Z: $atlZ"
                )
            }
        }
    }

    fun getWorldNameIfDIM(player: Player): String{
        when (player.world.name){
            // Warp Drive file names
            "Hyperspace" -> { return "HyperSpace"}
            "The Milky Way" -> {return "The Milky Way"}
            // Dim File Names
            "DIM-1" -> {return "The Underworld"}
            "world" -> {return "Telos"}
            "DIM4" -> {return "Kshyyyk"}
            "DIM80" -> {return "Tatooine"}
            "DIM8" -> {return "Dagobah"}
            "DIM3" -> {return "Hoth"}
            "DIM5" -> {return "Yavin Four"}
            "DIM32" -> {return "Ilum"}
            "DIM6" -> {return "Endor"}
            "DIM81" -> {return "Tython"}
            "DIM78" -> {return "Korriban"}
            "DIM33" -> {return "Hurrikane"}
            "DIM76" -> {return "Naboo"}
            "DIM77" -> {return "Dathomir"}
            "DIM75" -> {return "Alderaan"}
        }
        return "Not a valid world"
    }

    fun startTracking(hunter: Player, target: Player) {
        // Cancel existing tracking task for hunter if any
        trackingTasks[hunter.uniqueId]?.cancel()

        val interval = config.getLong("tracking-update-seconds", 15L).coerceAtLeast(5L) * 20L
        val task = object : BukkitRunnable() {
            override fun run() {
                if (!hunter.isOnline || !target.isOnline) {
                    cancel()
                    trackingTasks.remove(hunter.uniqueId)
                    trackingTargets.remove(hunter.uniqueId)
                    saveTrackingData()
                    return
                }
                hunter.compassTarget = target.location
            }
        }
        task.runTaskTimer(plugin, 0L, interval)
        trackingTasks[hunter.uniqueId] = task
        trackingTargets[hunter.uniqueId] = target.uniqueId
        saveTrackingData()
    }

    fun stopTracking(hunter: Player) {
        val target = trackingTargets[hunter.uniqueId]

        if (target != null) {
            val huntersTrackingTarget = trackingTargets.filterValues { it == target }.keys

            for (hunterId in huntersTrackingTarget) {
                trackingTasks[hunterId]?.cancel()
                trackingTasks.remove(hunterId)
                trackingTargets.remove(hunterId)

                val trackingPlayer = Bukkit.getPlayer(hunterId)
                trackingPlayer?.let { player ->
                    // Manually remove tracking compass items
                    val inv = player.inventory
                    for (slot in 0 until inv.size) {
                        val item = inv.getItem(slot)
                        if (isTrackingPuck(item)) {
                            inv.clear(slot)
                        }
                    }
                    player.sendMessage("§aTracking of ${Bukkit.getOfflinePlayer(target).name ?: "Unknown"} has been stopped.")
                }
            }

            saveTrackingData()
        } else if (trackingTasks.contains(hunter.uniqueId)) {
            trackingTasks[hunter.uniqueId]?.cancel()
            trackingTasks.remove(hunter.uniqueId)
            trackingTargets.remove(hunter.uniqueId)

            hunter.let { player ->
                val inv = player.inventory
                for (slot in 0 until inv.size) {
                    val item = inv.getItem(slot)
                    if (isTrackingPuck(item)) {
                        inv.clear(slot)
                    }
                }
            }

            saveTrackingData()
            hunter.sendMessage("§aTracking stopped.")
        }
    }

    private fun isTrackingPuck(item: ItemStack?): Boolean {
        if (item == null || item.type != Material.valueOf("swc:fob") || item.type != Material.COMPASS) return false
        val meta = item.itemMeta ?: return false
        val lore = meta.lore ?: return false
        return lore.any { it.contains("Tracking:") }
    }

    fun removeBounty(requester: OfflinePlayer, target: OfflinePlayer): Boolean {
        val stmt = conn.prepareStatement("SELECT * FROM bounties WHERE target = ?")
        stmt.setString(1, target.uniqueId.toString())
        val rs = stmt.executeQuery()
        while (rs.next()) {
            val placerId = UUID.fromString(rs.getString("placer"))
            val bountyId = rs.getInt("id")
            val bounty = getBounty(target as Player)
            economy.depositPlayer(Bukkit.getOfflinePlayer(placerId), bounty?.reward ?: 0.0)
            stopTracking(Bukkit.getPlayer(placerId))
            if (requester.uniqueId == placerId || requester.isOp) {
                val deleteStmt = conn.prepareStatement("DELETE FROM bounties WHERE id = ?")
                deleteStmt.setInt(1, bountyId)
                deleteStmt.executeUpdate()

                // Cancel the expiry task if running
                expiryTasks[bountyId]?.cancel()
                expiryTasks.remove(bountyId)

                return true
            } else {
                return false
            }
        }
        return false
    }

    fun hasBounty(target: Player): Boolean {
        val stmt = conn.prepareStatement("SELECT COUNT(*) FROM bounties WHERE target = ? AND expires > ?")
        stmt.setString(1, target.uniqueId.toString())
        stmt.setTimestamp(2, Timestamp.from(Instant.now()))
        val rs = stmt.executeQuery()
        return rs.next() && rs.getInt(1) > 0
    }

    fun getActiveBounties(): List<Bounty> {
        val stmt = conn.prepareStatement("SELECT * FROM bounties WHERE expires > ?")
        stmt.setTimestamp(1, Timestamp.from(Instant.now()))
        val rs: ResultSet = stmt.executeQuery()
        val list = mutableListOf<Bounty>()
        while (rs.next()) {
            val loc = Location(
                Bukkit.getWorld(rs.getString("world")),
                rs.getDouble("x"),
                rs.getDouble("y"),
                rs.getDouble("z")
            )
            list.add(
                Bounty(
                    rs.getInt("id"),
                    UUID.fromString(rs.getString("placer")),
                    UUID.fromString(rs.getString("target")),
                    rs.getDouble("reward"),
                    rs.getTimestamp("expires").toInstant(),
                    loc
                )
            )
        }
        return list
    }

    fun generateRandomBounty(op: Player, reward: Double, defaultDuration: Duration): Bounty? {
        val onlinePlayers = Bukkit.getOnlinePlayers().filter { it.uniqueId != op.uniqueId }
        if (onlinePlayers.isEmpty()) return null
        val randomTarget = onlinePlayers.random()
        placeBounty(op, randomTarget, reward, defaultDuration)
        return getActiveBounties().find { it.target == randomTarget.uniqueId }
    }

    fun completeBounty(hunter: Player, target: Player): Boolean {
        val stmt = conn.prepareStatement("SELECT * FROM bounties WHERE target = ? AND expires > ? LIMIT 1")
        stmt.setString(1, target.uniqueId.toString())
        stmt.setTimestamp(2, Timestamp.from(Instant.now()))
        val rs = stmt.executeQuery()
        if (rs.next()) {
            val id = rs.getInt("id")
            val reward = rs.getDouble("reward")
            hunter.sendMessage("$sysMsg You have claimed the bounty on ${target.name} and received $${reward}!")

            // Collection System
            economy.depositPlayer(hunter, reward)

            conn.prepareStatement("DELETE FROM bounties WHERE id = ?").apply {
                setInt(1, id)
                executeUpdate()
            }
            // Cancel expiry task if any
            expiryTasks[id]?.cancel()
            expiryTasks.remove(id)
            stopTracking(hunter)
            return true
        }
        return false
    }

    fun close() {
        conn.close()
    }

    fun scheduleBountyExpiry(bounty: Bounty) {
        val now = Instant.now()
        val delayTicks = Duration.between(now, bounty.expires).toMillis() / 50

        val task = object : BukkitRunnable() {
            override fun run() {
                if (removeExpiredBounty(bounty)) {
                    Bukkit.getOnlinePlayers().forEach {
                        it.sendMessage("$sysMsg The bounty on ${Bukkit.getOfflinePlayer(bounty.target).name ?: "Unknown"} has expired.")
                    }
                    expiryTasks.remove(bounty.id)
                }
            }
        }

        task.runTaskLater(plugin, delayTicks)
        expiryTasks[bounty.id] = task
    }

    fun removeExpiredBounty(bounty: Bounty): Boolean {
        economy.depositPlayer(Bukkit.getOfflinePlayer(bounty.placer), bounty.reward)
        stopTracking(Bukkit.getPlayer(bounty.placer))
        val stmt = conn.prepareStatement("SELECT * FROM bounties WHERE id = ?")
        stmt.setInt(1, bounty.id)
        val rs = stmt.executeQuery()
        if (rs.next()) {
            val deleteStmt = conn.prepareStatement("DELETE FROM bounties WHERE id = ?")
            deleteStmt.setInt(1, bounty.id)
            deleteStmt.executeUpdate()
            return true
        }
        return false
    }

    fun updateBountyLocation(target: Player) {
        val stmt = conn.prepareStatement("""
        UPDATE bounties 
        SET world = ?, x = ?, y = ?, z = ?
        WHERE target = ? AND expires > ?
    """.trimIndent())

        val loc = target.location
        stmt.setString(1, loc.world.name)
        stmt.setDouble(2, loc.x)
        stmt.setDouble(3, loc.y)
        stmt.setDouble(4, loc.z)
        stmt.setString(5, target.uniqueId.toString())
        stmt.setTimestamp(6, Timestamp.from(Instant.now()))
        stmt.executeUpdate()
    }

    fun getBounty(target: Player): Bounty? {
        val stmt = conn.prepareStatement("SELECT * FROM bounties WHERE target = ? AND expires > ? LIMIT 1")
        stmt.setString(1, target.uniqueId.toString())
        stmt.setTimestamp(2, Timestamp.from(Instant.now()))
        val rs = stmt.executeQuery()
        return if (rs.next()) {
            val loc = Location(
                Bukkit.getWorld(rs.getString("world")),
                rs.getDouble("x"),
                rs.getDouble("y"),
                rs.getDouble("z")
            )
            return Bounty(
                id = rs.getInt("id"),
                placer = UUID.fromString(rs.getString("placer")),
                target = UUID.fromString(rs.getString("target")),
                reward = rs.getDouble("reward"),
                expires = rs.getTimestamp("expires").toInstant(),
                location = loc
            )
        } else {
            null
        }
    }

    // Save tracking data to tracking.yml
    fun saveTrackingData() {
        val mapToSave = trackingTargets.mapKeys { it.key.toString() }
            .mapValues { it.value.toString() }
        trackingConfig.set("tracking", mapToSave)
        trackingConfig.save(trackingFile)
    }

    // Load tracking data and resume tracking tasks
    fun loadTrackingData() {
        val savedTracking = trackingConfig.getConfigurationSection("tracking") ?: return
        for (hunterIdStr in savedTracking.getKeys(false)) {
            val targetIdStr = savedTracking.getString(hunterIdStr) ?: continue
            val hunter = Bukkit.getPlayer(UUID.fromString(hunterIdStr)) ?: continue
            val target = Bukkit.getPlayer(UUID.fromString(targetIdStr)) ?: continue
            startTracking(hunter, target)
        }
    }

    // Load and schedule expiry tasks on plugin start
    fun loadAndScheduleExpiryTasks() {
        val activeBounties = getActiveBounties()
        activeBounties.forEach { bounty ->
            scheduleBountyExpiry(bounty)
        }
    }


    fun openBountyBoard(player: Player, page: Int) {
        val allBounties = getActiveBounties()
        if (allBounties.isEmpty()) {
            player.sendMessage("$sysMsg No active bounties.")
            return
        }

        val bountiesPerPage = 45
        val totalPages = (allBounties.size + bountiesPerPage - 1) / bountiesPerPage
        val safePage = page.coerceIn(1, totalPages)
        val start = (safePage - 1) * bountiesPerPage
        val end = (start + bountiesPerPage).coerceAtMost(allBounties.size)
        val pagedBounties = allBounties.subList(start, end)

        val inventory = Bukkit.createInventory(null, 54, "Bounties Page $safePage")

        for ((index, bounty) in pagedBounties.withIndex()) {
            val placerName = Bukkit.getOfflinePlayer(bounty.placer).name ?: "Unknown"
            val targetName = Bukkit.getOfflinePlayer(bounty.target).name ?: "Unknown"
            val timeLeft = Duration.between(Instant.now(), bounty.expires)

            val skull = ItemStack(Material.SKULL_ITEM, 1, 3)
            val meta = skull.itemMeta
            meta?.displayName = "§e$placerName → $targetName"
            meta?.lore = listOf(
                "§7Reward: §a$${bounty.reward}",
                "§7Expires in: §f${formatDuration(timeLeft)}",
                "§8ID: ${bounty.id}",
                if (player.hasPermission("swcb.admin.bounty.claim")) "§cClick to claim" else "§7"
            )
            skull.itemMeta = meta
            inventory.setItem(index, skull)
        }

        // Pagination buttons
        if (safePage > 1) {
            val back = ItemStack(Material.ARROW)
            val meta = back.itemMeta
            meta?.displayName = "§aPrevious Page"
            back.itemMeta = meta
            inventory.setItem(45, back)
        }

        if (safePage < totalPages) {
            val next = ItemStack(Material.ARROW)
            val meta = next.itemMeta
            meta?.displayName = "§aNext Page"
            next.itemMeta = meta
            inventory.setItem(53, next)
        }

        player.openInventory(inventory)
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