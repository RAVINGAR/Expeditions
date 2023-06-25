package com.ravingarinc.expeditions.api

import com.comphenix.protocol.PacketType
import com.comphenix.protocol.ProtocolLibrary
import com.comphenix.protocol.ProtocolManager
import com.comphenix.protocol.events.PacketContainer
import com.comphenix.protocol.wrappers.WrappedDataWatcher
import com.ravingarinc.api.module.RavinPlugin
import org.bukkit.Bukkit
import org.bukkit.entity.Entity
import org.bukkit.entity.EntityType
import java.util.*
import kotlin.experimental.ExperimentalTypeInference


/**
 * Versions representing usages of different protocols
 * See https://wiki.vg/Protocol_version_numbers
 *
 * // todo fill these out with appropriate values after restructuring the project such that there are different modules
 */
sealed class Version(
    val major: Int,
    val minor: Int,
    val patch: IntRange,
    val protocol: Int,
    val packFormat: Int,
    val names: Array<String>
) {
    sealed class V1_18_2(
            major: Int = 1,
            minor: Int = 18,
            patch: IntRange = 2..2,
            protocol: Int = 758,
            packFormat: Int = 8,
            names: Array<String> = arrayOf("1.18.2")
    ) :
            Version(major, minor, patch, protocol, packFormat, names) {
        override val indexedEntities: Map<EntityType, Int> = buildMap {
            this[EntityType.AREA_EFFECT_CLOUD] = 0
            this[EntityType.ARMOR_STAND] = 1
            this[EntityType.ARROW] = 2
            this[EntityType.AXOLOTL] = 3
            this[EntityType.BAT] = 4
            this[EntityType.BEE] = 5
            this[EntityType.BLAZE] = 6
            this[EntityType.BOAT] = 7
            this[EntityType.CAT] = 8
            this[EntityType.CAVE_SPIDER] = 9
            this[EntityType.CHICKEN] = 10
            this[EntityType.COD] = 11
            this[EntityType.COW] = 12
            this[EntityType.CREEPER] = 13
            this[EntityType.DOLPHIN] = 14
            this[EntityType.DONKEY] = 15
            this[EntityType.DRAGON_FIREBALL] = 16
            this[EntityType.DROWNED] = 17
            this[EntityType.ELDER_GUARDIAN] = 18
            this[EntityType.ENDER_CRYSTAL] = 19
            this[EntityType.ENDER_DRAGON] = 20
            this[EntityType.ENDERMAN] = 21
            this[EntityType.ENDERMITE] = 22
            this[EntityType.EVOKER] = 23
            this[EntityType.EVOKER_FANGS] = 24
            this[EntityType.EXPERIENCE_ORB] = 25
            this[EntityType.ENDER_SIGNAL] = 26
            this[EntityType.FALLING_BLOCK] = 27
            this[EntityType.FIREWORK] = 28
            this[EntityType.FOX] = 29
            this[EntityType.GHAST] = 30
            this[EntityType.GIANT] = 31
            this[EntityType.GLOW_ITEM_FRAME] = 32
            this[EntityType.GLOW_SQUID] = 33
            this[EntityType.GOAT] = 34
            this[EntityType.GUARDIAN] = 35
            this[EntityType.HOGLIN] = 36
            this[EntityType.HORSE] = 37
            this[EntityType.HUSK] = 38
            this[EntityType.ILLUSIONER] = 39
            this[EntityType.IRON_GOLEM] = 40
            this[EntityType.DROPPED_ITEM] = 41
            this[EntityType.ITEM_FRAME] = 42
            this[EntityType.FIREBALL] = 43
            this[EntityType.LEASH_HITCH] = 44
            this[EntityType.LIGHTNING] = 45
            this[EntityType.LLAMA] = 46
            this[EntityType.LLAMA_SPIT] = 47
            this[EntityType.MAGMA_CUBE] = 48
            this[EntityType.MARKER] = 49
            this[EntityType.MINECART] = 50
            this[EntityType.MINECART_CHEST] = 51
            this[EntityType.MINECART_COMMAND] = 52
            this[EntityType.MINECART_FURNACE] = 53
            this[EntityType.MINECART_HOPPER] = 54
            this[EntityType.MINECART_MOB_SPAWNER] = 55
            this[EntityType.MINECART_TNT] = 56
            this[EntityType.MULE] = 57
            this[EntityType.MUSHROOM_COW] = 58
            this[EntityType.OCELOT] = 59
            this[EntityType.PAINTING] = 60
            this[EntityType.PANDA] = 61
            this[EntityType.PARROT] = 62
            this[EntityType.PHANTOM] = 63
            this[EntityType.PIG] = 64
            this[EntityType.PIGLIN] = 65
            this[EntityType.PIGLIN_BRUTE] = 66
            this[EntityType.PILLAGER] = 67
            this[EntityType.POLAR_BEAR] = 68
            this[EntityType.PRIMED_TNT] = 69
            this[EntityType.PUFFERFISH] = 70
            this[EntityType.RABBIT] = 71
            this[EntityType.RAVAGER] = 72
            this[EntityType.SALMON] = 73
            this[EntityType.SHEEP] = 74
            this[EntityType.SHULKER] = 75
            this[EntityType.SHULKER_BULLET] = 76
            this[EntityType.SILVERFISH] = 77
            this[EntityType.SKELETON] = 78
            this[EntityType.SKELETON_HORSE] = 79
            this[EntityType.SLIME] = 80
            this[EntityType.SMALL_FIREBALL] = 81
            this[EntityType.SNOWMAN] = 82
            this[EntityType.SNOWBALL] = 83
            this[EntityType.SPECTRAL_ARROW] = 84
            this[EntityType.SPIDER] = 85
            this[EntityType.SQUID] = 86
            this[EntityType.STRAY] = 87
            this[EntityType.STRIDER] = 88
            this[EntityType.EGG] = 89
            this[EntityType.ENDER_PEARL] = 90
            this[EntityType.THROWN_EXP_BOTTLE] = 91
            this[EntityType.SPLASH_POTION] = 92
            this[EntityType.TRIDENT] = 93
            this[EntityType.TRADER_LLAMA] = 94
            this[EntityType.TROPICAL_FISH] = 95
            this[EntityType.TURTLE] = 96
            this[EntityType.VEX] = 97
            this[EntityType.VILLAGER] = 98
            this[EntityType.VINDICATOR] = 99
            this[EntityType.WANDERING_TRADER] = 100
            this[EntityType.WITCH] = 101
            this[EntityType.WITHER] = 102
            this[EntityType.WITHER_SKELETON] = 103
            this[EntityType.WITHER_SKULL] = 104
            this[EntityType.WOLF] = 105
            this[EntityType.ZOGLIN] = 106
            this[EntityType.ZOMBIE] = 107
            this[EntityType.ZOMBIE_HORSE] = 108
            this[EntityType.ZOMBIE_VILLAGER] = 109
            this[EntityType.ZOMBIFIED_PIGLIN] = 110
            this[EntityType.PLAYER] = 111
            this[EntityType.FISHING_HOOK] = 112
        }

        override fun updateMetadata(
            entity: Entity,
            data: List<Triple<Int, WrappedDataWatcher.Serializer, Any>>
        ): PacketContainer {
            val packet = Version.protocol.createPacket(PacketType.Play.Server.ENTITY_METADATA)
            packet.integers.write(0, entity.entityId)
            val watcher = WrappedDataWatcher()
            watcher.entity = entity
            data.forEach {
                watcher.setObject(it.first, it.second, it.third)
            }
            packet.watchableCollectionModifier.write(0, watcher.watchableObjects)
            return packet
        }

        companion object : V1_18_2()
    }

    protected abstract val indexedEntities: Map<EntityType, Int>

    @OptIn(ExperimentalTypeInference::class)
    fun updateMetadata(entity: Entity, @BuilderInference builder: MutableList<Triple<Int, WrappedDataWatcher.Serializer, Any>>.() -> Unit) : PacketContainer {
        val list = ArrayList<Triple<Int,WrappedDataWatcher.Serializer, Any>>()
        builder.invoke(list)
        return updateMetadata(entity, list)
    }

    abstract fun updateMetadata(entity: Entity, data: List<Triple<Int, WrappedDataWatcher.Serializer, Any>>) : PacketContainer

    fun getEntityTypeId(type: EntityType): Int {
        return indexedEntities[type]
            ?: throw IllegalStateException("Cannot get entity id for entity type '${type.name}' as version ${getVersionName()} does not contain this entity!")
    }

    fun getVersionName(): String {
        return names[names.size - 1]
    }

    override fun equals(other: Any?): Boolean {
        if (other is Version) {
            return other.major == this.major && other.minor == this.minor && other.patch == this.patch && other.protocol == this.protocol && other.packFormat == this.packFormat
        }
        return false
    }

    override fun hashCode(): Int {
        return Objects.hash(major, minor, patch, protocol, packFormat)
    }

    companion object {
        val protocol: ProtocolManager = ProtocolLibrary.getProtocolManager()
        val byteSerializer = WrappedDataWatcher.Registry.get(java.lang.Byte::class.java)
        val boolSerializer = WrappedDataWatcher.Registry.get(java.lang.Boolean::class.java)
        val integerSerializer = WrappedDataWatcher.Registry.get(java.lang.Integer::class.java)
        val floatSerializer = WrappedDataWatcher.Registry.get(java.lang.Float::class.java)
        val itemSerializer = WrappedDataWatcher.Registry.getItemStackSerializer(false)
    }
}

