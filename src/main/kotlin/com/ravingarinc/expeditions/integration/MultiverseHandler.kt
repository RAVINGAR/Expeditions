package com.ravingarinc.expeditions.integration

import com.onarandombox.MultiverseCore.MultiverseCore
import com.onarandombox.MultiverseCore.enums.AllowedPortalType
import com.onarandombox.MultiverseCore.event.MVWorldDeleteEvent
import com.ravingarinc.api.module.ModuleLoadException
import com.ravingarinc.api.module.RavinPlugin
import com.ravingarinc.api.module.SuspendingModuleListener
import com.ravingarinc.api.module.warn
import com.ravingarinc.expeditions.persistent.ConfigManager
import org.bukkit.Difficulty
import org.bukkit.World
import org.bukkit.event.EventHandler
import org.checkerframework.checker.units.qual.m
import java.util.Hashtable

class MultiverseHandler(plugin: RavinPlugin) : SuspendingModuleListener(MultiverseHandler::class.java, plugin) {
    private lateinit var multiverse: MultiverseCore
    private var maxInstances: Int = 4
    private val clonedWorldNames: MutableMap<String, Int> = Hashtable()
    private val deletedBySelf: MutableSet<String> = HashSet()

    override suspend fun suspendLoad() {
        val core = plugin.server.pluginManager.getPlugin("Multiverse-Core")
            ?: throw ModuleLoadException(this, ModuleLoadException.Reason.EXCEPTION, IllegalStateException("Could not find Multiverse-Core!"))
        multiverse = core as MultiverseCore
        maxInstances = plugin.getModule(ConfigManager::class.java).config.config.getInt("max-instances", 4)

        super.suspendLoad()
    }



    /**
     * Attempt to clone a world, giving it the same name with an appended number. Returns a world if successfully cloned,
     * or returns null if an error occurs or max instances have been reached.
     */
    fun cloneWorld(world: World) : World? {
        val name = world.name
        val integer = (clonedWorldNames[name] ?: 0) + 1
        if(integer > maxInstances) {
            return null
        }
        val newName = "${name}_$integer"
        if(plugin.server.getWorld(newName) != null) {
            warn("Could not clone world '${name}' as a world with the name '${newName}' already exists! " +
                    "This should not have occurred. Please stop the server, delete that world then try again!")
            return null
        }
        if(multiverse.mvWorldManager.cloneWorld(name, newName)) {
            clonedWorldNames[name] = integer
            val mvWorld = multiverse.mvWorldManager.getMVWorld(newName)
            if(mvWorld == null) {
                warn("Something went wrong cloning world '$name' via Multiverse!")
                return null
            }
            mvWorld.bedRespawn = false
            mvWorld.difficulty = Difficulty.HARD
            mvWorld.isHidden = true
            mvWorld.setKeepSpawnInMemory(false)
            mvWorld.allowPortalMaking(AllowedPortalType.NONE)
            return mvWorld.cbWorld
        } else {
            warn("Could not clone world '${name}' for unknown reason!")
            return null
        }
    }

    fun deleteWorld(world: World) : Boolean {
        deletedBySelf.add(world.name)
        val result = multiverse.mvWorldManager.deleteWorld(world.name, true, true)
        deletedBySelf.remove(world.name)
        return result
    }

    override suspend fun suspendCancel() {
        super.suspendCancel()

        clonedWorldNames.clear()
        deletedBySelf.clear()
    }

    @EventHandler
    fun onWorldDelete(event: MVWorldDeleteEvent) {
        val world = event.world.cbWorld
        if(deletedBySelf.contains(world.name)) {
            return // If we are deleting the world then do nothing
        }
        // TODO Check basically if this is an instance world and handle appropriately
    }
}