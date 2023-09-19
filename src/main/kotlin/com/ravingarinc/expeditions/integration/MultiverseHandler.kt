package com.ravingarinc.expeditions.integration

import com.onarandombox.MultiverseCore.MultiverseCore
import com.onarandombox.MultiverseCore.enums.AllowedPortalType
import com.onarandombox.MultiverseCore.event.MVWorldDeleteEvent
import com.ravingarinc.api.I
import com.ravingarinc.api.module.ModuleLoadException
import com.ravingarinc.api.module.RavinPlugin
import com.ravingarinc.api.module.SuspendingModuleListener
import com.ravingarinc.api.module.warn
import org.bukkit.Difficulty
import org.bukkit.GameRule
import org.bukkit.World
import org.bukkit.event.EventHandler
import java.io.File
import java.util.logging.Level

class MultiverseHandler(plugin: RavinPlugin) : SuspendingModuleListener(MultiverseHandler::class.java, plugin) {
    private lateinit var multiverse: MultiverseCore
    private val clonedWorldNames: MutableSet<String> = HashSet()
    private val deletedBySelf: MutableSet<String> = HashSet()

    override suspend fun suspendLoad() {
        val core = plugin.server.pluginManager.getPlugin("Multiverse-Core")
            ?: throw ModuleLoadException(this, ModuleLoadException.Reason.EXCEPTION, IllegalStateException("Could not find Multiverse-Core!"))
        multiverse = core as MultiverseCore

        super.suspendLoad()
    }



    /**
     * Attempt to clone a world, giving it the same name with an appended number. Returns a world if successfully cloned,
     * or returns null if an error occurs
     */
    fun cloneWorld(world: World) : World? {
        val name = world.name
        val newName = getFreeName(name)
        if(newName == null) {
            warn("Could not find free world name for new instance!")
            return null
        }
        val existingWorld = plugin.server.getWorld(newName)
        if(existingWorld != null && !deleteWorld(existingWorld)) {
            warn("Could not clone world '${name}' as a world with the name '${newName}' already exists! " +
                    "This should not have occurred. Please stop the server, delete that world then try again!")
            return null
        }
        copyWorldGuardFiles(name, newName)
        if(multiverse.mvWorldManager.cloneWorld(name, newName)) {
            clonedWorldNames.add(newName)
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
            mvWorld.cbWorld.setGameRule(GameRule.KEEP_INVENTORY, false)
            mvWorld.autoLoad = true
            //mvWorld.setRespawnToWorld()
            return mvWorld.cbWorld
        } else {
            warn("Could not clone world '${name}' for unknown reason!")
            return null
        }
    }

    fun copyWorldGuardFiles(name: String, newName: String) {
        val parent = File("${plugin.dataFolder.parent}\\WorldGuard\\worlds")
        if(!parent.exists()) return
        val origin = File(parent, name)
        if(!origin.exists()) return
        origin.copyRecursively(File(parent, newName), overwrite = true) { _, exception ->
            I.log(Level.SEVERE, "Encountered exception whilst copying WorldGuard files for expedition world '$name'", exception)
            return@copyRecursively OnErrorAction.TERMINATE
        }
    }

    private fun getFreeName(world: String) : String? {
        for(i in 0..128) {
            val freeName = "${world}_$i"
            if(!clonedWorldNames.contains(freeName)) return freeName
        }
        return null
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
        if(clonedWorldNames.contains(world.name)) {
            I.log(Level.SEVERE, "Instanced world was almost deleted manually! Please do not delete Expedition worlds!")
            event.isCancelled = true
        }
    }
}