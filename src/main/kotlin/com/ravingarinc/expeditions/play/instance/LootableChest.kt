package com.ravingarinc.expeditions.play.instance

import com.ravingarinc.api.Version
import com.ravingarinc.api.Versions
import com.ravingarinc.api.build
import com.ravingarinc.api.sendPacket
import com.ravingarinc.expeditions.play.item.LootTable
import kotlinx.coroutines.delay
import net.kyori.adventure.text.Component
import org.bukkit.*
import org.bukkit.Particle.DustOptions
import org.bukkit.entity.Entity
import org.bukkit.entity.MagmaCube
import org.bukkit.entity.Player
import org.bukkit.event.inventory.InventoryType
import org.bukkit.util.BlockVector
import java.util.concurrent.atomic.AtomicReference
import kotlin.random.Random

class LootableChest(private val loot: LootTable, val instance: AreaInstance, private val location: BlockVector, private val world: World) {
    private val block = world.getBlockAt(location.blockX, location.blockY, location.blockZ)
    private var entity : AtomicReference<Entity?> = AtomicReference(null)

    private val showingPlayers: MutableSet<Player> = HashSet()
    init {
        block.setType(instance.expedition.lootBlock, false)
    }

    suspend fun loot(player: Player) {
        var results = loot.collectResults(player)

        destroy()

        val loc = Location(world, location.x + 0.5, location.y + 0.5, location.z + 0.5)

        world.spawnParticle(Particle.SPELL_INSTANT, loc, 15, 0.4, 0.75, 0.4, 1.0)
        world.spawnParticle(Particle.REDSTONE, loc, 25, 0.5, 0.5, 0.5, dust)
        world.playSound(loc, Sound.ITEM_SHIELD_BLOCK, SoundCategory.BLOCKS, 0.7F, 0.5F)
        world.playSound(loc, Sound.ENTITY_PLAYER_LEVELUP, SoundCategory.BLOCKS, 0.7F, 0.9F)

        val inventory = Bukkit.createInventory(player, InventoryType.CHEST, Component.text(loot.title))
        val takenSlots : Set<Int> = HashSet()
        if(results.size > 27) {
            results = results.subList(0, 27)
        }
        for(result in results) {
            var i = random.nextInt(27)
            while(takenSlots.contains(i)) {
                i = random.nextInt(27)
            }
            inventory.setItem(i, result)
        }

        delay(50)

        world.playSound(loc, Sound.BLOCK_CHEST_OPEN, SoundCategory.BLOCKS, 0.7F, 0.8F)

        player.openInventory(inventory)
    }

    fun show(player: Player) {
        checkEntity()
        val entity = this.entity.acquire!!
        if(!entity.isValid) {
            return
        }
        if(showingPlayers.add(player)) {
            val loc = entity.location
            player.sendPacket(Versions.version.removeEntity(entity.entityId))
            player.sendPacket(Versions.version.spawnMob(entity.entityId, entity.uniqueId, entity.type, loc.x, loc.y, loc.z, 0, 0, 0))
            Version.protocol.sendServerPacket(player, Versions.version.updateMetadata(entity) {
                this.build(0, Version.byteSerializer, (0x40 or 0x20).toByte())
                this.build(4, Version.boolSerializer, true)
                this.build(5, Version.boolSerializer, true)
                this.build(15, Version.byteSerializer, (0x01).toByte())
                this.build(16, Version.integerSerializer, 2)
            })
        }
    }

    fun checkEntity() {
        if(this.entity.acquire == null) {
            this.entity.setRelease(world.spawn(Location(world, location.x + 0.5, location.y, location.z + 0.5), MagmaCube::class.java) {
                it.size = 1
                it.isSilent = true
                it.setGravity(false)
                it.setAI(false)
                it.isInvulnerable = true
                it.isPersistent = true
                it.removeWhenFarAway = false
                //val duration =  (instance.expedition.calmPhaseDuration + instance.expedition.stormPhaseDuration).toInt()
                //it.addPotionEffect(PotionEffect(PotionEffectType.INVISIBILITY, duration, 1, true, false))
                //it.addPotionEffect(PotionEffect(PotionEffectType.GLOWING, duration, 1, true, false))

                // ONLY when you first load into a world and go to a POI, the magma cube doesn't appear properly. Or rather isn't
                // registered by this object correctly. Maybe delay the initial tick?
            })
        }
    }

    fun hide(player: Player) {
        checkEntity()
        val entity = this.entity.acquire!!
        if(showingPlayers.remove(player)) {
            player.sendPacket(Versions.version.removeEntity(entity.entityId))
            /*
            Version.protocol.sendServerPacket(player, Versions.version.updateMetadata(entity) {
                this.build(0, Version.byteSerializer, (0x20).toByte())
                this.build(4, Version.boolSerializer, true)
                this.build(5, Version.boolSerializer, true)
                this.build(15, Version.byteSerializer, (0x01).toByte())
                this.build(16, Version.integerSerializer, 2)
            })*/
        }
    }

    fun destroy() {
        entity.acquire?.let { entity ->
            showingPlayers.forEach { it.sendPacket(Versions.version.removeEntity(entity.entityId)) }
            entity.remove()
        }
        entity.setRelease(null)
        showingPlayers.clear()
        block.setType(Material.AIR, false)
    }

    companion object {
        val dust = DustOptions(Color.fromRGB(232, 202, 81), 1.0F)

        val random = Random(System.currentTimeMillis())
    }
}