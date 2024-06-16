package com.ravingarinc.expeditions.locale.type

import com.github.shynixn.mccoroutine.bukkit.launch
import com.ravingarinc.api.module.RavinPlugin
import com.ravingarinc.expeditions.api.blockWithChunk
import com.ravingarinc.expeditions.play.instance.AreaInstance
import com.ravingarinc.expeditions.play.instance.ExpeditionInstance
import com.ravingarinc.expeditions.play.instance.RemoveReason
import com.ravingarinc.expeditions.play.item.LootTable
import com.ravingarinc.expeditions.play.mob.MobType
import kotlinx.coroutines.delay
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.ComponentLike
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.*
import org.bukkit.map.MapCursor
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import org.bukkit.util.BlockVector

class ExtractionZone(val chance: Double,
                     val beaconLoc: BlockVector?,
                     val particleHeight: Double,
                     displayName: String,
                     startLoc: BlockVector,
                     endLoc: BlockVector,
                     lootLimit: Double,
                     lootChance: Double,
                     lootTypes: List<Pair<LootTable, Double>>,
                     lootLocations: List<BlockVector>,
                     mobSpawnChance: Double,
                     maxMobs: Int,
                     mobs: List<Triple<MobType, Double, IntRange>>,
                     mobLocations: List<BlockVector>,
                     bossType: MobType,
                     bossLevel: Int,
                     bossSpawnChance: Double,
                     bossSpawnLocation: BlockVector?,
                     bossCooldown: Long,
                     npcIdentifier: String?,
                     npcSpawnLoc: BlockVector?,
                     npcOnSpawn: List<String>,
                     npcOnExtract: List<String>,
                     npcFollowText: String,
                     npcRefollowText: String,
                     npcUnfollowText: String,
                     cursorType: MapCursor.Type,
                     enterMessage: ComponentLike
) : PointOfInterest(displayName, startLoc, endLoc, lootLimit, lootChance, lootTypes, lootLocations, mobSpawnChance, maxMobs, mobs, mobLocations, bossType, bossLevel, bossSpawnChance, bossSpawnLocation, bossCooldown, npcIdentifier, npcSpawnLoc, npcOnSpawn, npcOnExtract, npcFollowText, npcRefollowText, npcUnfollowText, cursorType, enterMessage, false) {

    override val displayType: String = "Extraction Zone"

    private val particleData = Particle.DustTransition(Color.fromRGB(215,49,12), Color.fromRGB(255,179,39), 3F)

    override fun initialise(plugin: RavinPlugin, world: World) {
        super.initialise(plugin, world)
        beaconLoc?.let {
            world.blockWithChunk(plugin, it.blockX shr 4, it.blockZ shr 4) { chunk ->
                world.getBlockAt(it.blockX, it.blockY, it.blockZ).type = Material.BEACON
            }
        }
    }

    override fun dispose(plugin: RavinPlugin, world: World) {
        super.dispose(plugin, world)
        beaconLoc?.let {
            world.blockWithChunk(plugin, it.blockX shr 4, it.blockZ shr 4) { chunk ->
                world.getBlockAt(it.blockX, it.blockY, it.blockZ).type = Material.STONE
            }
        }
    }

    override fun tick(expedition: ExpeditionInstance, area: AreaInstance) {
        super.tick(expedition, area)
        tickEffects(expedition.world)
        tickExtractions(expedition, area)
    }

    private fun tickEffects(world: World) {
        val startX = startLoc.x.toInt()
        val endX = endLoc.x.toInt()
        val startZ = startLoc.z.toInt()
        val endZ = endLoc.z.toInt()
        for(x in xRange) {
            for(z in zRange) {
                if(x == startX || x == endX || z == startZ || z == endZ) {
                    world.spawnParticle(Particle.REDSTONE, x.toDouble(), particleHeight, z.toDouble(), 2, 0.0, 0.05, 0.0, particleData)
                }
            }
        }
    }

    private fun tickExtractions(expedition: ExpeditionInstance, area: AreaInstance) {
        val time = System.currentTimeMillis()
        val players = ArrayList(area.inArea.keys)
        for(player in players) {
            val startTime = area.inArea[player] ?: continue
            val diff = time - startTime
            val progress = diff / (expedition.expedition.extractionTime * 50.0)
            if(progress >= 1.0) {
                expedition.plugin.launch {
                    player.sendActionBar(Component.text("-- Extraction Complete --").color(NamedTextColor.GOLD))
                    player.playSound(player, Sound.ITEM_TRIDENT_RIPTIDE_2, 0.8F, 0.8F)
                    player.addPotionEffect(PotionEffect(PotionEffectType.BLINDNESS, 20, 1, true))
                    delay(60)
                    expedition.removePlayer(player, RemoveReason.EXTRACTION)
                    player.playSound(player, Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.8F, 1.0F)
                }
            } else {
                player.playSound(player, Sound.BLOCK_NOTE_BLOCK_PLING, 0.8F, 0.8F)
                val builder = Component.text()
                    .content("Extracting . . .")
                    .color(NamedTextColor.GOLD)
                    .append(Component.text(" | ").color(NamedTextColor.GRAY))
                    .append(Component.text("[").color(NamedTextColor.GRAY));
                for(i in 1..16) {
                    builder.append(
                        Component
                        .text("|")
                        .color(if(progress > (i / 16.0)) NamedTextColor.YELLOW else NamedTextColor.GRAY))
                }
                builder.append(Component.text("]").color(NamedTextColor.GRAY))
                player.sendActionBar(builder.build())
            }
        }
    }

    override fun isHidden(): Boolean {
        return false
    }
}