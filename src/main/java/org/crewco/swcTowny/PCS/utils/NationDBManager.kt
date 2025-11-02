package org.crewco.swcTowny.PCS.utils

import com.palmergames.bukkit.towny.`object`.Nation
import com.palmergames.bukkit.towny.`object`.Resident
import com.palmergames.bukkit.towny.`object`.TownyUniverse

import java.io.File
import java.sql.Connection
import java.sql.DriverManager
import java.sql.PreparedStatement
import java.sql.SQLException
import java.sql.Statement

class NationDBManager(private val dataFolder: File) {
    private val dbFile = File(dataFolder, "nations.db")
    private var connection: Connection? = null

    init {
        if (!dbFile.exists()) {
            println("[NationDB] Creating database file at: ${dbFile.absolutePath}")
            dbFile.parentFile.mkdirs()
            dbFile.createNewFile()
        }
        connect()
        createTables()
    }

    private fun connect() {
        try {
            Class.forName("org.sqlite.JDBC")
            connection = DriverManager.getConnection("jdbc:sqlite:${dbFile.absolutePath}")
            println("[NationDB] Connection to SQLite established.")
        } catch (e: Exception) {
            println("[NationDB] Failed to connect to SQLite: ${e.message}")
            e.printStackTrace()
        }
    }

    private fun createTables() {
        val sql = """
            CREATE TABLE IF NOT EXISTS nations (
                name TEXT PRIMARY KEY
            );

            CREATE TABLE IF NOT EXISTS towns (
                name TEXT PRIMARY KEY,
                nation_name TEXT,
                world_name TEXT,
                total_blocks INTEGER,
                FOREIGN KEY(nation_name) REFERENCES nations(name) ON DELETE CASCADE
            );

            CREATE TABLE IF NOT EXISTS leading_nations (
                world_name TEXT PRIMARY KEY,
                nation_name TEXT,
                town_count INTEGER,
                total_blocks INTEGER
            );
            
            CREATE TABLE IF NOT EXISTS claim_cooldowns (
                nation_name TEXT,
                world_name TEXT,
                last_claim INTEGER,
                PRIMARY KEY(nation_name, world_name)
            );
            
        """.trimIndent()

        var stmt: Statement? = null
        try {
            stmt = connection!!.createStatement()
            stmt.executeUpdate(sql)
            println("[NationDB] Tables created (if not already).")
        } catch (e: SQLException) {
            println("[NationDB] Failed to create tables: ${e.message}")
            e.printStackTrace()
        } finally {
            try { stmt?.close() } catch (_: Exception) {}
        }
    }

    fun saveNation(nation: NationData) {
        println("[NationDB] Saving nation: ${nation.name}")
        var nationStmt: PreparedStatement? = null
        try {
            nationStmt = connection!!.prepareStatement("REPLACE INTO nations (name) VALUES (?);")
            nationStmt.setString(1, nation.name)
            nationStmt.executeUpdate()
            println("[NationDB] Inserted/Updated nation: ${nation.name}")
        } catch (e: SQLException) {
            println("[NationDB] Failed to save nation ${nation.name}: ${e.message}")
            e.printStackTrace()
        } finally {
            try { nationStmt?.close() } catch (_: Exception) {}
        }

        nation.towns.forEach { town ->
            var townStmt: PreparedStatement? = null
            try {
                townStmt = connection!!.prepareStatement(
                    "REPLACE INTO towns (name, nation_name, world_name, total_blocks) VALUES (?, ?, ?, ?);"
                )
                townStmt.setString(1, town.name)
                townStmt.setString(2, nation.name)
                townStmt.setString(3, town.world)
                townStmt.setInt(4, town.totalBlocks)
                townStmt.executeUpdate()
                println("[NationDB] Inserted/Updated town: ${town.name} in world: ${town.world} with ${town.totalBlocks} blocks")
            } catch (e: SQLException) {
                println("[NationDB] Failed to save town ${town.name}: ${e.message}")
                e.printStackTrace()
            } finally {
                try { townStmt?.close() } catch (_: Exception) {}
            }
        }
    }

