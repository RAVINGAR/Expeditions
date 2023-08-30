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

        setFunction { sender, _ ->
            if(sender is Player) {
                if(handler.hasJoinedExpedition(sender)) {
                    sender.sendMessage("${ChatColor.RED}You cannot view other expeditions whilst in an expedition!")
                } else {
                    ExpeditionGui.openGui(plugin,sender)
                }
            } else {
                sender.sendMessage("${ChatColor.RED}This command can only be used by a player!")
            }

            return@setFunction true
        }

        addOption("instance", "expeditions.admin", "- Admin command to manage instances", 1) { _, _ -> false}
            .addOption("add", null, "<type> - Create a new expedition instance.", 3) { sender, args ->
                val type = expeditions.getMapByIdentifier(args[2])
                if(type == null) {
                    sender.sendMessage("${ChatColor.RED}Unknown expedition type called '${args[2]}'!")
                } else {
                    val inst =handler.createInstance(type)
                    if(inst == null) {
                        sender.sendMessage("${ChatColor.RED}Something went wrong creating expedition!")
                        return@addOption true
                    }
                    handler.addInstance(inst)
                    sender.sendMessage("${ChatColor.GREEN}Successfully created new instance!")
                }
                return@addOption true
            }.buildTabCompletions { _, args ->
                if(args.size == 3) {
                    return@buildTabCompletions expeditions.getMaps().map { it.identifier }.toList()
                }
                return@buildTabCompletions emptyList<String>()
            }.parent
            .addOption("remove", null, "<type> - Removes an empty expedition instance.", 3) { sender, args ->
                val instances = handler.getInstances()[args[2]]
                if(instances == null) {
                    sender.sendMessage("${ChatColor.RED}Unknown expedition type called '${args[2]}'!")
                } else {
                    for(inst in ArrayList(instances)) {
                        if(inst.getAmountOfPlayers() == 0) {
                            handler.destroyInstance(inst)
                            handler.removeInstance(inst)
                            sender.sendMessage("${ChatColor.GREEN}Successfully removed and destroyed instance!")
                            return@addOption true
                        }
                    }
                    sender.sendMessage("${ChatColor.RED}Could not find empty instance to destroy!")
                }
                return@addOption true
            }.buildTabCompletions { _, args ->
                if(args.size == 3) {
                    return@buildTabCompletions expeditions.getMaps().map { it.identifier }.toList()
                }
                return@buildTabCompletions emptyList<String>()
            }

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

        addOption("reload", "expeditions.admin", "- Reloads the plugin", 1) { sender, args ->
            sender.sendMessage("${ChatColor.YELLOW}Attempting to reload Expeditions... this may take a while.")
            plugin.reload()
            sender.sendMessage("${ChatColor.GREEN}Successfully reloaded Expeditions!")
            return@addOption true
        }

        addHelpOption(ChatColor.AQUA, ChatColor.DARK_AQUA)
    }
}