package com.ravingarinc.expeditions.integration.models

import com.ravingarinc.api.I
import com.ravingarinc.api.module.RavinPlugin
import com.ravingarinc.api.module.SuspendingModuleListener
import net.kyori.adventure.text.Component
import org.bukkit.Material
import org.bukkit.entity.ArmorStand
import org.bukkit.entity.Entity
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.scheduler.BukkitRunnable
import org.bukkit.util.Vector
import java.util.concurrent.ConcurrentHashMap
import java.util.logging.Level
import kotlin.math.abs
import kotlin.math.min

class ModelManager(plugin: RavinPlugin) : SuspendingModuleListener(ModelManager::class.java, plugin, isRequired = false) {

    private val attachedEntities: MutableMap<Player, Entity> = ConcurrentHashMap()
    private var runnable: BukkitRunnable? = null

    override suspend fun suspendLoad() {
        val newRunnable = object : BukkitRunnable() {
            override fun run() {
                val iterator = attachedEntities.iterator()
                while(iterator.hasNext()) {
                    val entry = iterator.next()
                    val player = entry.key
                    val loc = player.location
                    entry.value.setRotation(loc.yaw, loc.pitch)

                    val existingVelocity = player.velocity
                    if(existingVelocity.isZero) continue
                    val dir = existingVelocity.add(loc.direction.multiply(0.8))
                    val absX = abs(dir.x)
                    val absZ = abs(dir.z)
                    val xFactor = dir.x / absX
                    val zFactor = dir.z / absZ
                    val velocity = Vector(xFactor * min(absX, 1.0) * 0.6, -0.15, zFactor * min(absZ, 1.0) * 0.6)
                    player.velocity = velocity

                }
            }
        }
        newRunnable.runTaskTimer(plugin, 20L, 5L)
        runnable = newRunnable
    }

    fun attachModel(player: Player) {
        if(attachedEntities.containsKey(player)) {
            I.log(Level.WARNING, "Cannot attach model to player ${player.name} which already has a model attached!");
            return
        }
        val entity = player.world.spawn(player.eyeLocation, ArmorStand::class.java) {
            it.customName(Component.text("expeditions_parachute"))
            it.isCustomNameVisible = false
            it.isInvulnerable = true
            it.isInvisible = true
            it.isMarker = true
            it.setCanMove(true)
            it.setCanTick(false)
            it.isMarker = true

            val item = ItemStack(Material.CHEST, 1)
            val meta = item.itemMeta!!
            meta.setCustomModelData(69)
            item.setItemMeta(meta)
            it.equipment.helmet = item
        }
        player.addPassenger(entity)
        attachedEntities[player] = entity
    }

    fun detachModel(player: Player) {
        val entity = attachedEntities.remove(player) ?: return
        entity.remove()
    }

    fun hasModel(player: Player) : Boolean {
        return attachedEntities.containsKey(player)
    }

    override suspend fun suspendCancel() {
        runnable?.cancel()
        runnable = null
        ArrayList(attachedEntities.keys).forEach { detachModel(it) }

        attachedEntities.clear()
    }

}