    fun updateLeadingNations() {
        val worldNationStats = mutableMapOf<String, MutableMap<String, Pair<Int, Int>>>()

        var stmt: Statement? = null
        var rs: java.sql.ResultSet? = null

        try {
            stmt = connection!!.createStatement()
            rs = stmt.executeQuery("SELECT nation_name, world_name, total_blocks FROM towns")

            while (rs.next()) {
                val nationName = rs.getString("nation_name")
                val worldName = rs.getString("world_name")
                val blocks = rs.getInt("total_blocks")

                val nationStats = worldNationStats.getOrPut(worldName) { mutableMapOf() }
                val (townCount, totalBlocks) = nationStats[nationName] ?: Pair(0, 0)
                nationStats[nationName] = Pair(townCount + 1, totalBlocks + blocks)
            }
        } catch (e: SQLException) {
            println("[NationDB] Failed during reading towns: ${e.message}")
            e.printStackTrace()
            return
        } finally {
            try { rs?.close() } catch (_: Exception) {}
            try { stmt?.close() } catch (_: Exception) {}
        }

        try {
            for ((world, nationMap) in worldNationStats) {
                var leaderEntry: Map.Entry<String, Pair<Int, Int>>? = null
                for (entry in nationMap) {
                    if (leaderEntry == null) {
                        leaderEntry = entry
                    } else {
                        val current = entry.value
                        val best = leaderEntry.value
                        // Compare by townCount desc, then totalBlocks desc
                        if (current.first > best.first || (current.first == best.first && current.second > best.second)) {
                            leaderEntry = entry
                        }
                    }
                }

                if (leaderEntry != null) {
                    val (nationName, stats) = leaderEntry
                    val (townCount, totalBlocks) = stats

                    var ps: PreparedStatement? = null
                    try {
                        ps = connection!!.prepareStatement(
                            """
                            REPLACE INTO leading_nations (world_name, nation_name, town_count, total_blocks)
                            VALUES (?, ?, ?, ?)
                            """.trimIndent()
                        )
                        ps.setString(1, world)
                        ps.setString(2, nationName)
                        ps.setInt(3, townCount)
                        ps.setInt(4, totalBlocks)
                        ps.executeUpdate()
                    } catch (e: SQLException) {
                        println("[NationDB] Failed to update leading nation for world $world: ${e.message}")
                        e.printStackTrace()
                    } finally {
                        try { ps?.close() } catch (_: Exception) {}
                    }
                }
            }
            println("[NationDB] Updated leading nations per world.")
        } catch (e: Exception) {
            println("[NationDB] Error updating leading nations: ${e.message}")
            e.printStackTrace()
        }
    }

    fun getLeadingNationForWorld(world: String): NationLeader? {
        var ps: PreparedStatement? = null
        var rs: java.sql.ResultSet? = null
        try {
            ps = connection!!.prepareStatement(
                "SELECT nation_name, town_count, total_blocks FROM leading_nations WHERE world_name = ?"
            )
            ps.setString(1, world)
            rs = ps.executeQuery()

            if (rs.next()) {
                return NationLeader(
                    worldName = world,
                    nationName = rs.getString("nation_name"),
                    townCount = rs.getInt("town_count"),
                    totalBlocks = rs.getInt("total_blocks")
                )
            }
        } catch (e: SQLException) {
            println("[NationDB] Failed to get leading nation for world $world: ${e.message}")
            e.printStackTrace()
        } finally {
            try { rs?.close() } catch (_: Exception) {}
            try { ps?.close() } catch (_: Exception) {}
        }
        return null
    }

    fun getNation(name: String): NationData? {
        println("[NationDB] Fetching nation: $name")
        var nationStmt: PreparedStatement? = null
        var nationRs: java.sql.ResultSet? = null
        try {
            nationStmt = connection!!.prepareStatement("SELECT name FROM nations WHERE name = ?")
            nationStmt.setString(1, name)
            nationRs = nationStmt.executeQuery()

            if (!nationRs.next()) {
                println("[NationDB] Nation not found: $name")
                return null
            }
        } catch (e: SQLException) {
            println("[NationDB] Error loading nation $name: ${e.message}")
            e.printStackTrace()
            return null
        } finally {
            try { nationRs?.close() } catch (_: Exception) {}
            try { nationStmt?.close() } catch (_: Exception) {}
        }

        val towns = mutableListOf<TownData>()
        var townsStmt: PreparedStatement? = null
        var townRs: java.sql.ResultSet? = null
        try {
            townsStmt = connection!!.prepareStatement(
                "SELECT name, world_name, total_blocks FROM towns WHERE nation_name = ?"
            )
            townsStmt.setString(1, name)
            townRs = townsStmt.executeQuery()

            while (townRs.next()) {
                val town = TownData(
                    name = townRs.getString("name"),
                    world = townRs.getString("world_name"),
                    totalBlocks = townRs.getInt("total_blocks")
                )
                towns.add(town)
                println("[NationDB] Loaded town: ${town.name} (${town.world}) with ${town.totalBlocks} blocks")
            }

            println("[NationDB] Nation loaded: $name with ${towns.size} towns")
            return NationData(name, towns)
        } catch (e: SQLException) {
            println("[NationDB] Error loading towns for nation $name: ${e.message}")
            e.printStackTrace()
            return null
        } finally {
            try { townRs?.close() } catch (_: Exception) {}
            try { townsStmt?.close() } catch (_: Exception) {}
        }
    }

    fun close() {
        println("[NationDB] Closing database connection.")
        try {
            connection?.close()
        } catch (e: SQLException) {
            e.printStackTrace()
        }
    }


