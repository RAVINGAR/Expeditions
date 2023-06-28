package com.ravingarinc.expeditions.play.instance

import com.ravingarinc.expeditions.locale.type.Expedition
import org.bukkit.Material
import org.bukkit.Material.*
import org.bukkit.entity.Player
import org.bukkit.map.*
import java.awt.Color
import java.util.*

class ExpeditionRenderer(val expedition: Expedition) : MapRenderer(false) {
    private val collection = ArrayList<MapCursor>()
    private val players: MutableSet<UUID> = HashSet()

    fun addCursor(cursor: MapCursor) {
        collection.add(cursor)
    }

    fun removePlayer(player: Player) {
        players.remove(player.uniqueId)
    }

    override fun render(view: MapView, canvas: MapCanvas, player: Player) {
        if(!players.contains(player.uniqueId)) {
            for(x in 0 until 128) {
                for(z in 0 until 128) {
                    //warn("Debug -> Picked colour ${expedition.colourCache[z * 128 + x]}")
                    canvas.setPixel(x, z, expedition.colourCache[z * 128 + x])
                }
            }
            players.add(player.uniqueId)
        }

        val cursors = canvas.cursors
        while(cursors.size() > 0) {
            cursors.removeCursor(cursors.getCursor(0))
        }
        collection.forEach {
            cursors.addCursor(it)
        }
        val loc = player.location
        val byteX = (((loc.x - expedition.centreX) / expedition.radius.toFloat()) * 128).toInt().toByte()
        val byteZ = (((loc.z - expedition.centreZ) / expedition.radius.toFloat()) * 128).toInt().toByte()
        val direction = ((((loc.yaw + 360F) % 360F) / 360F) * 15F).toInt().toByte()
        cursors.addCursor(MapCursor(byteX, byteZ, direction, MapCursor.Type.WHITE_POINTER, true))
    }

    enum class MapColour(val id: Byte, val predicate: (Material) -> Boolean) {
        NONE((0).toByte(), { it.isAir }),
        GRASS(MapPalette.matchColor(Color.GREEN), { it == GRASS_BLOCK || it == SLIME_BLOCK }),
        SAND(MapPalette.matchColor(240, 242, 183), { it.name.startsWith("SAND") || it.name.startsWith("BIRCH") }),
        FIRE(MapPalette.matchColor(Color.RED), { it == LAVA || it == Material.FIRE || it == TNT || it == org.bukkit.Material.REDSTONE_BLOCK }),
        ICE(MapPalette.matchColor(172, 247, 245), { it.name.endsWith("ICE") || it.name.startsWith("SNOW") }),
        PLANT(MapPalette.matchColor(Color.GREEN), { it == org.bukkit.Material.GRASS || it == org.bukkit.Material.DANDELION || it == org.bukkit.Material.ROSE_BUSH || it == org.bukkit.Material.POPPY
                || it.name.endsWith("LEAVES") || it.name.endsWith("TULIP") || it == org.bukkit.Material.CORNFLOWER
                || it == org.bukkit.Material.FERN || it == org.bukkit.Material.TALL_GRASS || it == org.bukkit.Material.LARGE_FERN}),
        SNOW(MapPalette.matchColor(255, 255, 255), { it == org.bukkit.Material.SNOW || it == org.bukkit.Material.SNOW_BLOCK}),
        DIRT(MapPalette.matchColor(100, 81, 22), { it == org.bukkit.Material.DIRT || it == org.bukkit.Material.COARSE_DIRT || it == org.bukkit.Material.FARMLAND || it == org.bukkit.Material.DIRT_PATH
                || it.name.startsWith("GRANITE")}),
        WATER(MapPalette.matchColor(Color.BLUE), { it == org.bukkit.Material.WATER }),
        WOOD(MapPalette.matchColor(129, 119, 26), { it.name.startsWith("OAK") }),
        QUARTZ(MapPalette.matchColor(219, 219, 219), { it.name.startsWith("QUARTZ") || it.name.startsWith("DIORITE")} ),
        COLOR_ORANGE(MapPalette.matchColor(Color.ORANGE), { it.name.startsWith("ACACIA")}),
        COLOR_BROWN(MapPalette.matchColor(100, 81, 22), { it.name.startsWith("DARK_OAK") || it.name.startsWith("SPRUCE")}),
        DEEPSLATE(MapPalette.matchColor(81, 81, 81), { it.name.startsWith("DEEPSLATE")}),
        STONE(MapPalette.matchColor(152, 152, 152), { true });
    }
}