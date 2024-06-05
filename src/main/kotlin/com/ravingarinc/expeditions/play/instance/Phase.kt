package com.ravingarinc.expeditions.play.instance

import com.github.shynixn.mccoroutine.bukkit.launch
import com.github.shynixn.mccoroutine.bukkit.minecraftDispatcher
import com.ravingarinc.api.module.RavinPlugin
import com.ravingarinc.api.module.warn
import com.ravingarinc.expeditions.api.blockWithChunk
import com.ravingarinc.expeditions.locale.ExpeditionListener
import com.ravingarinc.expeditions.locale.type.Expedition
import com.ravingarinc.expeditions.locale.type.ExtractionZone
import com.ravingarinc.expeditions.play.PlayHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import org.bukkit.ChatColor
import org.bukkit.Location
import org.bukkit.Sound
import org.bukkit.boss.BarColor
import org.bukkit.boss.BarFlag
import org.bukkit.map.MapCursor
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.regex.Pattern
import kotlin.math.max
import kotlin.random.Random

sealed class Phase(val name: String, private val mobInterval: Long, private val randomMobInterval: Long, private val lootInterval: Long, val durationTicks: Long, private val nextPhase: () -> Phase) {
    protected var ticks = 0L
    protected var isActive: AtomicBoolean = AtomicBoolean(false)

    val currentTicks: Long get() = ticks

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
     * Tick method for every second (AKA 20 ticks) or rather
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
        instance.tickExpedition(random,
            mobInterval != -1L && ticks % max(mobInterval, 1L) == 0L,
            lootInterval != -1L && ticks % max(lootInterval, 1L) == 0L,
            randomMobInterval != -1L && ticks % max(randomMobInterval, 1L) == 0L)
    }

    fun next(instance: ExpeditionInstance) {
        nextPhase.invoke().let { instance.setPhase(it) }
    }

    protected abstract fun onStart(instance: ExpeditionInstance)

    protected abstract fun onEnd(instance: ExpeditionInstance)
}

class IdlePhase(expedition: Expedition) :
    Phase("${ChatColor.GRAY}Idle ✓", -1, -1, -1, -1, {
        PlayPhase(expedition)
}) {
    private val COMMAND_REGEX = Pattern.compile("(-?\\d+(?:.\\d+)?) (-?\\d+(?:\\.\\d+)?) (-?\\d+(?:.\\d+)?)")

    override fun onStart(instance: ExpeditionInstance) {
        instance.expedition.getAreas().forEach {
            if(it is ExtractionZone) {
                if(Random.nextDouble() < it.chance) {
                    instance.areaInstances.add(AreaInstance(instance.plugin, instance.expedition, it))
                }
            } else {
                instance.areaInstances.add(AreaInstance(instance.plugin, instance.expedition, it))
            }
        }
        for(it in instance.areaInstances) {
            if(it.area.isHidden()) {
                continue
            }
            it.initialise(instance.plugin, instance.world)
            val center = it.area.centre()
            val radius = instance.expedition.radius
            val byteX = (((center.first - instance.expedition.centreX) / radius.toFloat()) * 128).toInt().toByte()
            val byteZ = (((center.second - instance.expedition.centreZ) / radius.toFloat()) * 128).toInt().toByte()
            val cursor = MapCursor(byteX, byteZ, 8, it.area.cursorType, true, it.area.displayName)
            instance.renderer.addCursor(cursor)
        }
        val border = instance.world.worldBorder
        border.size = (instance.expedition.radius * 2).toDouble()
        border.center = Location(instance.world, instance.expedition.centreX.toDouble(), 60.0, instance.expedition.centreZ.toDouble())
        border.damageBuffer = 0.0
        border.damageAmount = 1.0
        instance.bossBar.progress = 1.0
        
        val plugin = instance.plugin
        instance.expedition.onCreateCommands.forEach { command ->
            val matcher = COMMAND_REGEX.matcher(command)
            var x : Double? = null
            var y : Double? = null
            var z : Double? = null
            if(matcher.find()) {
                x = matcher.group(1).toDoubleOrNull()
                y = matcher.group(2).toDoubleOrNull()
                z = matcher.group(3).toDoubleOrNull()
            }
            if(x != null && y != null && z != null) {
                plugin.launch(plugin.minecraftDispatcher) {
                    instance.world.blockWithChunk(plugin, x.toInt() shr 4, z.toInt() shr 4) {
                        plugin.server.dispatchCommand(plugin.server.consoleSender, command.replace("{world}", instance.world.name))
                    }
                }
            } else {
                plugin.server.dispatchCommand(plugin.server.consoleSender, command.replace("{world}", instance.world.name))
            }
        }
    }

    override fun onTick(random: Random, instance: ExpeditionInstance) {
        // do nothing...
    }

    override fun onEnd(instance: ExpeditionInstance) {

    }
}