    fun getOverallLeadingNation(): NationLeader? {
        var stmt: Statement? = null
        var rs: java.sql.ResultSet? = null

        try {
            stmt = connection!!.createStatement()
            rs = stmt.executeQuery("""
            SELECT nation_name, SUM(town_count) as total_towns, SUM(total_blocks) as total_blocks
            FROM leading_nations
            GROUP BY nation_name
            ORDER BY total_towns DESC, total_blocks DESC
            LIMIT 1
        """.trimIndent())

            if (rs.next()) {
                return NationLeader(
                    worldName = "ALL",
                    nationName = rs.getString("nation_name"),
                    townCount = rs.getInt("total_towns"),
                    totalBlocks = rs.getInt("total_blocks")
                )
            }

        } catch (e: SQLException) {
            println("[NationDB] Failed to get overall leading nation: ${e.message}")
            e.printStackTrace()
        } finally {
            try { rs?.close() } catch (_: Exception) {}
            try { stmt?.close() } catch (_: Exception) {}
        }

        return null
    }

    fun getKingNameOfNation(nationName: String): String? {
        return try {
            val nation: Nation = TownyUniverse.getDataSource().getNation(nationName)
            val king: Resident? = nation.residents.find { it.isKing }
            king?.name
        } catch (e: Exception) {
            println("[NationDB] Error retrieving king of $nationName: ${e.message}")
            null
        }
    }

    // Claim Functions

    fun getLastClaim(nationName: String, worldName: String): Long {
        var ps: PreparedStatement? = null
        var rs: java.sql.ResultSet? = null
        try {
            ps = connection!!.prepareStatement(
                "SELECT last_claim FROM claim_cooldowns WHERE nation_name = ? AND world_name = ?"
            )
            ps.setString(1, nationName)
            ps.setString(2, worldName)
            rs = ps.executeQuery()
            if (rs.next()) return rs.getLong("last_claim")
        } catch (e: SQLException) {
            println("[NationDB] Failed to get last claim: ${e.message}")
            e.printStackTrace()
        } finally {
            try { rs?.close() } catch (_: Exception) {}
            try { ps?.close() } catch (_: Exception) {}
        }
        return 0L
    }

    fun setLastClaim(nationName: String, worldName: String, timestamp: Long) {
        var ps: PreparedStatement? = null
        try {
            ps = connection!!.prepareStatement(
                """
            REPLACE INTO claim_cooldowns (nation_name, world_name, last_claim)
            VALUES (?, ?, ?)
            """.trimIndent()
            )
            ps.setString(1, nationName)
            ps.setString(2, worldName)
            ps.setLong(3, timestamp)
            ps.executeUpdate()
        } catch (e: SQLException) {
            println("[NationDB] Failed to set last claim: ${e.message}")
            e.printStackTrace()
        } finally {
            try { ps?.close() } catch (_: Exception) {}
        }
    }

    fun deleteNation(nationName: String) {
        var nationStmt: PreparedStatement? = null
        var townsStmt: PreparedStatement? = null
        var cooldownStmt: PreparedStatement? = null
        var leadingStmt: PreparedStatement? = null

        try {
            // Delete from towns
            townsStmt = connection!!.prepareStatement("DELETE FROM towns WHERE nation_name = ?;")
            townsStmt.setString(1, nationName)
            townsStmt.executeUpdate()

            // Delete from claim cooldowns
            cooldownStmt = connection!!.prepareStatement("DELETE FROM claim_cooldowns WHERE nation_name = ?;")
            cooldownStmt.setString(1, nationName)
            cooldownStmt.executeUpdate()

            // Delete from leading nations
            leadingStmt = connection!!.prepareStatement("DELETE FROM leading_nations WHERE nation_name = ?;")
            leadingStmt.setString(1, nationName)
            leadingStmt.executeUpdate()

            // Delete from nations
            nationStmt = connection!!.prepareStatement("DELETE FROM nations WHERE name = ?;")
            nationStmt.setString(1, nationName)
            nationStmt.executeUpdate()

            println("[NationDB] Nation '$nationName' and related data have been deleted.")
        } catch (e: SQLException) {
            println("[NationDB] Failed to delete nation $nationName: ${e.message}")
            e.printStackTrace()
        } finally {
            try { townsStmt?.close() } catch (_: Exception) {}
            try { cooldownStmt?.close() } catch (_: Exception) {}
            try { leadingStmt?.close() } catch (_: Exception) {}
            try { nationStmt?.close() } catch (_: Exception) {}
        }
    }




    data class NationData(
        val name: String,
        val towns: List<TownData>
    )

    data class TownData(
        val name: String,
        val world: String,
        val totalBlocks: Int
    )

    data class NationLeader(
        val worldName: String,
        val nationName: String,
        val townCount: Int,
        val totalBlocks: Int
    )
}
