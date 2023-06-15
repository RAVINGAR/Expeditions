package com.ravingarinc.expeditions.play.instance

import com.ravingarinc.expeditions.play.item.LootTable
import kotlinx.coroutines.delay
import org.bukkit.*
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
        for(i in 0..15) {
            val x = Random.nextInt(8) / 64.0 - 0.0625
            val y = Random.nextInt(8) / 64.0 - 0.0625
            val z = Random.nextInt(8) / 64.0 - 0.0625
            world.spawnParticle(Particle.SPELL_MOB, loc.add(x, y, z), 0, R, G, B, 1.0)
            val x2 = Random.nextInt(8) / 64.0 - 0.0625
            val y2 = Random.nextInt(8) / 64.0 - 0.0625
            val z2 = Random.nextInt(8) / 64.0 - 0.0625
            world.spawnParticle(Particle.SPELL, loc.subtract(x2, y2, z2), 0, R2, G2, B2, 1.0)
        }
        world.playSound(loc, Sound.BLOCK_CHEST_OPEN, SoundCategory.BLOCKS, 0.7F, 0.8F)
        world.playSound(loc, Sound.ITEM_SHIELD_BLOCK, SoundCategory.BLOCKS, 0.7F, 0.5F)
        world.playSound(loc, Sound.ENTITY_PLAYER_LEVELUP, SoundCategory.BLOCKS, 0.7F, 0.9F)

        for(result in results) {
            world.playSound(loc, Sound.ENTITY_ITEM_PICKUP, SoundCategory.BLOCKS, 0.7F, 1.0F)
            world.dropItemNaturally(loc, result)
            delay(Random.nextLong(100))
        }
    }

    fun destroy() {
        block.setType(Material.AIR, false)
    }

    companion object {
        val R = 232 / 255.0
        val G = 202 / 255.0
        val B = 81 / 255.0

        val R2 = 245 / 255.0
        val G2 = 234 / 255.0
        val B2 = 36 / 255.0
    }
}