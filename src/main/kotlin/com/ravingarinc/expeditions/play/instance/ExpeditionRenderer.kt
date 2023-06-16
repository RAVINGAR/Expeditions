package com.ravingarinc.expeditions.play.instance

import org.bukkit.Material
import org.bukkit.Material.*
import org.bukkit.entity.Player
import org.bukkit.map.MapCanvas
import org.bukkit.map.MapCursor
import org.bukkit.map.MapRenderer
import org.bukkit.map.MapView

class ExpeditionRenderer(val colourCache: Array<Byte>) : MapRenderer(false) {
    private val collection = ArrayList<MapCursor>()

    fun addCursor(cursor: MapCursor) {
        collection.add(cursor)
    }

    override fun render(view: MapView, canvas: MapCanvas, player: Player) {
        for(x in 0 until 128) {
            for(z in 0 until 128) {
                canvas.setPixel(x, z, colourCache[z * 128 + x])
            }
        }
        val cursors = canvas.cursors
        while(cursors.size() > 0) {
            cursors.removeCursor(cursors.getCursor(0))
        }
        collection.forEach {
            cursors.addCursor(it)
        }
    }

    enum class MapColour(val id: Byte, val predicate: (Material) -> Boolean) {
        NONE(0, { it.isAir }),
        GRASS(1, { it == GRASS_BLOCK || it == SLIME_BLOCK }),
        SAND(2, { it.name.startsWith("SAND") || it.name.startsWith("BIRCH") }),
        FIRE(4, { it == LAVA || it == Material.FIRE || it == TNT || it == org.bukkit.Material.REDSTONE_BLOCK }),
        ICE(5, { it.name.endsWith("ICE") }),
        METAL(6, { it == IRON_BLOCK || it == IRON_DOOR}),
        PLANT(7, { it == org.bukkit.Material.GRASS || it == org.bukkit.Material.DANDELION || it == org.bukkit.Material.ROSE_BUSH || it == org.bukkit.Material.POPPY
                || it.name.endsWith("LEAVES") || it.name.endsWith("TULIP") || it == org.bukkit.Material.CORNFLOWER
                || it == org.bukkit.Material.FERN || it == org.bukkit.Material.TALL_GRASS || it == org.bukkit.Material.LARGE_FERN}),
        SNOW(8, { it == org.bukkit.Material.SNOW || it == org.bukkit.Material.SNOW_BLOCK}),
        DIRT(10, { it == org.bukkit.Material.DIRT || it == org.bukkit.Material.COARSE_DIRT || it == org.bukkit.Material.FARMLAND || it == org.bukkit.Material.DIRT_PATH
                || it.name.startsWith("GRANITE")}),
        WATER(12, { it == org.bukkit.Material.WATER }),
        WOOD(13, { it.name.startsWith("OAK") }),
        QUARTZ(14, { it.name.startsWith("QUARTZ") || it.name.startsWith("DIORITE")} ),
        COLOR_ORANGE(15, { it.name.startsWith("ACACIA")}),
        COLOR_BROWN(26, { it.name.startsWith("DARK_OAK") || it.name.startsWith("SPRUCE")}),
        DEEPSLATE(59, { it.name.startsWith("DEEPSLATE")}),
        STONE(11, { true });
    }
}