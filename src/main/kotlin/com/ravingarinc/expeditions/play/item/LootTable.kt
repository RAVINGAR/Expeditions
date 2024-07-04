package com.ravingarinc.expeditions.play.item

import com.ravingarinc.api.module.RavinPlugin
import com.ravingarinc.expeditions.api.WeightedCollection
import com.ravingarinc.expeditions.queue.QueueManager
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import kotlin.experimental.ExperimentalTypeInference
import kotlin.math.floor

class LootTable @OptIn(ExperimentalTypeInference::class) constructor(val title: String, val scoreRange: IntRange, private val quantity: IntRange, @BuilderInference builderAction: MutableList<LootItem>.() -> Unit) :
    LootHolder {
    private val loots: WeightedCollection<LootItem> = WeightedCollection()
    private var singleCollection: WeightedCollection<LootTable>? = null
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


    private fun calculateScore(plugin: RavinPlugin) : Int {
        val manager = plugin.getModule(QueueManager::class.java)
        var totalScore = 0.0
        val iterator = loots.weightedIterator()
        for(entry in iterator) {
            val item = entry.first.getItem(null) ?: continue
            if(item.type.isAir) continue
            (manager.getItemScore(item) ?: 0).let { totalScore += it * entry.second }
        }
        totalScore /= loots.getTotalWeight()
        return floor(totalScore).toInt()
    }

    fun asSingleCollection() : WeightedCollection<LootTable> {
        if(singleCollection == null) {
            WeightedCollection<LootTable>().let {
                it.add(this, 1.0)
                singleCollection = it
            }
        }
        return singleCollection!!
    }

    companion object {
        val EMPTY = LootTable("Nothing", 0..0,0..0) {}

        val EMPTY_COLLECTION = WeightedCollection<LootTable>()
        init {
            EMPTY_COLLECTION.add(EMPTY, 1.0)
        }
    }
}