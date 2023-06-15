package com.ravingarinc.expeditions.play.item

import com.ravingarinc.expeditions.play.item.ItemType
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack

class LootItem(val type: ItemType, val range: IntRange, val weight: Double) {
    fun getItem(player: Player?): ItemStack? {
        return type.generate(player)
    }
}