object Versions {
    val serverVersion by lazy {
        getFromServer()
    }

    val values: Array<Version> = arrayOf(
        Version.V1_18_2
    )

    private val protocolMap: Map<Int, Version> = buildMap {
        for (version in values) {
            this[version.protocol] = version
        }
    }

    fun getFromProtocol(protocol: Int): Version {
        return protocolMap[protocol] ?: throw IllegalArgumentException("Unsupported protocol version $protocol")
    }

    fun getFromServer(): Version {
        val version = Bukkit.getServer().bukkitVersion // expecting Format of 1.18.2-R0.1-SNAPSHOT
        val parts = version.substring(0, version.indexOf('-')).split(".")

        val major = parts[0].toIntOrNull()
            ?: throw IllegalStateException("Could not parse version major from version $version!")
        val minor = parts[1].toIntOrNull()
            ?: throw IllegalStateException("Could not parse version minor from version $version!")
        val patch = (if (parts.size > 2) parts[2].toIntOrNull() else 0)
            ?: throw IllegalStateException("Could not parse version patch from version $version!")

        for (v in values) {
            if (v.major == major && v.minor == minor && v.patch.contains(patch)) {
                return v
            }
        }
        throw IllegalStateException("Could not get server version as this plugin does not support the version $version!")
    }
}

fun RavinPlugin.getVersion() : Version {
    return Versions.serverVersion
}

fun MutableList<Triple<Int, WrappedDataWatcher.Serializer, Any>>.build(index: Int, serializer: WrappedDataWatcher.Serializer, obj: Any) {
    this.add(Triple(index, serializer, obj))
}