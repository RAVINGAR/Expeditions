package com.ravingarinc.expeditions.api

import com.ravingarinc.api.module.warn
import com.ravingarinc.expeditions.play.item.*
import com.ravingarinc.expeditions.play.mob.MobType
import com.ravingarinc.expeditions.play.mob.MythicMobType
import com.ravingarinc.expeditions.play.mob.VanillaMobType
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.entity.EntityType
import java.util.*

fun ConfigurationSection.getDropTable(path: String): LootTable {
    val range = getRange("$path.quantity")
    val title = getString("$path.title") ?: "Loot Crate"
    val scoreRange = getRange("$path.score-range")
    return LootTable(title, scoreRange, range) {
        getMapList("$path.loot").forEach { map ->
            val id = map["id"].toString()
            val r = parseRange(map["quantity"].toString())
            val weight = (map["weight"].toString()).toDoubleOrNull() ?: 0.0
            parseItem(id)?.let {
                this@LootTable.add(LootItem(it, r, weight))
            }
        }
    }
}

fun ConfigurationSection.getMob(path: String) : Triple<MobType, Double, IntRange>? {
    val string = getString(path)
    if (string == null) {
        warn("Could not find option at path '$path' in section '${this.name}'")
        return null
    }
    return parseMob(string)
}

fun parseMob(string: String) : Triple<MobType, Double, IntRange>? {
    if(string == "null") return null
    val split = string.split(":".toRegex(), limit = 2)
    if(split.size < 2) {
        warn("Incorrect syntax for mob type '$string'. Please use the format <type>:<identifier>!")
        return null
    }
    if(split[0].equals("mythic", true) || split[0].equals("mythicmobs", true) || split[0].equals("mm", true)) {
        if(!Bukkit.getServer().pluginManager.isPluginEnabled("MythicMobs")) {
            warn("Could not get parse mythic mob type as MythicMobs is not enabled!")
            return null
        }
        val subSplit = split[1].split(",".toRegex(), limit = 3)
        val id = subSplit[0]
        val weight = subSplit[1].toDoubleOrNull()
        val range = if(subSplit.size > 2) parseRange(subSplit[2]) else IntRange(1, 1)
        if(weight == null) {
            warn("Could not parse weight for mob string '$string'! Please specify a valid number!")
            return null
        }
        return Triple(MythicMobType(id), weight, range)
    } else if (split[0].equals("vanilla", true) || split[0].equals("v", true)) {
        val subSplit = split[1].split(",".toRegex(), limit = 2)
        val id = subSplit[0]
        val weight = subSplit[1].toDoubleOrNull()
        if(weight == null) {
            warn("Could not parse weight for mob string '$string'! Please specify a valid number!")
            return null
        }
        var entity : EntityType? = null
        for(type in EntityType.values()) {
            if(type.name.equals(id, true)) {
                entity = type
            }
        }
        if(entity == null) {
            warn("Could not find vanilla entity type called '${id}' in string '${string}'")
            return null
        }
        return Triple(VanillaMobType(entity), weight, IntRange(0, 0))
    } else {
        warn("Unknown mob type '${split[0]}' found for string $string! Please use 'mythic' or 'vanilla'")
    }
    return null
}

fun ConfigurationSection.getMobType(path: String) : MobType? {
    val string = getString(path)
    if (string == null) {
        warn("Could not find option at path '$path' in section '${this.name}'")
        return null
    }
    return parseMobType(string)
}

fun parseMobType(string: String) : MobType? {
    val split = string.split(":".toRegex(), limit = 2)
    if(split[0].equals("mythic", true) || split[0].equals("mythicmobs", true) || split[0].equals("mm", true)) {
        if(!Bukkit.getServer().pluginManager.isPluginEnabled("MythicMobs")) {
            warn("Could not get parse mythic mob type as MythicMobs is not enabled!")
            return null
        }
        return MythicMobType(split[1])
    } else if (split[0].equals("vanilla", true) || split[0].equals("v", true)) {
        val id = split[1]
        var entity : EntityType? = null
        for(type in EntityType.values()) {
            if(type.name.equals(id, true)) {
                entity = type
            }
        }
        if(entity == null) {
            warn("Could not find vanilla entity type called '${id}' in string '${string}'")
            return null
        }
        return VanillaMobType(entity)
    } else {
        warn("Unknown mob type '${split[0]}' found for string $string! Please use 'mythic' or 'vanilla'")
    }
    return null
}

fun ConfigurationSection.getItem(path: String): ItemType? {
    val string = getString(path)
    if (string == null) {
        warn("Could not find option at path '$path' in section '${this.name}'")
        return null
    }
    return parseItem(string)
}

fun parseItem(string: String): ItemType? {
    val split = string.lowercase(Locale.getDefault()).split(":".toRegex(), limit = 3)
    if (split[0] == "mmoitem" || split[0] == "mmoitems") {
        if(!Bukkit.getServer().pluginManager.isPluginEnabled("MMOItems")) {
            warn("Could not load MMOItem '$string' as MMOItems is not enabled!")
            return null
        }
        if (split.size == 3) {
            return MMOItemType(split[1].uppercase(), split[2].uppercase())
        } else {
            warn("Could not parse $string as an MMOItem as this requires both a type and identifier. Such as 'mmoitem:type:identifier'")
        }
    } else if (split[0] == "vanilla") {
        val material = Material.matchMaterial(split[1])
        if (material == null) {
            warn("Could not find vanilla material ${split[1]} as it does not exist! Please use a valid material.")
        } else {
            return VanillaItemType(material)
        }
    } else if(split[0] == "crucible") {
        if(!Bukkit.getServer().pluginManager.isPluginEnabled("MythicCrucible")) {
            warn("Could not load crucible '$string' as MythicCrucible is not enabled!")
            return null
        }
        val identifier = split[1]
        return CrucibleItemType(identifier)
    } else {
        warn("Unknown item type '${split[0]}' found for string $string! Please use 'mmoitem' or 'vanilla'")
    }
    return null
}

/*
fun ItemStack.getMMOIdentifier(): String {
    return NBTItem.get(this).getString("MMOITEMS_ITEM_ID") ?: ""
}

fun ItemStack.takeDurability(player: Player, amount: Int = 1) {
    val nbt = NBTItem.get(this)
    if (nbt.hasType()) {
        val durability = DurabilityItem(player, nbt)
        if (durability.isValid) {
            durability.decreaseDurability(amount)
            return
        }
    }
    val meta = this.itemMeta
    if (meta is Damageable) {
        meta.damage = meta.damage + amount
        this.itemMeta = meta
    }
}*/