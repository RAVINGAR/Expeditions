package com.ravingarinc.expeditions.api

import com.ravingarinc.api.module.Module
import com.ravingarinc.api.module.RavinPlugin
import com.ravingarinc.api.module.severe
import com.ravingarinc.api.module.warn
import com.ravingarinc.expeditions.api.Extensions.timeTypes
import kotlinx.coroutines.future.await
import org.bukkit.*
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.map.MapCursor
import org.bukkit.util.BlockVector
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.concurrent.ThreadLocalRandom
import java.util.concurrent.TimeUnit


fun <T : Module> RavinPlugin.withModule(module: Class<T>, function: T.() -> Unit) {
    val m = this.getModule(module)
    if (m.isLoaded) {
        function.invoke(m)
    } else {
        warn("Could not execute function with module ${module.name} as this module has not been loaded!")
    }
}

fun Double.roll(): Boolean {
    return this > 0.0 && (this >= 1.0 || ThreadLocalRandom.current().nextDouble() < this)
}

fun ConfigurationSection.getPercentage(path: String): Double {
    val string = getString(path)
    if (string == null) {
        warn("Could not find option at $path in ${this.name}!")
        return 0.0
    }
    return parsePercentage(string)
}

fun parsePercentage(string: String): Double {
    if(string == "null") {
        return 0.0
    }
    var double = string.replace("%", "").toDoubleOrNull()
    if (double == null) {
        warn("Could not parse $string as a percentage! Format must be 0.4 or 40%!")
        return 0.0
    }
    if (double > 1.0) {
        double /= 100.0
    }
    return double
}

fun ConfigurationSection.getRange(path: String): IntRange {
    if (!this.contains(path)) {
        warn("Could not find option at $path in ${this.name}!")
        return IntRange(1, 1)
    }
    return parseRange(getString(path)!!.replace(" ", ""))
}

fun parseRange(string: String): IntRange {
    val split: List<String> = string.split("-", "to", limit = 2)
    if (split.size == 2) {
        val min = split[0].toIntOrNull()
        val max = split[1].toIntOrNull()
        if (min == null) {
            warn("Could not parse minimum value of '${split[0]}' as a valid number!")
            return IntRange(0, 0)
        }
        if (max == null) {
            warn("Could not parse maximum value of '${split[1]}' as a valid number!")
            return IntRange(0, 0)
        }
        return IntRange(min, max)
    } else {
        string.toIntOrNull()?.let {
            return IntRange(it,it)
        }
    }
    warn("Could not parse $string as a valid range! Please use the format '3-4', '3to4' or a single number such as 3!")
    return IntRange(0, 0)
}



fun parseCursor(cursorName: String?, default: MapCursor.Type) : MapCursor.Type {
    if(cursorName == null) return default
    for(c in MapCursor.Type.values()) {
        if(c.name.equals(cursorName, true)) {
            return c
        }
    }
    warn("Could not find cursor type called '$cursorName'")
    return default
}

fun ConfigurationSection.getWorld(path: String) : World? {
    val string = getString(path)
    if(string == null) {
        warn("Could not find option at path '$path' in section '${this.name}'")
        return null
    }
    val world = Bukkit.getWorld(string)
    if(world == null) {
        warn("Could not find world called '$string' in section '${this.name}'")
    }
    return world;
}

fun ConfigurationSection.getIntPair(path: String) : Pair<Int, Int>? {
    val string = getString(path)
    if(string == null) {
        warn("Could not find option at path '$path' in section '${this.name}'")
        return null
    }
    val split = string.replace(" ", "").split(",",";", limit = 2)
    val first = split[0].toIntOrNull()
    val second = split[1].toIntOrNull()
    if(first == null || second == null) {
        return null
    }
    return Pair(first, second)
}

fun ConfigurationSection.getBlockVector(path: String) : BlockVector? {
    val string = getString(path)
    if(string == null) {
        warn("Could not find option at path '$path' in section '${this.name}'")
        return null
    }
    return parseBlockVector(string)
}

fun parseBlockVector(string: String) : BlockVector? {
    if(string == "null") return null
    val split = string.replace(" ".toRegex(), "").split(",", ";", limit = 3)
    if(split.size < 3) {
        warn("Could not parse location '$string' as three coordinates are required!")
        return null
    }
    val x = split[0].toDoubleOrNull()
    val y = split[1].toDoubleOrNull()
    val z = split[2].toDoubleOrNull()
    if(x == null || y == null || z == null) {
        warn("Could not parse string '$string' as a valid location!")
        return null
    }
    return BlockVector(x, y, z)
}

/**
 * Parses the duration in either milliseconds, ticks, seconds, minutes or hours.
 * Returns a value in ticks
 */
