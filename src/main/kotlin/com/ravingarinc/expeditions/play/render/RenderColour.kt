package com.ravingarinc.expeditions.play.render

import org.bukkit.Material
import java.awt.Color
import java.util.*
import kotlin.math.floor

enum class RenderColour(val id: Color, val predicate: (Material) -> Boolean) {
    NONE(Color(255, 255, 255, 0), { it.isAir || it == Material.GLASS || it == Material.GLASS_PANE || it == Material.BARRIER }),
    STONE(Color(7368816), { it == Material.GRAVEL || it == Material.TUFF || it == Material.BASALT || it == Material.SMOOTH_BASALT || it == Material.POLISHED_BASALT || it.name.startsWith("STONE") || it.name.startsWith("COBBLESTONE") || it.name.contains("ANDESITE") }),
    GRASS(Color(8368696), { it == Material.GRASS_BLOCK || it == Material.SLIME_BLOCK }),
    SAND(Color(16247203), { it.name.startsWith("SAND") || it.name.startsWith("SMOOTH_SANDSTONE") || it.name.startsWith("BIRCH") }),
    FIRE(Color(16711680), { it == Material.LAVA || it == Material.FIRE || it == Material.TNT || it == Material.REDSTONE_BLOCK }),
    ICE(Color(10526975), { it.name.contains("ICE") }),
    PLANT(Color(31744), { it == Material.GRASS || it == Material.DANDELION || it == Material.ROSE_BUSH || it == Material.POPPY || it == Material.BLUE_ORCHID || it == Material.ALLIUM
            || it == Material.AZURE_BLUET || it == Material.OXEYE_DAISY || it == Material.LILY_PAD || it == Material.SUGAR_CANE || it.name.endsWith("SAPLING")
            || it.name.endsWith("LEAVES") || it.name.endsWith("TULIP") || it == Material.CORNFLOWER || it == Material.BAMBOO || it.name.contains("AZALEA")
            || it == Material.FERN || it == Material.TALL_GRASS || it == Material.LARGE_FERN || it == Material.CACTUS
    }),
    TERRACOTTA_WHITE(Color(13742497), { it == Material.WHITE_TERRACOTTA }),
    TERRACOTTA_CYAN(Color(5725276), { it == Material.CYAN_TERRACOTTA }),
    SNOW(Color(255, 255, 255), { it == Material.SNOW || it == Material.SNOW_BLOCK || it == Material.POWDER_SNOW || it.name.startsWith("WHITE")}),
    CLAY(Color(10791096), { it == Material.CLAY || it.name.startsWith("IRON") || it.name.startsWith("SMOOTH_STONE") }),
    DIRT(Color(9923917), { it == Material.DIRT || it == Material.COARSE_DIRT || it == Material.FARMLAND
            || it == Material.DIRT_PATH || it == Material.BROWN_MUSHROOM_BLOCK
            || it.name.startsWith("GRANITE") || it.name.startsWith("JUNGLE")}),
    WATER(Color(4210943), { it == Material.WATER }),
    WOOD(Color(9402184), { it == Material.CHEST || it == Material.TRAPPED_CHEST || it == Material.DEAD_BUSH || it == Material.CRAFTING_TABLE || it == Material.COMPOSTER || it == Material.NOTE_BLOCK || it.name.contains("OAK") }),
    QUARTZ(Color(16776437), { it.name.startsWith("QUARTZ") || it.name.startsWith("DIORITE") || it.name.startsWith("BIRCH")} ),
    COLOR_ORANGE(Color(14188339), { it == Material.PUMPKIN || it == Material.TERRACOTTA || it.name.startsWith("RED_SAND") || it.name.startsWith("ACACIA") || it.name.startsWith("ORANGE") }),
    COLOR_MAGENTA(Color(11685080), { it.name.startsWith("MAGENTA") || it.name.startsWith("PURPUR")}),
    COLOR_LIGHT_BLUE(Color(6724056), { it.name.startsWith("LIGHT_BLUE") || it == Material.SOUL_FIRE }),
    COLOR_YELLOW(Color(15066419), { it.name.startsWith("YELLOW") || it == Material.SPONGE || it == Material.WET_SPONGE || it == Material.HAY_BLOCK || it == Material.BEEHIVE }),
    COLOR_LIGHT_GREEN(Color(8375321), { it.name.startsWith("LIME") || it == Material.MELON }),
    COLOR_PINK(Color(15892389), { it.name.startsWith("PINK") || it.name.startsWith("BRAIN_CORAL") || it.name.startsWith("CHERRY")}),
    COLOR_GRAY(Color(5000268), { it.name.startsWith("GRAY") || it.name.startsWith("DEAD_")}),
    COLOR_LIGHT_GRAY(Color(10066329), { it.name.startsWith("LIGHT_GRAY")}),
    COLOR_CYAN(Color(5013401), { it.name.startsWith("CYAN") || it.name.startsWith("PRISMARINE")}),
    COLOR_PURPLE(Color(8339378), { it.name.startsWith("PURPLE") || it == Material.SHULKER_BOX || it.name.contains("AMETHYST")}),
    COLOR_BLUE(Color(3361970), { it == Material.LAPIS_BLOCK || it.name.startsWith("BLUE") || it.name.startsWith("TUBE_CORAL")}),
    COLOR_BROWN(Color(6704179), { it == Material.SOUL_SAND || it == Material.SOUL_SOIL || it.name.startsWith("MUD") || it.name.startsWith("DARK_OAK") || it.name.startsWith("BROWN") }),
    COLOR_GREEN(Color(6717235), { it == Material.MOSS_BLOCK || it == Material.MOSS_CARPET || it == Material.DRIED_KELP_BLOCK || it.name.startsWith("GREEN") }), // add emerald
    COLOR_RED(Color(10040115), { it == Material.RED_MUSHROOM || it == Material.RED_MUSHROOM_BLOCK || it.name.startsWith("MANGROVE") || it.name.startsWith("RED") || it.name.startsWith("FIRE_CORAL")}),
    COLOR_BLACK(Color(1644825), { it == Material.OBSIDIAN || it == Material.COAL_BLOCK || it == Material.CRYING_OBSIDIAN || it.name.startsWith("BLACK") || it.name.contains("BASALT")}),
    GOLD(Color(16445005), { it == Material.GOLD_BLOCK || it == Material.BELL || it == Material.RAW_GOLD_BLOCK }),
    DIAMOND(Color(6085589), { it == Material.DIAMOND_BLOCK || it == Material.BEACON || it.name.startsWith("DARK_PRISMARINE")}),
    PODZOL(Color(8476209), { it == Material.PODZOL || it == Material.CAMPFIRE || it == Material.SOUL_CAMPFIRE || it.name.startsWith("SPRUCE") }),
    NETHER(Color(7340544), { it == Material.MAGMA_BLOCK || it.name.startsWith("NETHER")}),
    WARPED(Color(1474182), { it.name.startsWith("WARPED")}),
    CRIMSON(Color(6035741), { it.name.startsWith("CRIMSON")}),
    MYCELIUM(Color(9726851), { it == Material.MYCELIUM }),
    DEEPSLATE(Color(6579300), { it.name.startsWith("DEEPSLATE") || it.name.startsWith("BLACKSTONE")});

    fun withBrightness(brightness: Brightness) : Color {
        val red = floor(id.red * brightness.brightness).toInt()
        val green = floor(id.green * brightness.brightness).toInt()
        val blue = floor(id.blue * brightness.brightness).toInt()
        return Color(red, green, blue)
    }

    enum class Brightness(val brightness: Double) {
        LOW(0.71),
        NORMAL(0.86),
        HIGH(1.0),
        LOWEST(0.53)
    }

    companion object {
        private val cache: MutableMap<Material, RenderColour> = EnumMap(org.bukkit.Material::class.java)

        fun match(material: Material) : RenderColour {
            return cache.computeIfAbsent(material) {
                for(colour in RenderColour.values()) {
                    if(colour.predicate.invoke(material)) {
                        return@computeIfAbsent colour
                    }
                }
                return@computeIfAbsent RenderColour.NONE
            }
        }
    }
}