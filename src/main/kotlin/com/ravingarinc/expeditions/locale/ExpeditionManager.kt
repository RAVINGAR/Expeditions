package com.ravingarinc.expeditions.locale

import com.ravingarinc.api.I
import com.ravingarinc.api.module.RavinPlugin
import com.ravingarinc.api.module.SuspendingModule
import com.ravingarinc.api.module.warn
import com.ravingarinc.expeditions.api.*
import com.ravingarinc.expeditions.integration.MultiverseHandler
import com.ravingarinc.expeditions.locale.type.Expedition
import com.ravingarinc.expeditions.locale.type.ExtractionZone
import com.ravingarinc.expeditions.locale.type.PointOfInterest
import com.ravingarinc.expeditions.persistent.ConfigManager
import com.ravingarinc.expeditions.play.item.LootTable
import com.ravingarinc.expeditions.play.mob.EmptyMobType
import com.ravingarinc.expeditions.play.mob.MobType
import org.bukkit.ChatColor
import org.bukkit.Material
import org.bukkit.map.MapCursor
import org.bukkit.util.BlockVector
import java.util.*
import java.util.logging.Level

class ExpeditionManager(plugin: RavinPlugin) : SuspendingModule(ExpeditionManager::class.java, plugin, true, ConfigManager::class.java, MultiverseHandler::class.java) {
    private val maps: MutableList<Expedition> = ArrayList()

    private val lootTables: MutableMap<String, LootTable> = Hashtable()
    private val mobTypes: MutableMap<String, MobType> = Hashtable()

    private var extractionTime: Long = 0L
    private var lootBlock: Material = Material.BARREL

    override suspend fun suspendLoad() {
        val manager = plugin.getModule(ConfigManager::class.java)

        manager.config.consume("general") {
            lootBlock = it.getMaterial("loot-chest-block") ?: Material.BARREL
            extractionTime = it.getDuration("extraction-time") ?: 0L
        }

        loadLootTables(manager)
        loadExpeditions(manager)
    }

    fun getLootBlock() : Material {
        return lootBlock
    }

    private fun loadLootTables(manager: ConfigManager) {
        manager.config.config.getConfigurationSection("loot-tables")?.let { section ->
            for(key in section.getKeys(false)) {
                section.getDropTable(key).let {
                    lootTables[key] = it
                }
            }
        }
    }

    private fun loadExpeditions(manager: ConfigManager) {
        for(entry in manager.getMapConfigs()) {
            val name = entry.key
            val it = entry.value
            val world = it.getWorld("map.world-name") ?: continue
            val pair = it.getIntPair("map.centre") ?: Pair(0, 0)
            val calmDuration = it.getDuration("map.calm-duration") ?: continue
            val stormDuration = it.getDuration("map.storm-duration") ?: continue

            if(getMapByIdentifier(name) != null) {
                warn("Could not load expedition with duplicate id of '${name}'!")
                continue
            }
            if(getMapByWorld(world.name) != null) {
                warn("Could not load expedition with duplicate world of '${world.name}'")
                continue
            }

            val spawnLocations = buildList { it.getStringList("map.spawn-locations").forEach { loc ->
                parseBlockVector(loc)?.let { vec -> this.add(vec) }
            } }
            val permission: String? = it.getString("map.permission")

            val onJoin = buildList {
                it.getStringList("map.on-join-commands").forEach { line ->
                    this.add(if(line.startsWith("/")) line.substring(1) else line)
                }
            }
            val onExtract = buildList {
                it.getStringList("map.on-extract-commands").forEach { line ->
                    this.add(if(line.startsWith("/")) line.substring(1) else line)
                }
            }
            /*
            val mobList = buildList {
            (poi["mobs"] as? List<*>)?.let { list ->
                for (i in list) {
                    parseMob(i as String)?.let {
                        this.add(Triple(registerMobType(it.first), it.second, it.third))
                    }
                }
            }
        }
             */
            val mobList = buildList {
                it.getStringList("map.random-mobs").forEach { i ->
                    parseMob(i)?.let { this.add(Triple(registerMobType(it.first), it.second, it.third))}
                }
            }

            val map = Expedition(
                name,
                it.getStringList("map.description"),
                it.getString("map.name") ?: name,
                if(permission != null && permission.isEmpty()) null else permission,
                ChatColor.translateAlternateColorCodes('&',it.getString("map.locked-message") ?: ""),
                world,
                it.getInt("map.max-players", 1),
                pair.first,
                pair.second,
                it.getInt("map.radius", 125),
                it.getInt("map.mob-spawn-amount", 0),
                calmDuration, stormDuration,
                it.getDuration("map.mob-spawn-interval") ?: -1L,
                it.getDouble("map.mob-spawn-modifier", 1.0),
                it.getDuration("map.loot-respawn-interval") ?: -1L,
                it.getDouble("map.loot-respawn-modifier", 1.0),
                lootBlock, extractionTime, it.getDouble("map.loot-chest-range", 8.0),
                spawnLocations, onJoin, onExtract, mobList,
                it.getDuration("map.random-mob-spawn-interval") ?: -1L,
                it.getInt("map.spawns-per-interval-per-player", 0),
                it.getPercentage("map.random-mob-spawn-chance"),
                it.getInt("map.max-mobs-per-chunk", 0),
                it.getInt("map.lowest-y", 0),
                it.getInt("map.highest-y", 324)
            )
            for(poi in it.getMapList("points-of-interest")) {
                loadPointOfInterest(name, poi)?.let { map.addArea(it) }
            }
            for(zone in it.getMapList("extraction-zones")) {
                loadExtractionZone(name, zone)?.let { map.addArea(it) }
            }

            I.log(Level.INFO, "Loaded expedition map '${name}'")
            maps.add(map)
        }
    }

