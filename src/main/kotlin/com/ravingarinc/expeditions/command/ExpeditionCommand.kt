package com.ravingarinc.expeditions.command

import com.ravingarinc.api.command.BaseCommand
import com.ravingarinc.api.module.RavinPlugin
import com.ravingarinc.expeditions.locale.ExpeditionManager
import com.ravingarinc.expeditions.play.PlayHandler
import org.bukkit.ChatColor
import org.bukkit.entity.Player

class ExpeditionCommand(plugin: RavinPlugin) : BaseCommand(plugin, "expeditions", null) {
    init {
        val expeditions = plugin.getModule(ExpeditionManager::class.java)
        val handler = plugin.getModule(PlayHandler::class.java)

        addOption("admin", "expeditions.admin", "- Admin command for Expeditions", 2) { _, _ -> false }
            .addOption("maps", "expeditions.admin", "- View currently loaded maps", 2) { sender, args ->
                expeditions.getMaps().forEach {
                    sender.sendMessage("${ChatColor.DARK_AQUA}${it.displayName} - ${ChatColor.GRAY} Max Players = ${it.maxPlayers}, Radius = ${it.radius}")
                }
                return@addOption true
            }

        addOption("view", null, "- View and join available expeditions", 1) { sender, args ->
            if(sender is Player) {
                if(handler.hasJoinedExpedition(sender)) {
                    sender.sendMessage("${ChatColor.RED}You cannot view other expeditions whilst in an expedition!")
                } else {
                    ExpeditionGui.openGui(plugin,sender)
                }

            } else {
                sender.sendMessage("${ChatColor.RED}This command can only be used by a player!")
            }

            return@addOption true
        }

        addOption("extract", null, "- Extract from your current expedition if you're in one", 1) { sender, args ->

            return@addOption true
        }

        addHelpOption(ChatColor.AQUA, ChatColor.DARK_AQUA)
    }
}