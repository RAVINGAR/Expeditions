package com.ravingarinc.expeditions.integration.models

import com.ravingarinc.api.I
import com.ravingarinc.api.module.ModuleLoadException
import com.ravingarinc.api.module.RavinPlugin
import com.ravingarinc.api.module.SuspendingModuleListener
import com.ravingarinc.api.module.warn
import com.ravingarinc.expeditions.persistent.ConfigManager
import com.ticxo.modelengine.api.ModelEngineAPI
import net.kyori.adventure.text.Component
import org.bukkit.entity.ArmorStand
import org.bukkit.entity.Entity
import org.bukkit.entity.Player
import org.bukkit.scheduler.BukkitRunnable
import java.util.concurrent.ConcurrentHashMap
import java.util.logging.Level

class ModelManager(plugin: RavinPlugin) : SuspendingModuleListener(ModelManager::class.java, plugin, isRequired = false) {
    private var parachuteModel: String = ""

    private val attachedEntities: MutableMap<Player, Entity> = ConcurrentHashMap()
    private var runnable: BukkitRunnable? = null

    override suspend fun suspendLoad() {
        if(plugin.server.pluginManager.getPlugin("ModelEngine") == null) {
            throw ModuleLoadException(this, ModuleLoadException.Reason.PLUGIN_DEPEND, IllegalStateException("ModelEngine is required for this module!"))
        }

        parachuteModel = plugin.getModule(ConfigManager::class.java).config.config.getString("parachute.model-id") ?: ""
        if(parachuteModel.isEmpty()) return
        val newRunnable = object : BukkitRunnable() {
            override fun run() {
                val iterator = attachedEntities.iterator()
                while(iterator.hasNext()) {
                    val entry = iterator.next()
                    val player = entry.key
                    val eye = player.eyeLocation
                    entry.value.setRotation(eye.yaw, eye.pitch)
                }
            }
        }
        newRunnable.runTaskTimer(plugin, 20L, 5L)
        runnable = newRunnable
    }

    fun attachParachuteModel(player: Player) {
        attachModel(player, parachuteModel)
    }

    fun attachModel(player: Player, modelId: String) {
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
        }
        player.addPassenger(entity)
        val model = ModelEngineAPI.createActiveModel(modelId)
        if (model == null) {
            warn("Could not spawn ModelEngine model as no model with the id '${modelId}' could be found!")
            return
        }
        val modeledEntity = ModelEngineAPI.createModeledEntity(entity)
        if (modeledEntity == null) {
            warn("Could not create ModelEngine model for unknown reason!")
            return
        }
        modeledEntity.addModel(model, true)
        attachedEntities[player] = entity
    }

    fun detachModel(player: Player) {
        val entity = attachedEntities.remove(player) ?: return
        val modeledEntity = ModelEngineAPI.getModeledEntity(entity.uniqueId)
        modeledEntity.destroy()
        entity.remove()
    }

    override suspend fun suspendCancel() {
        runnable?.cancel()
        runnable = null
        ArrayList(attachedEntities.keys).forEach { detachModel(it) }
    }

}