    private fun loadPointOfInterest(mapName: String, poi: MutableMap<*, *>): PointOfInterest? {
        val startLoc = parseBlockVector(poi["start-location"].toString()) ?: return null
        val endLoc = parseBlockVector(poi["end-location"].toString()) ?: return null

        val lootList = buildList {
            (poi["loot-types"] as? List<*>)?.let { list ->
                for (i in list) {
                    val entry = (i as String)
                    val split = entry.split(",".toRegex(), limit = 2)
                    val id = split[0]
                    val weight = split[1].toDoubleOrNull()
                    if (weight == null) {
                        warn("Could not parse weight for loot-type '$entry'")
                        continue
                    }
                    val table = lootTables[id]
                    if (table == null) {
                        warn("Could not find loot table with id '$id' in area $name for map $mapName")
                        continue
                    }
                    this.add(Pair(table, weight))
                }
            }
        }
        val lootLoc = buildList {
            (poi["loot-locations"] as? List<*>)?.let { list ->
                for (i in list) {
                    parseBlockVector(i as String)?.let { this.add(it) }
                }
            }
        }

        val mobList = buildList {
            (poi["mobs"] as? List<*>)?.let { list ->
                for (i in list) {
                    parseMob(i as String)?.let {
                        this.add(Triple(registerMobType(it.first), it.second, it.third))
                    }
                }
            }
        }

        val mobLoc = buildList {
            (poi["mob-locations"] as? List<*>)?.let { list ->
                for (i in list) {
                    parseBlockVector(i as String)?.let { this.add(it) }
                }
            }
        }
        val boss: MobType = poi["boss"]?.let {
            val type = parseMobType(it.toString())
            if (type != null) {
                return@let registerMobType(type)
            }
            return@let null
        } ?: EmptyMobType

        val npcIdentifier: String? = poi["npc-identifier"]?.toString()
        val npcSpawnLoc: BlockVector? = parseBlockVector(poi["npc-spawn-location"].toString())
        val npcFollowText: String = ChatColor.translateAlternateColorCodes('&',poi["npc-follow-text"]?.toString() ?: "")
        val npcRefollowText: String = ChatColor.translateAlternateColorCodes('&',poi["npc-refollow-text"]?.toString() ?: "")
        val npcUnfollowText: String = ChatColor.translateAlternateColorCodes('&',poi["npc-unfollow-text"]?.toString() ?: "")
        val npcOnSpawn: List<String> = buildList {
            (poi["npc-on-spawn-commands"] as? List<*>)?.let { list ->
                for(i in list) {
                    val str = i.toString()
                    this.add(if(str.startsWith("/")) str.substring(1) else str)
                }
            }
        }
        val npcOnExtract: List<String> = buildList {
            (poi["npc-on-extract-commands"] as? List<*>)?.let { list ->
                for(i in list) {
                    val str = i.toString()
                    this.add(if(str.startsWith("/")) str.substring(1) else str)
                }
            }
        }
        return PointOfInterest(
            poi["name"].toString(),
            startLoc,
            endLoc,
            poi["loot-chest-limit"].toString().toDoubleOrNull() ?: 0.0,
            parsePercentage(poi["loot-chance"].toString()),
            lootList,
            lootLoc,
            parsePercentage(poi["mob-spawn-chance"].toString()),
            poi["max-mobs"].toString().toIntOrNull() ?: 0,
            mobList,
            mobLoc,
            boss,
            poi["boss-level"].toString().toIntOrNull() ?: 1,
            parsePercentage(poi["boss-spawn-chance"].toString()),
            parseBlockVector(poi["boss-spawn-location"].toString()),
            poi["boss-cooldown"].toString().toLongOrNull() ?: 120,
            npcIdentifier,
            npcSpawnLoc,
            npcOnSpawn,
            npcOnExtract,
            npcFollowText,
            npcRefollowText,
            npcUnfollowText,
            parseCursor(poi["cursor-type"]?.toString(), MapCursor.Type.MANSION)
        )
    }
    
