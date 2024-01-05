package com.ravingarinc.expeditions.play.mob

import com.ravingarinc.api.module.warn
import io.lumine.mythic.api.adapters.AbstractLocation
import io.lumine.mythic.api.mobs.MythicMob
import io.lumine.mythic.bukkit.BukkitAdapter
import io.lumine.mythic.bukkit.MythicBukkit
import org.bukkit.Location
import org.bukkit.World
import org.bukkit.entity.Entity
import org.bukkit.entity.EntityType
import org.bukkit.util.BlockVector

sealed interface MobType {
    fun identifier() : String

    fun spawn(level: Int, vector: BlockVector, world: World) : Entity?

    fun isSameAs(entity: Entity) : Boolean

    fun reload()
}

class MythicMobType(private val identifier: String) : MobType {
    private var mob: MythicMob? = null

    override fun spawn(level: Int, vector: BlockVector, world: World) : Entity? {
        getMythicMob()?.let {
            val spawned = it.spawn(
                AbstractLocation(BukkitAdapter.adapt(world), vector.x + 0.5, vector.y, vector.z + 0.5),
                level.toDouble()
            )
            return spawned.entity.bukkitEntity
        }
        return null
    }

    private fun getMythicMob() : MythicMob? {
        if(mob == null) {
            mob = MythicBukkit.inst().mobManager.getMythicMob(identifier).orElse(null)
            if(mob == null) {
                warn("Could not spawn mythic mob of type '$identifier' as this type does not exist!")
            }
        }
        return mob
    }

    override fun isSameAs(entity: Entity): Boolean {
        val mob = MythicBukkit.inst().mobManager.getActiveMob(entity.uniqueId).orElse(null) ?: return false
        return mob.type.internalName == identifier
    }

    override fun reload() {
        mob = null
    }

    override fun identifier(): String {
        return "mythic:$identifier"
    }
}

class VanillaMobType(private val type: EntityType) : MobType {
    override fun spawn(level: Int, vector: BlockVector, world: World): Entity {
        return world.spawnEntity(Location(world, vector.x + 0.5, vector.y, vector.z + 0.5), type, true)
    }

    override fun isSameAs(entity: Entity): Boolean {
        return entity.type == type
    }

    override fun reload() {}

    override fun identifier(): String {
        return "vanilla:${type.name.lowercase()}"
    }
}

object EmptyMobType : MobType {
    override fun spawn(level: Int, vector: BlockVector, world: World): Entity? { return null }

    override fun isSameAs(entity: Entity): Boolean {
        return false
    }

    override fun reload() {}

    override fun identifier(): String {
        return "none:none"
    }
}