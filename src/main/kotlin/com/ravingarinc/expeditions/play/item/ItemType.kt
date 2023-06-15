package com.ravingarinc.expeditions.play.item

import com.ravingarinc.api.I
import io.lumine.mythic.lib.api.item.NBTItem
import net.Indyuce.mmoitems.MMOItems
import net.Indyuce.mmoitems.api.player.PlayerData
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import java.util.*
import java.util.logging.Level

sealed interface ItemType {

    fun generate(player: Player?): ItemStack?

    fun isSameAs(item: ItemStack): Boolean

    fun getId(): String
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
}