    private fun loadExtractionZone(mapName: String, zone: MutableMap<*, *>) : ExtractionZone? {
        val startLoc = parseBlockVector(zone["start-location"].toString()) ?: return null
        val endLoc = parseBlockVector(zone["end-location"].toString()) ?: return null

        val lootList = buildList {
            (zone["loot-types"] as? List<*>)?.let { list ->
                for (i in list) {
                    val entry = (i as String)
                    val split = entry.split(",".toRegex(), limit = 2)
                    val id = split[0]
                    val weight = split[1].toDoubleOrNull()
                    if (weight == null) {
                        warn("Could not parse weight for loot-type '$entry'")
                        continue
                    }
                    val table = lootTables[id]
                    if (table == null) {
                        warn("Could not find loot table with id '$id' in area $name for map $mapName")
                        continue
                    }
                    this.add(Pair(table, weight))
                }
            }
        }
        val lootLoc = buildList {
            (zone["loot-locations"] as? List<*>)?.let { list ->
                for (i in list) {
                    parseBlockVector(i as String)?.let { this.add(it) }
                }
            }
        }

        val mobList = buildList {
            (zone["mobs"] as? List<*>)?.let { list ->
                for (i in list) {
                    parseMob(i as String)?.let {
                        this.add(Triple(registerMobType(it.first), it.second, it.third))
                    }
                }
            }
        }

        val mobLoc = buildList {
            (zone["mob-locations"] as? List<*>)?.let { list ->
                for (i in list) {
                    parseBlockVector(i as String)?.let { this.add(it) }
                }
            }
        }
        val boss: MobType = zone["boss"]?.let {
            val type = parseMobType(it.toString())
            if (type != null) {
                return@let registerMobType(type)
            }
            return@let null
        } ?: EmptyMobType

        val npcIdentifier: String? = zone["npc-identifier"]?.toString()
        val npcSpawnLoc: BlockVector? = parseBlockVector(zone["npc-spawn-location"].toString())
        val npcFollowText: String = ChatColor.translateAlternateColorCodes('&',zone["npc-follow-text"]?.toString() ?: "")
        val npcRefollowText: String = ChatColor.translateAlternateColorCodes('&',zone["npc-refollow-text"]?.toString() ?: "")
        val npcUnfollowText: String = ChatColor.translateAlternateColorCodes('&',zone["npc-unfollow-text"]?.toString() ?: "")
        val npcOnSpawn: List<String> = buildList {
            (zone["npc-on-spawn-commands"] as? List<*>)?.let { list ->
                for(i in list) {
                    val str = i.toString()
                    this.add(if(str.startsWith("/")) str.substring(1) else str)
                }
            }
        }
        val npcOnExtract: List<String> = buildList {
            (zone["npc-on-extract-commands"] as? List<*>)?.let { list ->
                for(i in list) {
                    val str = i.toString()
                    this.add(if(str.startsWith("/")) str.substring(1) else str)
                }
            }
        }

        return ExtractionZone(
            parsePercentage(zone["chance"].toString()),
            parseBlockVector(zone["beacon-loc"].toString()),
            zone["name"].toString(),
            startLoc,
            endLoc,
            zone["loot-chest-limit"].toString().toDoubleOrNull() ?: 0.0,
            parsePercentage(zone["loot-chance"].toString()),
            lootList,
            lootLoc,
            parsePercentage(zone["mob-spawn-chance"].toString()),
            zone["max-mobs"].toString().toIntOrNull() ?: 0,
            mobList,
            mobLoc,
            boss,
            zone["boss-level"].toString().toIntOrNull() ?: 1,
            parsePercentage(zone["boss-spawn-chance"].toString()),
            parseBlockVector(zone["boss-spawn-location"].toString()),
            zone["boss-cooldown"].toString().toLongOrNull() ?: 120,
            npcIdentifier,
            npcSpawnLoc,
            npcOnSpawn,
            npcOnExtract,
            npcFollowText,
            npcRefollowText,
            npcUnfollowText,
            parseCursor(zone["cursor-type"]?.toString(), MapCursor.Type.BANNER_GREEN)
        )
    }

    fun registerMobType(mobType: MobType) : MobType {
        val existing = mobTypes[mobType.identifier()]
        if(existing == null) {
            mobTypes[mobType.identifier()] = mobType
            return mobType
        }
        return existing
    }

    fun getMapByWorld(worldName: String) : Expedition? {
        for(expedition in maps) {
            if(expedition.world.name.equals(worldName, true)) {
                return expedition
            }
        }
        return null
    }

    fun getMapByIdentifier(identifier: String) : Expedition? {
        for(expedition in maps) {
            if(expedition.identifier.equals(identifier, true)) {
                return expedition
            }
        }
        return null
    }

    fun getMaps() : Collection<Expedition> {
        return maps
    }

    fun reloadMobs() {
        mobTypes.values.forEach { it.reload() }
    }

    override suspend fun suspendCancel() {

        lootTables.clear()
        maps.clear()
        mobTypes.clear()
    }
}