class PlayPhase(expedition: Expedition) :
    Phase("${ChatColor.GREEN}Peaceful ✓", expedition.mobInterval, expedition.randomMobInterval, expedition.lootInterval, expedition.calmPhaseDuration, {
    StormPhase(expedition)
}) {

    private val totalTime = expedition.calmPhaseDuration + expedition.stormPhaseDuration
    private var rainStarted = false

    override fun onStart(instance: ExpeditionInstance) {
        // Initialise world border
    }

    override fun onTick(random: Random, instance: ExpeditionInstance) {
        super.onTick(random, instance)
        instance.bossBar.progress = 1.0 - (ticks / totalTime.toDouble())
        if(!rainStarted && this.durationTicks - ticks <= 200) {
            val world = instance.world
            val duration = instance.expedition.stormPhaseDuration.toInt() + 200
            world.setStorm(true)
            world.isThundering = true
            world.thunderDuration = duration
            world.weatherDuration = duration
        }
    }

    override fun onEnd(instance: ExpeditionInstance) {

    }
}

class StormPhase(expedition: Expedition) :
    Phase("${ChatColor.RED}Storm ❌", (expedition.mobInterval / expedition.mobModifier).toLong(), (expedition.randomMobInterval / expedition.mobModifier).toLong(), (expedition.lootInterval / expedition.lootModifier).toLong(), expedition.stormPhaseDuration, {
    RestorationPhase(expedition)
}) {
    private val totalTime = expedition.calmPhaseDuration + expedition.stormPhaseDuration
    override fun onStart(instance: ExpeditionInstance) {
        // Make announcement
        // Change world border status

        instance.bossBar.addFlag(BarFlag.CREATE_FOG)
        instance.bossBar.addFlag(BarFlag.DARKEN_SKY)
        instance.bossBar.color = BarColor.RED

        instance.getRemainingPlayers().forEach { cache ->
            val it = cache.player.player!!
            instance.world.strikeLightning(Location(it.world, it.location.x, 400.0, it.location.z))
            it.sendTitle("${ChatColor.DARK_RED}WARNING", "${ChatColor.RED}The storm is approaching!", 20, 70, 15)
            it.sendMessage("${ChatColor.YELLOW}Make your way to the nearest extraction point!")
        }
    }

    override fun onTick(random: Random, instance: ExpeditionInstance) {
        super.onTick(random, instance)
        instance.bossBar.progress = 1.0 - ((instance.expedition.calmPhaseDuration + ticks) / totalTime.toDouble())
        if(instance.getRemainingPlayers().isEmpty()) {
            ticks = durationTicks
            return
        }
        when ((durationTicks - ticks) / 20) {
            60L -> {
                instance.getRemainingPlayers().forEach { cache ->
                    val it = cache.player.player!!
                    instance.world.strikeLightning(Location(it.world, it.location.x, 400.0, it.location.z))
                    it.sendMessage("${ChatColor.YELLOW}There are 60 seconds remaining. Make your way to the nearest extraction point before it's too late!")
                }
            }
            30L -> {
                instance.getRemainingPlayers().forEach { cache ->
                    val it = cache.player.player!!
                    it.playSound(it, Sound.ENTITY_ENDERMAN_SCREAM, 0.7F, 0.1F)
                    instance.world.strikeLightning(Location(it.world, it.location.x, 400.0, it.location.z))
                    it.sendMessage("${ChatColor.YELLOW}There are 30 seconds remaining. Make your way to the nearest extraction point before it's too late!")
                }
            }
            10L -> {
                instance.getRemainingPlayers().forEach { cache ->
                    val it = cache.player.player!!
                    it.playSound(it, Sound.ENTITY_ENDERMAN_DEATH, 0.7F, 0.1F)
                    instance.world.strikeLightning(Location(it.world, it.location.x, 400.0, it.location.z))
                    it.sendMessage("${ChatColor.YELLOW}There are 10 seconds remaining. Make your way to the nearest extraction point before it's too late!")
                    instance.plugin.launch {
                        delay(Random.nextLong(100))
                        instance.world.strikeLightning(Location(it.world, it.location.x, 200.0, it.location.z))
                    }
                }
            }
            2L -> {
                instance.getRemainingPlayers().forEach { cache ->
                    val it = cache.player.player!!
                    it.playSound(it, Sound.ENTITY_WITHER_SPAWN, 0.9F, 0.2F)
                }
            }
        }
    }

    override fun onEnd(instance: ExpeditionInstance) {
        // Kill any remaining players
        // Players which have left the expedition, but haven't rejoined yet should have their player data cached in a file.
        // And then their join responsibility is now up to the PlayHandler class!
        val handler = instance.plugin.getModule(PlayHandler::class.java)
        // Done before getQuitPlayers, since this method may add to quit players
        instance.getRemainingPlayers().forEach {
            val player = it.player.player!!
            instance.plugin.getModule(ExpeditionListener::class.java).clearItemsOnDeath(player.uniqueId)
            player.health = 0.0
            instance.removePlayer(player, RemoveReason.DEATH)
            instance.world.strikeLightningEffect(player.location)
        }
        instance.getQuitPlayers().forEach {
            handler.addAbandon(it)
        }
        instance.plugin.launch(Dispatchers.IO) {
            handler.saveData()
        }
        instance.clearPlayers()
        instance.bossBar.removeAll()
        instance.bossBar.removeFlag(BarFlag.CREATE_FOG)
        instance.bossBar.removeFlag(BarFlag.DARKEN_SKY)
        instance.bossBar.color = BarColor.BLUE
    }
}

