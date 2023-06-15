package com.ravingarinc.expeditions.play.item

import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack

interface LootHolder {
    fun collectResults(player: Player?): List<ItemStack>
}