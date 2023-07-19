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
import org.bukkit.entity.LivingEntity
import org.bukkit.util.BlockVector

sealed interface MobType {
    fun identifier() : String

    fun spawn(level: Int, vector: BlockVector, world: World) : Entity?

    fun reload()
}

class MythicMobType(private val identifier: String) : MobType {
    private var mob: MythicMob? = null

    override fun spawn(level: Int, vector: BlockVector, world: World) : Entity? {
        getMythicMob()?.let {
            val spawned = it.spawn(AbstractLocation(BukkitAdapter.adapt(world), vector.x, vector.y, vector.z), level.toDouble())
            val bukkitEntity = spawned.entity.bukkitEntity
            bukkitEntity.isPersistent = true
            if(bukkitEntity is LivingEntity) {
                bukkitEntity.removeWhenFarAway = false
            }
            return bukkitEntity
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

    override fun reload() {
        mob = null
    }

    override fun identifier(): String {
        return "mythic:$identifier"
    }
}

class VanillaMobType(private val type: EntityType) : MobType {
    override fun spawn(level: Int, vector: BlockVector, world: World): Entity {
        val entity = world.spawnEntity(Location(world, vector.x, vector.y, vector.z), type, true)
        entity.isPersistent = true
        if(entity is LivingEntity) {
            entity.removeWhenFarAway = false
        }
        return entity
    }

    override fun reload() {}

    override fun identifier(): String {
        return "vanilla:${type.name.lowercase()}"
    }
}

object EmptyMobType : MobType {
    override fun spawn(level: Int, vector: BlockVector, world: World): Entity? { return null }

    override fun reload() {}

    override fun identifier(): String {
        return "none:none"
    }
}