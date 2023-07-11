package com.ravingarinc.expeditions.play.instance

import com.ravingarinc.api.Version
import com.ravingarinc.api.Versions
import com.ravingarinc.api.build
import com.ravingarinc.expeditions.play.item.LootTable
import kotlinx.coroutines.delay
import org.bukkit.*
import org.bukkit.Particle.DustOptions
import org.bukkit.entity.MagmaCube
import org.bukkit.entity.Player
import org.bukkit.util.BlockVector
import java.util.*
import kotlin.random.Random

class LootableChest(private val loot: LootTable, val instance: AreaInstance, private val location: BlockVector, private val world: World) {
    private val block = world.getBlockAt(location.blockX, location.blockY, location.blockZ)
    private val entity = world.spawn(Location(world, location.x + 0.5, location.y, location.z + 0.5), MagmaCube::class.java) {
        it.size = 1
        it.isSilent = true
        it.setGravity(false)
        it.setAI(false)
        it.isInvulnerable = true
        it.isPersistent = true
        //val duration =  (instance.expedition.calmPhaseDuration + instance.expedition.stormPhaseDuration).toInt()
        //it.addPotionEffect(PotionEffect(PotionEffectType.INVISIBILITY, duration, 1, true, false))
        //it.addPotionEffect(PotionEffect(PotionEffectType.GLOWING, duration, 1, true, false))

        // ONLY when you first load into a world and go to a POI, the magma cube doesn't appear properly. Or rather isn't
        // registered by this object correctly. Maybe delay the initial tick?
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
        world.spawnParticle(Particle.SPELL_INSTANT, loc, 15, 0.4, 0.75, 0.4, 1.0)
        world.spawnParticle(Particle.REDSTONE, loc, 10, 0.5, 0.5, 0.5, dust)
        world.playSound(loc, Sound.BLOCK_CHEST_OPEN, SoundCategory.BLOCKS, 0.7F, 0.8F)
        world.playSound(loc, Sound.ITEM_SHIELD_BLOCK, SoundCategory.BLOCKS, 0.7F, 0.5F)
        world.playSound(loc, Sound.ENTITY_PLAYER_LEVELUP, SoundCategory.BLOCKS, 0.7F, 0.9F)

        for(result in results) {
            world.playSound(loc, Sound.ENTITY_ITEM_PICKUP, SoundCategory.BLOCKS, 0.7F, 1.0F)
            world.dropItemNaturally(loc, result)
            delay(Random.nextLong(150))
        }
    }

    fun show(player: Player) {
        if(showingPlayers.add(player.uniqueId)) {
            Version.protocol.sendServerPacket(player, Versions.version.updateMetadata(entity) {
                this.build(0, Version.byteSerializer, (0x40 or 0x20).toByte())
                this.build(4, Version.boolSerializer, true)
                this.build(5, Version.boolSerializer, true)
                this.build(15, Version.byteSerializer, (0x01).toByte())
                this.build(16, Version.integerSerializer, 2)
            })
        }
    }

    fun hide(player: Player) {
        if(showingPlayers.remove(player.uniqueId)) {
            Version.protocol.sendServerPacket(player, Versions.version.updateMetadata(entity) {
                this.build(0, Version.byteSerializer, (0x20).toByte())
                this.build(4, Version.boolSerializer, true)
                this.build(5, Version.boolSerializer, true)
                this.build(15, Version.byteSerializer, (0x01).toByte())
                this.build(16, Version.integerSerializer, 2)
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