package com.ravingarinc.expeditions.integration.npc

import com.ravingarinc.api.module.RavinPlugin
import org.bukkit.World
import org.bukkit.entity.Player
import org.jetbrains.annotations.Blocking

interface ExpeditionNPC {

    fun spawn(x: Double, y: Double, z: Double, world: World)

    fun destroy()

    fun teleport(x: Double, y: Double, z: Double, world: World)

    fun select(plugin: RavinPlugin)

    fun unselect(plugin: RavinPlugin)

    @Blocking
    fun startFollowing(player: Player)

    @Blocking
    fun stopFollowing(player: Player?)

    @Blocking
    fun getFollowing() : Player?

    fun isValid() : Boolean {
        return false
    }

    fun identifier() : String

    fun numericalId() : Int
}

class EmptyNPC : ExpeditionNPC {
    override fun spawn(x: Double, y: Double, z: Double, world: World) {
    }

    override fun destroy() {

    }

    override fun teleport(x: Double, y: Double, z: Double, world: World) {

    }

    override fun startFollowing(player: Player) {

    }

    override fun stopFollowing(player: Player?) {

    }

    override fun select(plugin: RavinPlugin) {

    }

    override fun unselect(plugin: RavinPlugin) {

    }

    override fun getFollowing(): Player? {
        return null
    }

    override fun numericalId(): Int {
        return -1
    }

    override fun identifier(): String {
        return ""
    }
}