fun ConfigurationSection.getDuration(path: String) : Long? {
    val string = getString(path)
    if(string == null) {
        warn("Could not find option at path '$path' in section '${this.name}'")
        return null
    }
    val formatted = string.replace(" ", "")

    for(type in timeTypes) {
        return type.format(this@getDuration.name, formatted) ?: continue
    }
    warn("Could not discern duration from string '$string' in section '${this.name}'. Please specify 'ms', 't', 's', 'm', or 'h' after the number!")
    return null
}

fun Long.formatMilliseconds() : String {
    return if(this < 1000) {
        "$this milliseconds"
    } else if(this < 60000) {
        "${(this / 1000).toInt()} seconds"
    } else if(this < 3600000) {
        if(this % 60000 > 0) "${(this / 60000).toInt()} minutes, ${(this % 60000 / 1000).toInt()} seconds" else "${(this / 60000).toInt()} minutes"
    } else {
        if(this % 3600000 > 0) "${(this / 3600000).toInt()} hours, ${(this % 3600000 / 60000)} minutes" else "${(this / 3600000).toInt()} hours"
    }
}

private object Extensions {
    val timeTypes: Array<TimeFormat> = arrayOf(
        TimeFormat(arrayOf("t", "ticks", "tick")) { it },
        TimeFormat(arrayOf("ms", "milliseconds", "millisecond")) { it / 50L },
        TimeFormat(arrayOf("s", "secs", "sec", "seconds", "second")) { it * 20L },
        TimeFormat(arrayOf("m", "mins", "min", "minutes", "minute")) { it * 1200L },
        TimeFormat(arrayOf("h", "hours", "hour")) { it * 72000L }
    )
}

private class TimeFormat(val suffixes: Array<String>, val formatter: (Long) -> Long) {
    fun format(section: String, string: String) : Long? {
        for(suffix in suffixes) {
            if(string.endsWith(suffix)) {
                val long = string.replace(suffix, "").toLongOrNull()
                if(long == null) {
                    warn("Could not format duration in section '${section}' as the value '${string}' does contain a number")
                    break;
                }
                return formatter.invoke(long)
            }
        }
        return null
    }
}



fun RavinPlugin.copyResource(parent: File, sourcePath: String, destPath: String) {
    this.getResource(sourcePath)?.let {
        it.use { stream ->
            Files.copy(
                stream,
                File(parent, destPath).toPath(),
                StandardCopyOption.REPLACE_EXISTING
            )
        }
    }
}

fun ConfigurationSection.getMaterialList(path: String): Set<Material> {
    val list = getStringList(path)
    return buildSet {
        for (m in list) {
            parseMaterial(m)?.let { this.add(it) }
        }
    }
}

fun ConfigurationSection.getMaterial(path: String): Material? {
    val material = this.getString(path)
    if (material == null) {
        warn("Could not find option at path '$path' in section '${this.name}'")
        return null
    }
    return parseMaterial(material)
}

fun ConfigurationSection.getSound(path: String): Sound? {
    getString(path)?.let {
        try {
            return Sound.valueOf(it.uppercase())
        } catch (exception: IllegalArgumentException) {
            warn("Could not find sound with ID of '$it'!")
        }
    }
    warn("Could not find option at path '$path' in section '${this.name}'")
    return null
}

fun parseMaterial(string: String): Material? {
    val material = Material.matchMaterial(string)
    if (material == null) {
        warn("Could not find valid material called '$string'. Please fix your config!")
    }
    return material
}

fun World.blockWithChunk(plugin: RavinPlugin, location: Location, withChunk: (Chunk) -> Unit) {
    return blockWithChunk(plugin, location.blockX shr 4, location.blockZ shr 4, withChunk)
}

fun World.blockWithChunk(plugin: RavinPlugin, chunkX: Int, chunkZ: Int, withChunk: (Chunk) -> Unit) {
    if(this.isChunkLoaded(chunkX, chunkZ)) {
        withChunk.invoke(this.getChunkAt(chunkX, chunkZ))
    } else {
        try {
            this.addPluginChunkTicket(chunkX, chunkZ, plugin)
            if(isChunkLoaded(chunkX, chunkZ)) {
                withChunk.invoke(getChunkAt(chunkX, chunkZ))
            } else {
                getChunkAtAsyncUrgently(chunkX, chunkZ).thenAccept(withChunk).exceptionally {
                    severe("Encountered exception whilst attempting to load chunk", it)
                    return@exceptionally null
                }
            }
        } finally {
            this.removePluginChunkTicket(chunkX, chunkZ, plugin)
        }
    }
}

suspend fun World.suspendWithChunk(chunkX: Int, chunkZ: Int, withChunk: (Chunk) -> Unit) {
    getChunkAtAsyncUrgently(chunkX, chunkZ).thenAccept {
        withChunk.invoke(it)
    }.orTimeout(10, TimeUnit.SECONDS).exceptionally {
        severe("Encountered unexpected exception whilst waiting for chunk to load!", it)
        return@exceptionally null
    }.await()
}

