package com.ravingarinc.expeditions.locale.type

import org.bukkit.World

class Expedition(val identifier: String,
                 val displayName: String,
                 val world: World,
                 val maxPlayers: Int,
                 val centreX: Int,
                 val centreZ: Int,
                 val radius: Int,
                 val mobSpawnAmount: Int,
                 val calmPhaseDuration: Long,
                 val stormPhaseDuration: Long,
                 val mobInterval: Long,
                 val mobModifier: Double,
                 val lootInterval: Long,
                 val lootModifier: Double) {
    private val areas: MutableList<Area> = ArrayList()

    fun addArea(area: Area) {
        areas.add(area)
    }

    fun getArea(x: Int, y: Int, z: Int) : Area? {
        areas.forEach {
            if(it.isInArea(x, y, z)) {
                return it
            }
        }
        return null
    }

    fun getAreas() : List<Area> {
        return areas
    }
}