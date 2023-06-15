package com.ravingarinc.expeditions.locale

import com.ravingarinc.api.I
import com.ravingarinc.api.module.RavinPlugin
import com.ravingarinc.api.module.SuspendingModule
import com.ravingarinc.api.module.warn
import com.ravingarinc.expeditions.api.*
import com.ravingarinc.expeditions.integration.MultiverseHandler
import com.ravingarinc.expeditions.integration.WorldGuardHandler
import com.ravingarinc.expeditions.locale.type.Expedition
import com.ravingarinc.expeditions.locale.type.ExtractionZone
import com.ravingarinc.expeditions.locale.type.PointOfInterest
import com.ravingarinc.expeditions.persistent.ConfigManager
import com.ravingarinc.expeditions.play.item.LootTable
import com.ravingarinc.expeditions.play.mob.EmptyMobType
import com.ravingarinc.expeditions.play.mob.MobType
import java.util.Hashtable
import java.util.logging.Level

class ExpeditionManager(plugin: RavinPlugin) : SuspendingModule(ExpeditionManager::class.java, plugin, ConfigManager::class.java, MultiverseHandler::class.java, WorldGuardHandler::class.java) {
    private val maps: MutableList<Expedition> = ArrayList()

    private val lootTables: MutableMap<String, LootTable> = Hashtable()
    private val mobTypes: MutableMap<String, MobType> = Hashtable()

    override suspend fun suspendLoad() {
        val manager = plugin.getModule(ConfigManager::class.java)

        loadLootTables(manager)
        loadExpeditions(manager)
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
        for(it in manager.getMapConfigs()) {
            val world = it.getWorld("map.world-name") ?: continue
            val pair = it.getIntPair("map.centre") ?: Pair(0, 0)
            val calmDuration = it.getDuration("map.calm-duration") ?: continue
            val stormDuration = it.getDuration("map.storm-duration") ?: continue

            if(getMapByIdentifier(it.name) != null) {
                warn("Could not load expedition with duplicate id of '${it.name}'!")
                continue
            }
            if(getMapByWorld(world.name) != null) {
                warn("Could not load expedition with duplicate world of '${world.name}'")
                continue
            }

            val map = Expedition(
                it.name,
                it.getString("map.name") ?: it.name,
                world,
                it.getInt("map.max-players", 1),
                pair.first,
                pair.second,
                it.getInt("map.radius", 125),
                it.getInt("map.mob-spawn-amount", 0),
                calmDuration, stormDuration,
                it.getDuration("mob-spawn-interval") ?: -1L,
                it.getDouble("mob-spawn-modifier", 1.0),
                it.getDuration("loot-respawn-interval") ?: -1L,
                it.getDouble("loot-respawn-modifier", 1.0)
            )
            for(poi in it.getMapList("points-of-interest")) {
                loadPointOfInterest(it.name, poi)?.let { map.addArea(it) }
            }
            for(zone in it.getMapList("extraction-zone")) {
                loadExtractionZone(it.name, zone)?.let { map.addArea(it) }
            }

            I.log(Level.INFO, "Loaded expedition map '${it.name}'")
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
            poi["boss-cooldown"].toString().toLongOrNull() ?: 120
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
            zone["boss-cooldown"].toString().toLongOrNull() ?: 120
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