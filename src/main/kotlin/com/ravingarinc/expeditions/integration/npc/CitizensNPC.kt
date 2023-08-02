package com.ravingarinc.expeditions.integration.npc

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import net.citizensnpcs.api.CitizensAPI
import net.citizensnpcs.api.ai.tree.Behavior
import net.citizensnpcs.api.ai.tree.BehaviorStatus
import net.citizensnpcs.api.npc.NPC
import org.bukkit.Location
import org.bukkit.World
import org.bukkit.entity.EntityType
import org.bukkit.entity.Player
import org.bukkit.event.player.PlayerTeleportEvent

class CitizensNPC(private val identifier: String) : ExpeditionNPC {
    private var npc: NPC? = null
    private var followingPlayer: Player? = null
    private var playerLock = Mutex(false)

    override fun spawn(x: Double, y: Double, z: Double, world: World) {
        if(npc == null) {
            npc = CitizensAPI.getNPCRegistry().createNPC(EntityType.PLAYER, identifier)
            npc?.let {
                it.spawn(Location(world, x, y, z))
                it.isProtected = true
                it.defaultGoalController.addBehavior(FollowingBehaviour(this), 0)
            }
        }
    }

    fun getInternalNPC() : NPC? {
        return npc
    }

    override fun destroy() {
        npc?.let {
            it.destroy()
            npc = null
        }
    }

    override fun identifier(): String {
        return identifier
    }

    override fun teleport(x: Double, y: Double, z: Double, world: World) {
        npc?.teleport(Location(world, x, y, z), PlayerTeleportEvent.TeleportCause.PLUGIN)
    }

    override fun startFollowing(player: Player) {
        val following = getFollowing()
        if(following == player) return
        runBlocking {
            playerLock.withLock {
                npc?.let {
                    followingPlayer = player
                }
            }
        }

    }

    override fun stopFollowing(player: Player?) {
        if(getFollowing() != player) return
        runBlocking {
            playerLock.withLock { npc?.let { followingPlayer = null } }
        }

    }

    override fun getFollowing(): Player? {
        return runBlocking {
            return@runBlocking playerLock.withLock {
                return@withLock followingPlayer
            }
        }
    }

    override fun isValid(): Boolean {
        return npc != null && npc?.entity?.isValid ?: false
    }

    override fun numericalId(): Int {
        return npc?.id ?: -1
    }
}

class FollowingBehaviour(private val npc: CitizensNPC) : Behavior {
    override fun reset() {

    }

    override fun run(): BehaviorStatus {
        val following = npc.getFollowing()
        npc.getInternalNPC()?.let {
            val navigator = it.navigator
            if(following == null) {
                if(navigator.isNavigating) it.navigator.cancelNavigation()
            } else {
                navigator.setTarget(following, false)
            }
            return BehaviorStatus.RUNNING
        }
        return BehaviorStatus.RESET_AND_REMOVE
    }

    override fun shouldExecute(): Boolean {
        return npc.isValid()
    }

}

