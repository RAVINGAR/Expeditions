package com.ravingarinc.expeditions.locale

import com.destroystokyo.paper.event.entity.EntityRemoveFromWorldEvent
import com.github.shynixn.mccoroutine.bukkit.launch
import com.github.shynixn.mccoroutine.bukkit.ticks
import com.ravingarinc.api.module.RavinPlugin
import com.ravingarinc.api.module.SuspendingModuleListener
import com.ravingarinc.expeditions.api.getBlockVector
import com.ravingarinc.expeditions.api.getMaterialList
import com.ravingarinc.expeditions.api.getWorld
import com.ravingarinc.expeditions.persistent.ConfigManager
import com.ravingarinc.expeditions.play.PlayHandler
import kotlinx.coroutines.delay
import org.bukkit.ChatColor
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.entity.MagmaCube
import org.bukkit.entity.Player
import org.bukkit.event.Event
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.block.BlockExplodeEvent
import org.bukkit.event.block.BlockPlaceEvent
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.event.entity.EntityDeathEvent
import org.bukkit.event.entity.EntityExplodeEvent
import org.bukkit.event.player.*
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.util.Vector
import java.util.*

class ExpeditionListener(plugin: RavinPlugin) : SuspendingModuleListener(ExpeditionListener::class.java, plugin, true, PlayHandler::class.java) {
    private lateinit var handler: PlayHandler
    private lateinit var manager: ExpeditionManager
    private val breakableBlocks: MutableSet<Material> = EnumSet.noneOf(Material::class.java)

    private val movementCooldown: MutableMap<UUID, Long> = HashMap()
    private val lastLocation: MutableMap<UUID, Vector> = HashMap()

    private val allowedCommands: MutableSet<String> = HashSet()

    private lateinit var fallbackLocation: Location
    override suspend fun suspendLoad() {
        handler = plugin.getModule(PlayHandler::class.java)
        manager = plugin.getModule(ExpeditionManager::class.java)
        val config = plugin.getModule(ConfigManager::class.java)
        config.config.config.getMaterialList("general.breakable-blocks").forEach {
            breakableBlocks.add(it)
        }
        val vec = config.config.config.getBlockVector("general.fallback-location")
        val world = config.config.config.getWorld("general.fallback-world")
        fallbackLocation = if(vec == null || world == null) {
            plugin.server.worlds[0].spawnLocation
        } else {
            Location(world, vec.x + 0.5, vec.y, vec.z + 0.5)
        }
        config.config.config.getStringList("general.allowed-commands").forEach {
            allowedCommands.add(it.replace("/", ""))
        }

        super.suspendLoad()
    }

    override suspend fun suspendCancel() {
        super.suspendCancel()
        breakableBlocks.clear()
        allowedCommands.clear()
    }
    @EventHandler
    fun onPlayerJoin(event: PlayerJoinEvent) {
        val player = event.player
        movementCooldown[player.uniqueId] = System.currentTimeMillis()
        lastLocation[player.uniqueId] = player.location.toVector()
        handler.getInstances().values.forEach { list -> list.forEach {
            if(it.onJoinEvent(player)) return
        }}
        // If player was not previously joined in any event. Check if they abandoned!
        plugin.launch {
            delay(5.ticks)
            if(handler.didAbandon(player)) {
                handler.removeAbandon(player)
                player.sendMessage("${ChatColor.RED}You previously abandoned an expedition! You were devoured by the storm and lost all your items!")
                player.inventory.clear()
                player.health = 0.0
            } else {
                if(handler.isExpeditionWorld(player.world)) {
                    player.sendMessage("${ChatColor.RED}You have been removed from that expired expedition!")
                    player.teleport(fallbackLocation)
                }
            }
        }

    }



    @EventHandler
    fun onPlayerQuit(event: PlayerQuitEvent) {
        val player = event.player
        handler.getJoinedExpedition(player)?.onQuitEvent(player)
        movementCooldown.remove(player.uniqueId)
        lastLocation.remove(player.uniqueId)
    }

