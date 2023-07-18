package com.ravingarinc.expeditions.persistent

import com.ravingarinc.api.module.RavinPlugin
import com.ravingarinc.api.module.SuspendingModule
import com.ravingarinc.expeditions.api.copyResource
import com.ravingarinc.expeditions.play.instance.CachedPlayer
import org.bukkit.Location
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.configuration.file.YamlConfiguration
import java.io.File
import java.util.*

class ConfigManager(plugin: RavinPlugin) : SuspendingModule(ConfigManager::class.java, plugin) {
    val config: ConfigFile = ConfigFile(plugin, "config.yml")
    val data: ConfigFile = ConfigFile(plugin, "data.yml")

    private val mapConfigurations: MutableMap<String, ConfigurationSection> = Hashtable()


    override suspend fun suspendLoad() {
        val maps = File(plugin.dataFolder, "maps")
        if (!maps.exists()) {
            maps.mkdirs()
            plugin.copyResource(plugin.dataFolder, "map_example.yml", "maps/map_example.yml")
        }
        for (f in maps.listFiles()!!) {
            if (f.isFile && f.name.endsWith(".yml")) {
                mapConfigurations[f.nameWithoutExtension] = (YamlConfiguration.loadConfiguration(f))
            }
        }
        data.reload()
    }

    fun addAbandonedPlayer(uuid: UUID) {
        val list = data.config.getStringList("abandoned-players")
        val str = uuid.toString()
        if(!list.contains(str)) {
            list.add(str)
        }
        data.config.set("abandoned-players", list)
    }

    fun removeAbandonedPlayer(uuid: UUID) {
        val list = data.config.getStringList("abandoned-players")
        list.remove(uuid.toString())
        data.config.set("abandoned-players", list)
    }

    fun getRespawningPlayers() : Collection<CachedPlayer> {
        val collection = ArrayList<CachedPlayer>()
        for(it in data.config.getStringList("respawning-players")) {
            val split = it.split(";".toRegex(), 2)
            if(split.size < 2) continue
            val uuid = UUID.fromString(split[0])
            val loc = split[1].split(",".toRegex(), 4)
            val world = plugin.server.getWorld(loc[0]) ?: continue
            val x = loc[1].toDoubleOrNull() ?: continue
            val y = loc[2].toDoubleOrNull() ?: continue
            val z = loc[3].toDoubleOrNull() ?: continue

            val player = plugin.server.getOfflinePlayer(uuid)
            collection.add(CachedPlayer(player, Location(world, x, y, z)))
        }
        return collection
    }

    fun addRespawningPlayer(cache: CachedPlayer) {
        val list = data.config.getStringList("respawning-players")
        val uuid = cache.player.uniqueId.toString()
        for(line in list) {
            if(line.startsWith(uuid)) {
                return
            }
        }
        val loc = cache.previousLocale
        list.add("$uuid;${loc.world.name},${loc.x},${loc.y},${loc.z}")
        data.config.set("respawning-players", list)
    }

    fun removeRespawningPlayer(cache: CachedPlayer) {
        val list = data.config.getStringList("respawning-players")
        val uuid = cache.player.uniqueId.toString()
        for(line in ArrayList(list)) {
            if(line.startsWith(uuid)) {
                list.remove(line)
                data.config.set("respawning-players", list)
                break
            }
        }
    }

    fun saveData() {
        data.save()
    }

    fun getMapConfigs(): Map<String, ConfigurationSection> {
        return mapConfigurations
    }

    override suspend fun suspendCancel() {
        config.reload()
        data.save()
        mapConfigurations.clear()
    }
}
