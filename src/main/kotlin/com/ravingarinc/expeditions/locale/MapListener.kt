package com.ravingarinc.expeditions.locale

import com.ravingarinc.api.module.RavinPlugin
import com.ravingarinc.api.module.SuspendingModuleListener
import com.ravingarinc.expeditions.api.getMaterialList
import com.ravingarinc.expeditions.persistent.ConfigManager
import com.ravingarinc.expeditions.play.PlayHandler
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.Event
import org.bukkit.event.EventHandler
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.block.BlockPlaceEvent
import org.bukkit.event.entity.EntityDeathEvent
import org.bukkit.event.entity.EntitySpawnEvent
import org.bukkit.event.player.*
import java.util.*

class MapListener(plugin: RavinPlugin) : SuspendingModuleListener(MapListener::class.java, plugin, PlayHandler::class.java) {
    private lateinit var handler: PlayHandler
    private val breakableBlocks: MutableSet<Material> = EnumSet.noneOf(Material::class.java)

    private val movementCooldown: MutableMap<UUID, Long> = HashMap()
    override suspend fun suspendLoad() {
        handler = plugin.getModule(PlayHandler::class.java)
        val config = plugin.getModule(ConfigManager::class.java)
        config.config.config.getMaterialList("general.breakable-blocks").forEach {
            breakableBlocks.add(it)
        }
        super.suspendLoad()
    }

    override suspend fun suspendCancel() {
        super.suspendCancel()
        breakableBlocks.clear()
    }
    @EventHandler
    fun onPlayerJoin(event: PlayerJoinEvent) {
        val player = event.player
        handler.getInstances().values.forEach { list -> list.forEach {
            if(it.onJoinEvent(player)) return
        }}
        // If player was not previously joined in any event. Check if they abandoned!
        if(handler.didAbandon(player)) {
            handler.removeAbandon(player)
            player.inventory.clear()
            player.health = 0.0
        }
    }

    @EventHandler
    fun onPlayerQuit(event: PlayerQuitEvent) {
        val player = event.player
        handler.getJoinedExpedition(player)?.let {
            it.onQuitEvent(player)
            handler.removeJoinedExpedition(player)
        }
        movementCooldown.remove(player.uniqueId)
    }

    @EventHandler
    fun onEntityDeath(event: EntityDeathEvent) {
        handler.getInstances().values.forEach { list -> list.forEach {
            if(it.onDeathEvent(event)) return
        } }
    }

    @EventHandler
    fun onEntitySpawn(event: EntitySpawnEvent) {
        val entity = event.entity
        if(entity !is Player) return
        // todo does this need a delay?
        handler.getJoinedExpedition(entity)?.let {
            it.onSpawnEvent(entity)
            handler.removeJoinedExpedition(entity)
        }
    }

    @EventHandler
    fun onPlayerInteract(event: PlayerInteractEvent) {
        val block = event.clickedBlock ?: return
        if(block.type != Material.CHEST) return
        handler.getJoinedExpedition(event.player)?.let {
            it.onBlockInteract(block, event.player)
            event.setUseInteractedBlock(Event.Result.DENY)
        }
    }

    @EventHandler
    fun onBlockPlace(event: BlockPlaceEvent) {
        val player = event.player
        handler.getJoinedExpedition(player)?.let {
            if(player.world == event.block.world) {
                event.setBuild(false)
                event.isCancelled = true
            }
        }
    }

    @EventHandler
    fun onBlockBreak(event: BlockBreakEvent) {
        val player = event.player
        handler.getJoinedExpedition(player)?.let {
            val block = event.block
            if(player.world != block.world) return@let
            if(breakableBlocks.contains(block.type)) {
                it.breakBlock(block, block.type)
            } else {
                event.isCancelled = true
            }
        }
    }

    @EventHandler
    fun onShiftEvent(event: PlayerToggleSneakEvent) {
        handler.getJoinedExpedition(event.player)?.onSneakEvent(event.player, event.isSneaking)
    }

    @EventHandler
    fun onMoveEvent(event: PlayerMoveEvent) {
        val player = event.player
        val time = System.currentTimeMillis()
        val lastTime = movementCooldown[player.uniqueId] ?: time
        if(time - lastTime > 500L) {
            movementCooldown[player.uniqueId] = time
            handler.getJoinedExpedition(event.player)?.onMoveEvent(player)
        }
    }
}