package com.ravingarinc.expeditions.play.instance

import com.ravingarinc.expeditions.locale.type.Expedition
import org.bukkit.Material
import org.bukkit.Material.*
import org.bukkit.entity.Player
import org.bukkit.map.MapCanvas
import org.bukkit.map.MapCursor
import org.bukkit.map.MapRenderer
import org.bukkit.map.MapView
import java.awt.Color
import java.util.*
import java.util.concurrent.ThreadLocalRandom
import kotlin.math.floor

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
                    canvas.setPixelColor(x, z, expedition.colourCache[z * 128 + x])
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

    enum class MapColour(val id: Color, val predicate: (Material) -> Boolean) {
        NONE(Color(255, 255, 255, 0), { it.isAir || it == Material.GLASS || it == Material.GLASS_PANE || it == Material.BARRIER }),
        GRASS(Color(8368696), { it == GRASS_BLOCK || it == SLIME_BLOCK }),
        SAND(Color(16247203), { it.name.startsWith("SAND") || it.name.startsWith("BIRCH") }),
        FIRE(Color(16711680), { it == LAVA || it == Material.FIRE || it == TNT || it == Material.REDSTONE_BLOCK }),
        ICE(Color(10526975), { it.name.endsWith("ICE") || it.name.startsWith("SNOW") }),
        PLANT(Color(31744), { it == Material.GRASS || it == Material.DANDELION || it == Material.ROSE_BUSH || it == Material.POPPY || it == Material.BLUE_ORCHID || it == org.bukkit.Material.ALLIUM
                || it == Material.AZURE_BLUET || it == Material.OXEYE_DAISY || it == Material.LILY_PAD || it == Material.SUGAR_CANE || it.name.endsWith("SAPLING")
                || it.name.endsWith("LEAVES") || it.name.endsWith("TULIP") || it == Material.CORNFLOWER || it == Material.BAMBOO || it.name.contains("AZALEA")
                || it == Material.FERN || it == TALL_GRASS || it == Material.LARGE_FERN}),
        TERRACOTTA_WHITE(Color(13742497), { it == Material.WHITE_TERRACOTTA }),
        TERRACOTTA_CYAN(Color(5725276), { it == Material.CYAN_TERRACOTTA }),
        SNOW(Color(255, 255, 255), { it == Material.SNOW || it == Material.SNOW_BLOCK || it == Material.POWDER_SNOW || it.name.startsWith("WHITE")}),
        CLAY(Color(10791096), { it == Material.CLAY || it.name.startsWith("IRON")}),
        DIRT(Color(9923917), { it == Material.DIRT || it == Material.COARSE_DIRT || it == Material.FARMLAND
                || it == Material.DIRT_PATH || it == BROWN_MUSHROOM_BLOCK
                || it.name.startsWith("GRANITE") || it.name.startsWith("JUNGLE")}),
        WATER(Color(4210943), { it == Material.WATER }),
        WOOD(Color(9402184), { it == Material.CHEST || it == TRAPPED_CHEST || it == DEAD_BUSH || it == org.bukkit.Material.CRAFTING_TABLE || it == Material.COMPOSTER || it == org.bukkit.Material.NOTE_BLOCK|| it.name.contains("OAK") }),
        QUARTZ(Color(16776437), { it.name.startsWith("QUARTZ") || it.name.startsWith("DIORITE") || it.name.startsWith("BIRCH")} ),
        COLOR_ORANGE(Color(14188339), { it == Material.PUMPKIN || it == Material.TERRACOTTA || it.name.startsWith("RED_SAND") || it.name.startsWith("ACACIA") || it.name.startsWith("ORANGE") }),
        COLOR_MAGENTA(Color(11685080), { it.name.startsWith("MAGENTA") || it.name.startsWith("PURPUR")}),
        COLOR_LIGHT_BLUE(Color(6724056), { it.name.startsWith("LIGHT_BLUE") || it == Material.SOUL_FIRE}),
        COLOR_YELLOW(Color(15066419), { it.name.startsWith("YELLOW") || it == Material.SPONGE || it == Material.WET_SPONGE || it == Material.HAY_BLOCK || it == Material.BEEHIVE}),
        COLOR_LIGHT_GREEN(Color(8375321), { it.name.startsWith("LIME") || it == org.bukkit.Material.MELON}),
        COLOR_PINK(Color(15892389), { it.name.startsWith("PINK") || it.name.startsWith("BRAIN_CORAL") || it.name.startsWith("CHERRY")}),
        COLOR_GRAY(Color(5000268), { it.name.startsWith("GRAY") || it.name.startsWith("DEAD_")}),
        COLOR_LIGHT_GRAY(Color(10066329), { it.name.startsWith("LIGHT_GRAY")}),
        COLOR_CYAN(Color(5013401), { it.name.startsWith("CYAN") || it.name.startsWith("PRISMARINE")}),
        COLOR_PURPLE(Color(8339378), { it.name.startsWith("PURPLE") || it == Material.SHULKER_BOX || it.name.contains("AMETHYST")}),
        COLOR_BLUE(Color(3361970), { it == Material.LAPIS_BLOCK || it.name.startsWith("BLUE") || it.name.startsWith("TUBE_CORAL")}),
        COLOR_BROWN(Color(6704179), { it == Material.SOUL_SAND || it == Material.SOUL_SOIL || it.name.startsWith("MUD") || it.name.startsWith("DARK_OAK")}),
        COLOR_GREEN(Color(6717235), { it == Material.MOSS_BLOCK || it == org.bukkit.Material.MOSS_CARPET || it == Material.DRIED_KELP_BLOCK || it.name.startsWith("GREEN") }), // add emerald
        COLOR_RED(Color(10040115), { it == Material.RED_MUSHROOM || it == Material.RED_MUSHROOM_BLOCK || it.name.startsWith("MANGROVE") || it.name.startsWith("RED") || it.name.startsWith("FIRE_CORAL")}),
        COLOR_BLACK(Color(1644825), { it == Material.OBSIDIAN || it == Material.COAL_BLOCK || it == Material.CRYING_OBSIDIAN || it.name.startsWith("BLACK") || it.name.contains("BASALT")}),
        GOLD(Color(16445005), { it == Material.GOLD_BLOCK || it == Material.BELL || it == Material.RAW_GOLD_BLOCK }),
        DIAMOND(Color(6085589), { it == Material.DIAMOND_BLOCK || it == Material.BEACON || it.name.startsWith("DARK_PRISMARINE")}),
        PODZOL(Color(8476209), { it == Material.PODZOL || it == Material.CAMPFIRE || it == org.bukkit.Material.SOUL_CAMPFIRE || it.name.startsWith("SPRUCE") }),
        NETHER(Color(7340544), { it == Material.MAGMA_BLOCK || it.name.startsWith("NETHER")}),
        WARPED(Color(1474182), { it.name.startsWith("WARPED")}),
        CRIMSON(Color(6035741), { it.name.startsWith("CRIMSON")}),
        DEEPSLATE(Color(6579300), { it.name.startsWith("DEEPSLATE")}),
        STONE(Color(7368816), { true });


        fun randomise() : Color {
            val random = shades[ThreadLocalRandom.current().nextInt(4)]
            val red = floor(id.red * random).toInt()
            val green = floor(id.green * random).toInt()
            val blue = floor(id.blue * random).toInt()
            return Color(red, green, blue)
        }

        fun brighter() : Color {
            val red = floor(id.red * shades[1]).toInt()
            val green = floor(id.green * shades[1]).toInt()
            val blue = floor(id.blue * shades[1]).toInt()
            return Color(red, green, blue)
        }

        fun darker() : Color {
            val red = floor(id.red * shades[3]).toInt()
            val green = floor(id.green * shades[3]).toInt()
            val blue = floor(id.blue * shades[3]).toInt()
            return Color(red, green, blue)
        }

        companion object {
            val shades: Array<Double> = arrayOf(
                0.71,
                0.86,
                1.0,
                0.53
            )
        }
    }
}