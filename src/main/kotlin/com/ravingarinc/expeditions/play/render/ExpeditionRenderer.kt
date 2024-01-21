package com.ravingarinc.expeditions.play.render

import com.ravingarinc.expeditions.locale.type.Expedition
import org.bukkit.entity.Player
import org.bukkit.map.MapCanvas
import org.bukkit.map.MapCursor
import org.bukkit.map.MapRenderer
import org.bukkit.map.MapView
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
        if(expedition.isMapRendered()) {
            if(!players.contains(player.uniqueId)) {
                for(x in 0 until 128) {
                    for(z in 0 until 128) {
                        canvas.setPixelColor(x, z, expedition.getColourCache()[z * 128 + x])
                    }
                }
                players.add(player.uniqueId)
            }
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

}