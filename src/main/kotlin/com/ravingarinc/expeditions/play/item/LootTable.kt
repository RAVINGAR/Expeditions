package com.ravingarinc.expeditions.play.item

import com.ravingarinc.expeditions.api.WeightedCollection
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import kotlin.experimental.ExperimentalTypeInference

class LootTable @OptIn(ExperimentalTypeInference::class) constructor(val title: String, private val quantity: IntRange, @BuilderInference builderAction: MutableList<LootItem>.() -> Unit) :
    LootHolder {
    private val loots: WeightedCollection<LootItem> = WeightedCollection()
    private var score: Int? = null
    init {
        for(it in buildList(builderAction)) {
            loots.add(it, it.weight)
        }
    }

    override fun collectResults(player: Player?): List<ItemStack> {
        val list: List<ItemStack> = buildList {
            val amount = quantity.random()
            for(i in 0 until amount) {
                loots.random().getItem(player)?.let {
                    this.add(it)
                }
            }
        }
        return list
    }

    fun getScore(plugin: RavinPlugin) {
        if(score != null) return score!!

        var itemArray = Array<ItemStack?>(loots.size) { (i, it) ->
            this[i] = it.generate(null)
        }
        score = plugin.getModule(QueueManager::class.java).calculateGearScore(itemArray)
        return score!!
    }
}