    @EventHandler
    fun onEntityDeath(event: EntityDeathEvent) {
        handler.getInstances().values.forEach { list -> list.forEach {
            if(it.onDeathEvent(event)) return
        } }
    }

    @EventHandler
    fun onEntitySpawn(event: PlayerRespawnEvent) {
        val player = event.player

        handler.removeRespawn(player)?.let {
            event.respawnLocation = it.previousLocale
        }
    }

    @EventHandler
    fun onCommandProcessEvent(event: PlayerCommandPreprocessEvent) {
        val player = event.player
        handler.getJoinedExpedition(player)?.let {
            val split = event.message.split(" ".toRegex())
            if(player.hasPermission("expeditions.admin.bypass")) {
                return@let
            }
            if(!allowedCommands.contains(split[0].replace("/", ""))) {
                player.sendMessage("${ChatColor.RED}You cannot use that command whilst in an expedition!")
                event.isCancelled = true
            }
        }
    }

    @EventHandler
    fun onEntityDamageEvent(event: EntityDamageByEntityEvent) {
        val player = event.damager
        if(player is Player && event.entity is MagmaCube) {
            handler.getJoinedExpedition(player)?.let {
                if(it.onBlockInteract(player.world.getBlockAt(event.entity.location), player)) {
                    event.isCancelled = true
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    fun onPlayerDamageEvent(event: EntityDamageEvent) {
        val entity = event.entity
        if(entity is Player) handler.getJoinedExpedition(entity)?.onPlayerDamage(entity)
    }

    @EventHandler
    fun onPlayerInteractMob(event: PlayerInteractEvent) {
        if(event.hand != EquipmentSlot.HAND) return
        val player = event.player
        handler.getJoinedExpedition(player)?.let { instance ->
            val target = player.getTargetEntity(4, false)
            if(target == null) {
                player.getTargetBlockExact(4)?.let {
                    instance.onBlockInteract(it, player)
                }
            } else {
                if (target is MagmaCube) {
                    instance.onBlockInteract(player.world.getBlockAt(target.location), player)
                } else { }
            }
        }
    }

    @EventHandler
    fun onPlayerInteractEntity(event: PlayerInteractEntityEvent) {
        if(event.hand != EquipmentSlot.HAND) return
        val entity = event.rightClicked
        if(entity is MagmaCube) {
            handler.getJoinedExpedition(event.player)
                ?.onBlockInteract(event.player.world.getBlockAt(entity.location), event.player)
        }

    }

    @EventHandler
    fun onPlayerInteract(event: PlayerInteractEvent) {
        if(event.hand != EquipmentSlot.HAND) return
        val block = event.clickedBlock ?: return
        if(block.type != manager.getLootBlock()) return
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
    fun onEntityExplode(event: EntityExplodeEvent) {
        if(event.blockList().isEmpty()) return
        if(handler.isExpeditionWorld(event.entity.world)) {
            event.isCancelled = true
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    fun onBlockExplode(event: BlockExplodeEvent) {
        if(event.blockList().isEmpty()) return
        if(handler.isExpeditionWorld(event.block.world)) {
            event.isCancelled = true
        }
    }

    @EventHandler
    fun onMoveEvent(event: PlayerMoveEvent) {
        val player = event.player
        val time = System.currentTimeMillis()
        val lastTime = movementCooldown[player.uniqueId] ?: time
        if(time - lastTime > 1000L) {
            movementCooldown[player.uniqueId] = time
            val vector = player.location.toVector()
            val lastVector = lastLocation[player.uniqueId] ?: vector
            if(vector != lastVector) {
                handler.getJoinedExpedition(player)?.onMoveEvent(player)
            }
        }
    }

    @EventHandler
    fun onEntityDespawn(event: EntityRemoveFromWorldEvent) {
        val entity = event.entity
        val loc = entity.location
        handler.getInstance(loc.world)?.decrementMobSpawns(entity.uniqueId)
    }
}