class RestorationPhase(expedition: Expedition) :
    Phase("${ChatColor.YELLOW}Restoring ❌", -1, -1, -1, 0L, {
    IdlePhase(expedition)
}) {
        private val jobs : MutableSet<Job> = ConcurrentHashMap.newKeySet()
    override fun onStart(instance: ExpeditionInstance) {
        queueJob(instance.plugin) {
            instance.brokenBlocks.values.forEach { pair ->
                val block = pair.first
                instance.world.blockWithChunk(instance.plugin, block.location) {
                    block.setType(pair.second, false)
                }
            }
            instance.brokenBlocks.clear()
        }
        queueJob(instance.plugin) {
            instance.areaInstances.forEach {
                it.dispose(instance.plugin, instance.world)
            }
            instance.areaInstances.clear()
        }
        queueJob(instance.plugin) {
            instance.world.entities.forEach { entity ->
                if(entity.isValid) {
                    instance.world.blockWithChunk(instance.plugin, entity.location) {
                        entity.remove()
                    }
                }
            }
            instance.clearMobSpawns()
        }
        instance.renderer.clearCursors()
        val world = instance.world
        world.isThundering = false
        world.setStorm(false)
        world.thunderDuration = 0
        instance.score = 0
    }

    private fun queueJob(plugin: RavinPlugin, block: suspend CoroutineScope.() -> Unit) {
        val job = plugin.launch(block = block)
        jobs.add(job)
        job.invokeOnCompletion {
            if(it == null) {
                jobs.remove(job)
            } else {
                warn("Encountered exception in Restoration Phase!", it)
            }
        }
    }

    override fun onTick(random: Random, instance: ExpeditionInstance) {
        if(jobs.isEmpty()) {
            next(instance)
        }
    }

    override fun onEnd(instance: ExpeditionInstance) {

    }
}
