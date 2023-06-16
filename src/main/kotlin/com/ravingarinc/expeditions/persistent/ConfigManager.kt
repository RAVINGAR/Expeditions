package com.ravingarinc.expeditions.persistent

import com.github.shynixn.mccoroutine.bukkit.launch
import com.ravingarinc.api.module.RavinPlugin
import com.ravingarinc.api.module.SuspendingModule
import com.ravingarinc.expeditions.api.copyResource
import kotlinx.coroutines.Dispatchers
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

    fun saveAbandons() {
        plugin.launch(Dispatchers.IO) {
            data.save()
        }
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
