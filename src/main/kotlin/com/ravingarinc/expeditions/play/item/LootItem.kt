package com.ravingarinc.expeditions.play.item

import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack

class LootItem(val type: ItemType, val range: IntRange, val weight: Double) {
    fun getItem(player: Player?): ItemStack? {
        val i = type.generate(player)
        if(i != null) i.amount = range.random()
        return i
    }
}