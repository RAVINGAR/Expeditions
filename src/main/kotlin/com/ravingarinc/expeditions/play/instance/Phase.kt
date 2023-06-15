package com.ravingarinc.expeditions.play.instance

import com.github.shynixn.mccoroutine.bukkit.launch
import com.github.shynixn.mccoroutine.bukkit.ticks
import com.ravingarinc.expeditions.locale.type.Expedition
import com.ravingarinc.expeditions.locale.type.ExtractionZone
import com.ravingarinc.expeditions.play.PlayHandler
import kotlinx.coroutines.delay
import org.bukkit.ChatColor
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.Sound
import org.bukkit.boss.BarColor
import org.bukkit.boss.BarFlag
import org.bukkit.entity.EntityType
import org.bukkit.map.MapCursor
import org.bukkit.map.MapCursorCollection
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.random.Random

sealed class Phase(val name: String, private val mobInterval: Long, private val lootInterval: Long, private val durationTicks: Long, private val nextPhase: () -> Phase) {
    protected var ticks = 0L
    protected var isActive: AtomicBoolean = AtomicBoolean(false)

    fun start(instance: ExpeditionInstance) {
        onStart(instance)
        isActive.setRelease(true)
    }

    fun end(instance: ExpeditionInstance) {
        onEnd(instance)
        isActive.setRelease(false)
    }

    fun isActive() : Boolean {
        return isActive.acquire
    }

    /**
     * Tick method for every second (AKA 20 ticks)
     */
    fun tick(random: Random, instance: ExpeditionInstance) {
        if(!isActive()) {
            return
        }
        if(durationTicks != -1L && ticks >= durationTicks) {
            next(instance)
            return
        }
        onTick(random, instance)
        ticks += 20L
    }

    open fun onTick(random: Random, instance: ExpeditionInstance) {
        if(mobInterval != -1L && ticks % mobInterval == 0L) {
            instance.tickMobs(random)
        }
        if(lootInterval != -1L && ticks % lootInterval == 0L) {
            instance.tickLoot(random)
        }
    }

    fun next(instance: ExpeditionInstance) {
        nextPhase.invoke().let { instance.setPhase(it) }
    }

    protected abstract fun onStart(instance: ExpeditionInstance)

    protected abstract fun onEnd(instance: ExpeditionInstance)
}

class IdlePhase(expedition: Expedition) :
    Phase("Idle", -1, -1, -1, {
        PlayPhase(expedition)
}) {
    override fun onStart(instance: ExpeditionInstance) {
        instance.expedition.getAreas().forEach {
            if(it is ExtractionZone) {
                if(Random.nextDouble() < it.chance) {
                    instance.areaInstances.add(AreaInstance(instance.expedition, it))
                }
            } else {
                instance.areaInstances.add(AreaInstance(instance.expedition, it))
            }
        }
        val collection = MapCursorCollection()
        instance.areaInstances.forEach {
            it.initialise(instance.plugin, instance.world)
            val center = it.area.centre()
            val radius = instance.expedition.radius
            val byteX = (((center.first - instance.expedition.centreX) / radius.toFloat()) * 128).toInt().toByte()
            val byteZ = (((center.second - instance.expedition.centreZ) / radius.toFloat()) * 128).toInt().toByte()
            val type = if(it.area is ExtractionZone) MapCursor.Type.GREEN_POINTER else MapCursor.Type.RED_POINTER
            val cursor = MapCursor(byteX, byteZ, 0, type, true, it.area.displayName)
            collection.addCursor(cursor)
        }
        instance.mapView.renderers.forEach {
            // todo idk!
        }
        val border = instance.world.worldBorder
        border.size = (instance.expedition.radius * 2).toDouble()
        border.center = Location(instance.world, instance.expedition.centreX.toDouble(), 60.0, instance.expedition.centreZ.toDouble())
        border.damageBuffer = 0.0
        border.damageAmount = 1.0
        instance.bossBar.progress = 1.0
    }

    override fun onTick(random: Random, instance: ExpeditionInstance) {
        // do nothing...
    }

    override fun onEnd(instance: ExpeditionInstance) {

    }
}

