package com.ravingarinc.expeditions.command

import com.ravingarinc.api.gui.BaseGui
import com.ravingarinc.api.gui.builder.GuiBuilder
import com.ravingarinc.api.gui.builder.GuiProvider
import com.ravingarinc.api.gui.component.action.RunnableAction
import com.ravingarinc.api.module.RavinPlugin
import com.ravingarinc.expeditions.locale.ExpeditionManager
import com.ravingarinc.expeditions.play.PlayHandler
import org.bukkit.ChatColor
import org.bukkit.Material
import org.bukkit.Sound
import org.bukkit.entity.Player
import java.util.concurrent.ConcurrentHashMap

object ExpeditionGui {
    private val guis: MutableMap<Player, BaseGui> = ConcurrentHashMap()

    fun openGui(plugin: RavinPlugin, player: Player) {
        val gui = guis.computeIfAbsent(player) {
            return@computeIfAbsent build(plugin, it)
        }
        player.openInventory(gui.inventory)
    }

    fun build(plugin: RavinPlugin, player: Player) : BaseGui {
        /**
         * 00 01 02 03 04 05 06 07 08
         * 09 10 11 12 13 14 15 16 17
         * 18 19 20 21 22 23 24 25 26
         */
        val manager = plugin.getModule(ExpeditionManager::class.java)
        val handler = plugin.getModule(PlayHandler::class.java)

        val builder = GuiBuilder(plugin, "Expeditions", BaseGui::class.java, 27)

        builder.primaryBorder = Material.BLUE_STAINED_GLASS_PANE
        builder.secondaryBorder = Material.BLACK_STAINED_GLASS_PANE
        builder.setBackIconIndex(22)

        builder.createMenu("MAIN", null)
            .addStaticIcon("title", "${ChatColor.DARK_AQUA}Expeditions", "${ChatColor.GRAY}Select and join a\n${ChatColor.GRAY}random expedition!", Material.COMPASS, 4)
            .finalise()
            .addPage("expedition_type_page", 10, 11, 12, 13, 14, 15, 16)
            .addNextPageIcon(17).finalise()
            .addPreviousPageIcon(9).finalise()
            .addPageFiller("type_filler") { manager.getMaps() }
            .setIdentifierProvider { it -> "type_${it.identifier}" }
            .setDisplayNameProvider { gui, it ->
                var str = "${ChatColor.AQUA}${it.displayName}"
                if(it.permission != null && !gui.player.hasPermission(it.permission)) {
                    str += " ${ChatColor.RED}\uD83D\uDD12"
                }
                return@setDisplayNameProvider str
            }
            .setLoreProvider { gui, it ->
                var str = it.getFormattedDescription()
                if(it.permission != null && !gui.player.hasPermission(it.permission)) {
                    str += "\n${ChatColor.RED}<Locked>"
                }
                handler.getInstances()[it.identifier]?.let { list ->
                    str += "\n"
                    for((i, inst) in list.withIndex()) {
                        str += "\n${ChatColor.GRAY}#${i + 1} | ${inst.getPhaseName()} ${ChatColor.GRAY}| ${ChatColor.DARK_GRAY}${inst.getAmountOfPlayers()}/${it.maxPlayers}"
                    }
                }
                return@setLoreProvider str
            }
            .setMaterialProvider { _ -> Material.FILLED_MAP }
            .addActionProvider { it -> RunnableAction { _, player ->
                if(it.permission != null && !player.hasPermission(it.permission)) {
                    player.sendMessage(it.lockedMessage)
                    player.playSound(player, Sound.BLOCK_NOTE_BLOCK_SNARE, 0.8F, 0.5F)
                    return@RunnableAction
                }
                if(handler.joinExpedition(it.identifier, player)) {
                    player.closeInventory()
                    player.sendMessage("${ChatColor.GREEN}You have joined the '${it.displayName}' expedition!")
                    player.playSound(player, Sound.BLOCK_NOTE_BLOCK_CHIME, 0.8F, 0.8F)
                } else {
                    player.sendMessage("${ChatColor.RED}Could not join that expedition at this time! Please try again later!")
                    player.playSound(player, Sound.BLOCK_NOTE_BLOCK_SNARE, 0.8F, 0.5F)
                }
                return@RunnableAction
            } }

        builder.runOnDestroy {
            guis.remove(player)
        }

        return builder.build()
    }

    fun dispose() {
        GuiProvider.unregister()
    }
}