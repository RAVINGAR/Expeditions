package com.ravingarinc.expeditions.play.item

import com.ravingarinc.api.I
import com.ravingarinc.api.module.warn
import io.lumine.mythic.bukkit.MythicBukkit
import io.lumine.mythic.bukkit.adapters.BukkitItemStack
import io.lumine.mythic.lib.api.item.NBTItem
import net.Indyuce.mmoitems.MMOItems
import net.Indyuce.mmoitems.api.player.PlayerData
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import java.util.*
import java.util.logging.Level

sealed interface ItemType {

    fun generate(player: Player?): ItemStack?

    fun isSameAs(item: ItemStack): Boolean

    fun getId(): String



    companion object {
        fun convert(item: ItemStack) : ItemType {
            if(Bukkit.getServer().pluginManager.isPluginEnabled("MMOItems")) {
                val nbtItem = NBTItem.get(item)
                if(nbtItem.hasType()) {
                    return MMOItemType(nbtItem.type, nbtItem.getString("MMOITEMS_ITEM_ID"))
                }
            }
            if(Bukkit.getServer().pluginManager.isPluginEnabled("MythicCrucible")) {
                MythicBukkit.inst().itemManager.getMythicTypeFromItem(item)?.let {
                    return CrucibleItemType(it)
                }
            }
            return VanillaItemType(item.type)
        }

        fun convertAsString(item: ItemStack) : String {
            if(Bukkit.getServer().pluginManager.isPluginEnabled("MMOItems")) {
                val nbtItem = NBTItem.get(item)
                if(nbtItem.hasType()) {
                    return "mmoitems:${nbtItem.type}:${nbtItem.getString("MMOITEMS_ITEM_ID")}"
                }
            }
            if(Bukkit.getServer().pluginManager.isPluginEnabled("MythicCrucible")) {
                MythicBukkit.inst().itemManager.getMythicTypeFromItem(item)?.let {
                    return "crucible:$it"
                }
            }
            return "vanilla:${item.type.name}"
        }
    }
}

class MMOItemType(private val type: String, private val identifier: String) : ItemType {
    override fun generate(player: Player?): ItemStack? {
        val type = MMOItems.plugin.types.get(type)
        if (type == null) {
            I.log(
                Level.WARNING,
                "MMOItem could not be found for drop ${this.type}:$identifier as ${this.type} was not a valid MMOItem Type!"
            )
            return null
        }
        val item = MMOItems.plugin.getMMOItem(type, identifier, player?.let { PlayerData.get(it) })
        if (item == null) {
            I.log(
                Level.WARNING,
                "MMOItem could not be found for drop ${this.type}:$identifier as $identifier could not be found!"
            )
            return null
        }
        return item.newBuilder().build()
    }

    override fun getId(): String {
        return identifier
    }

    override fun isSameAs(item: ItemStack): Boolean {
        if (item.type == Material.AIR) {
            return false
        }
        val nbtItem = NBTItem.get(item)
        if (!nbtItem.hasType()) {
            return false
        }
        return (nbtItem.type == type && nbtItem.getString("MMOITEMS_ITEM_ID").equals(identifier, true))
    }

    override fun toString(): String {
        return "mmoitems:$type:$identifier"
    }
}

class CrucibleItemType(private val identifier: String) : ItemType {
    override fun generate(player: Player?): ItemStack? {
        val item = MythicBukkit.inst().itemManager.getItem(identifier)
        if(item.isPresent) {
            return (item.get().generateItemStack(1) as BukkitItemStack).build()
        }
        warn("Could not find MythicCrucible item with id '$identifier'!")
        return null
    }

    override fun isSameAs(item: ItemStack): Boolean {
        val identifier = MythicBukkit.inst().itemManager.getMythicTypeFromItem(item) ?: return false
        return identifier == this.identifier
    }

    override fun getId(): String {
        return identifier
    }

    override fun toString(): String {
        return "crucible:$identifier"
    }
}

class VanillaItemType(private val material: Material) : ItemType {
    override fun generate(player: Player?): ItemStack {
        return ItemStack(material, 1)
    }

    override fun isSameAs(item: ItemStack): Boolean {
        if (item.type == Material.AIR) {
            return false
        }
        return item.type == material
    }

    override fun getId(): String {
        return material.name.lowercase(Locale.getDefault())
    }

    override fun toString(): String {
        return "vanilla:${material.name}"
    }
}