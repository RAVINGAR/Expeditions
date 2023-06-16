package com.ravingarinc.expeditions.play.instance

import com.ravingarinc.api.I
import com.ravingarinc.expeditions.api.Version
import com.ravingarinc.expeditions.api.add
import com.ravingarinc.expeditions.api.getVersion
import com.ravingarinc.expeditions.play.item.LootTable
import kotlinx.coroutines.delay
import org.bukkit.*
import org.bukkit.Particle.DustOptions
import org.bukkit.entity.MagmaCube
import org.bukkit.entity.Player
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import org.bukkit.util.BlockVector
import java.util.*
import java.util.logging.Level
import kotlin.random.Random

class LootableChest(private val loot: LootTable, val instance: AreaInstance, private val location: BlockVector, private val world: World) {
    private val block = world.getBlockAt(location.blockX, location.blockY, location.blockZ)
    private val entity = world.spawn(Location(world, location.x + 0.5, location.y, location.z + 0.5), MagmaCube::class.java) {
        it.size = 1
        it.isSilent = true
        it.setGravity(false)
        it.isInvulnerable = true
        it.setAI(false)
        it.isPersistent = true
        it.addPotionEffect(PotionEffect(PotionEffectType.INVISIBILITY, (instance.expedition.calmPhaseDuration + instance.expedition.stormPhaseDuration).toInt(), 0, true, false))
    }

    private val showingPlayers: MutableSet<UUID> = HashSet()
    init {
        block.setType(instance.expedition.lootBlock, false)
    }

    suspend fun loot(player: Player) {
        val results = loot.collectResults(player)
        block.setType(Material.AIR, false)
        entity.remove()
        val loc = Location(world, location.x + 0.5, location.y + 0.5, location.z + 0.5)
        world.spawnParticle(Particle.SPELL_INSTANT, loc, 15, 0.1, 0.01, 0.01, 1.0)
        world.spawnParticle(Particle.REDSTONE, loc, 10, 0.05, 0.05, 0.05, dust)
        world.playSound(loc, Sound.BLOCK_CHEST_OPEN, SoundCategory.BLOCKS, 0.7F, 0.8F)
        world.playSound(loc, Sound.ITEM_SHIELD_BLOCK, SoundCategory.BLOCKS, 0.7F, 0.5F)
        world.playSound(loc, Sound.ENTITY_PLAYER_LEVELUP, SoundCategory.BLOCKS, 0.7F, 0.9F)

        for(result in results) {
            world.playSound(loc, Sound.ENTITY_ITEM_PICKUP, SoundCategory.BLOCKS, 0.7F, 1.0F)
            world.dropItemNaturally(loc, result)
            delay(Random.nextLong(100))
        }
    }

    fun show(player: Player) {
        if(showingPlayers.add(player.uniqueId)) {
            I.log(Level.WARNING, "Sending show server packet!")
            Version.protocol.sendServerPacket(player, instance.plugin.getVersion().updateMetadata(entity) {
                this.add(0, Version.byteSerializer, (0x80))
                this.add(4, Version.boolSerializer, true)
                this.add(5, Version.boolSerializer, true)
                this.add(15, Version.byteSerializer, (0x01))
            })
        }
    }

    fun hide(player: Player) {
        if(showingPlayers.remove(player.uniqueId)) {
            I.log(Level.WARNING, "Sending hide server packet!")
            Version.protocol.sendServerPacket(player, instance.plugin.getVersion().updateMetadata(entity) {
                this.add(0, Version.byteSerializer, (0).toByte())
                this.add(4, Version.boolSerializer, true)
                this.add(5, Version.boolSerializer, true)
                this.add(15, Version.byteSerializer, (0x01))
            })
        }
    }

    fun destroy() {
        block.setType(Material.AIR, false)
        entity.remove()
    }

    companion object {
        val dust = DustOptions(Color.fromRGB(232, 202, 81), 1.0F)
    }
}