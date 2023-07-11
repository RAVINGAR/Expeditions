package com.ravingarinc.expeditions.command

import com.ravingarinc.api.command.BaseCommand
import com.ravingarinc.api.module.RavinPlugin
import com.ravingarinc.expeditions.locale.ExpeditionManager
import com.ravingarinc.expeditions.play.PlayHandler
import com.ravingarinc.expeditions.play.instance.RemoveReason
import org.bukkit.ChatColor
import org.bukkit.entity.Player

class ExpeditionCommand(plugin: RavinPlugin) : BaseCommand(plugin, "expeditions", null) {
    init {
        val expeditions = plugin.getModule(ExpeditionManager::class.java)
        val handler = plugin.getModule(PlayHandler::class.java)

        addOption("admin", "expeditions.admin", "- Admin command for Expeditions", 1) { _, _ -> false }
            .addOption("leave", null, "<player> - Force leave an expedition", 2) { sender, args ->
                val player = sender as? Player ?: if (args.size > 2) plugin.server.getPlayer(args[2]) else null
                if(player == null) {
                    sender.sendMessage("${ChatColor.RED}Could not find player!")
                } else {
                    val inst = handler.getJoinedExpedition(player)
                    if(inst == null) {
                        sender.sendMessage("${ChatColor.RED}That player is not currently apart of an expedition!")
                    } else {
                        inst.removePlayer(player, RemoveReason.EXTRACTION)
                        sender.sendMessage("${ChatColor.GREEN}${player.name} has been force removed from their expedition!")
                        player.sendMessage("${ChatColor.GREEN}You have been force removed from your expedition!")
                    }
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

        addHelpOption(ChatColor.AQUA, ChatColor.DARK_AQUA)
    }
}