class PlayPhase(expedition: Expedition) :
    Phase("Peaceful", expedition.mobInterval, expedition.lootInterval, expedition.calmPhaseDuration, {
    StormPhase(expedition)
}) {
        private val totalTime = expedition.calmPhaseDuration + expedition.stormPhaseDuration

    override fun onStart(instance: ExpeditionInstance) {
        // Initialise world border
    }

    override fun onTick(random: Random, instance: ExpeditionInstance) {
        super.onTick(random, instance)
        instance.bossBar.progress = 1.0 - (ticks / totalTime.toDouble())
    }

    override fun onEnd(instance: ExpeditionInstance) {

    }
}

class StormPhase(expedition: Expedition) :
    Phase("Storm", (expedition.mobInterval / expedition.mobModifier).toLong(), (expedition.lootInterval / expedition.lootModifier).toLong(), expedition.stormPhaseDuration, {
    RestorationPhase(expedition)
}) {
    private val totalTime = expedition.calmPhaseDuration + expedition.stormPhaseDuration
    override fun onStart(instance: ExpeditionInstance) {
        // Make announcement
        // Change world border status

        instance.bossBar.addFlag(BarFlag.CREATE_FOG)
        instance.bossBar.addFlag(BarFlag.DARKEN_SKY)
        instance.bossBar.color = BarColor.RED

        val centreX = instance.expedition.centreX
        val centreZ = instance.expedition.centreZ
        val radius = instance.expedition.radius
        instance.plugin.launch {
            for(i in 0..4) {
                val x = Random.nextInt(centreX - radius, centreX + radius)
                val z = Random.nextInt(centreZ - radius, centreZ + radius)
                instance.world.strikeLightning(Location(instance.world,x.toDouble(), 255.0, z.toDouble()))
                delay(Random.nextInt(5, 10).ticks)
            }
        }

        instance.getRemainingPlayers().forEach {
            it.sendTitle("", "${ChatColor.RED}The storm is approaching!")
            it.sendMessage("${ChatColor.YELLOW}Make your way to the nearest extraction point")
        }
    }

    override fun onTick(random: Random, instance: ExpeditionInstance) {
        super.onTick(random, instance)
        instance.bossBar.progress = 1.0 - (ticks / totalTime.toDouble())
    }

    override fun onEnd(instance: ExpeditionInstance) {
        // Kill any remaining players
        // Players which have left the expedition, but haven't rejoined yet should have their player data cached in a file.
        // And then their join responsibility is now up to the PlayHandler class!
        val handler = instance.plugin.getModule(PlayHandler::class.java)

        instance.bossBar.removeAll()
        instance.bossBar.removeFlag(BarFlag.CREATE_FOG)
        instance.bossBar.removeFlag(BarFlag.DARKEN_SKY)
        instance.bossBar.color = BarColor.BLUE
        // Done before getQuitPlayers, since this method may add to quit players
        instance.getRemainingPlayers().forEach {
            it.playSound(it, Sound.ENTITY_WITHER_SPAWN, 0.7F, 0.2F)
            it.inventory.clear()
            instance.world.strikeLightningEffect(it.location)
            it.health = 0.0
        }
        instance.getQuitPlayers().forEach {
            handler.addAbandon(it)
        }
        instance.clearPlayers()
    }
}

class RestorationPhase(expedition: Expedition) :
    Phase("Restoration", -1, -1, 0L, {
    IdlePhase(expedition)
}) {
    override fun onStart(instance: ExpeditionInstance) {
        instance.areaInstances.forEach {
            it.dispose(instance.plugin, instance.world)
        }
        instance.brokenBlocks.values.forEach { pair ->
            val block = pair.first
            if(!block.chunk.isLoaded) {
                instance.world.getChunkAt(block)
            }
            block.setType(pair.second, false)
        }
        instance.clear()
    }

    override fun onTick(random: Random, instance: ExpeditionInstance) {
        next(instance)
    }

    override fun onEnd(instance: ExpeditionInstance) {

    }
}

fun tickExtractions(instance: ExpeditionInstance) {
    instance.sneakingPlayers.forEach { player, time ->
        val diff = System.currentTimeMillis() - time
        if((750L..1250L).contains(diff)) {
            player.sendMessage("${ChatColor.YELLOW}Prepare for extraction!")
            player.playSound(player, Sound.BLOCK_NOTE_BLOCK_PLING, 0.8F, 0.8F)
        }

        if(diff > instance.extractionTime) {
            instance.plugin.launch {
                player.playSound(player, Sound.ITEM_TRIDENT_RIPTIDE_2, 0.8F, 0.8F)
                player.playSound(player, Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.8F, 1.0F)
                instance.extract(player)
            }
        }
    }
}