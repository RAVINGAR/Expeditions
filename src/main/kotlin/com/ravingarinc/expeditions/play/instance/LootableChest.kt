package com.ravingarinc.expeditions.play.instance

import com.ravingarinc.expeditions.play.item.LootTable
import kotlinx.coroutines.delay
import org.bukkit.*
import org.bukkit.block.data.BlockData
import org.bukkit.entity.Player
import org.bukkit.util.BlockVector
import kotlin.random.Random

class LootableChest(private val loot: LootTable, private val location: BlockVector, private val world: World) {
    private val block = world.getBlockAt(location.blockX, location.blockY, location.blockZ)
    init {
        // Actually spawn entity here
        block.setType(Material.CHEST, false)
    }

    suspend fun loot(player: Player) {
        val results = loot.collectResults(player)
        block.setType(Material.AIR, false)
        val loc = Location(world, location.x + 0.5, location.y + 0.5, location.z + 0.5)
        world.spawnParticle(Particle.SPELL_MOB, loc, 15, 0.0, 0.1, 0.1, 0.1,  Particle.DustOptions(COLOUR_1, 1F))
        world.spawnParticle(Particle.SPELL_MOB, loc, 15, 0.0, 0.2, 0.2, 0.2,  Particle.DustOptions(COLOUR_2, 0.5F))
        world.playSound(loc, Sound.ITEM_SHIELD_BLOCK, SoundCategory.BLOCKS, 0.8F, 0.5F)
        world.playSound(loc, Sound.ENTITY_PLAYER_LEVELUP, SoundCategory.BLOCKS, 0.6F, 0.9F)

        for(result in results) {
            world.playSound(loc, Sound.ENTITY_ITEM_PICKUP, SoundCategory.BLOCKS, 0.8F, 1.0F)
            world.dropItemNaturally(loc, result)
            delay(Random.nextLong(100))
        }
    }

    fun destroy() {
        block.setType(Material.AIR, false)
    }

    companion object {
        val COLOUR_1 = Color.fromRGB(232, 202, 81)
        val COLOUR_2 = Color.fromRGB(245, 234, 36)
    }
}