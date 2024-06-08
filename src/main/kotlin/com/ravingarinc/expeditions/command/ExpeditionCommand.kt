package com.ravingarinc.expeditions.command

import com.ravingarinc.api.command.BaseCommand
import com.ravingarinc.api.module.RavinPlugin
import com.ravingarinc.expeditions.locale.ExpeditionManager
import com.ravingarinc.expeditions.party.PartyManager
import com.ravingarinc.expeditions.play.PlayHandler
import com.ravingarinc.expeditions.play.instance.RemoveReason
import com.ravingarinc.expeditions.queue.JoinRequest
import com.ravingarinc.expeditions.queue.PartyRequest
import com.ravingarinc.expeditions.queue.PlayerRequest
import com.ravingarinc.expeditions.queue.QueueManager
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.ChatColor
import org.bukkit.entity.Player

class ExpeditionCommand(plugin: RavinPlugin) : BaseCommand(plugin, "expeditions", null, "", 0, { _, _ -> true }) {
    init {
        val expeditions = plugin.getModule(ExpeditionManager::class.java)
        val handler = plugin.getModule(PlayHandler::class.java)
        val queueManager = plugin.getModule(QueueManager::class.java)

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

        addOption("queue", "expeditions.queue", "<rotation> - Queue for a random expedition within a rotation.", 2) { sender, args ->
            if(sender !is Player) {
                sender.sendMessage(Component.text("Only a player can use this command! For admin usages, please use /expeditions admin queue").color(NamedTextColor.RED))
                return@addOption true
            }
            if(queueManager.isRotation(args[1])) {
                tryQueuePlayer(sender, args[1])
            } else {
                sender.sendMessage(Component.text("Could not find a rotation called '${args[1]}'!").color(NamedTextColor.RED))
            }
            return@addOption true
        }

        addOption("dequeue", "expeditions.queue", "- Leave any expedition queues you are apart of.", 1) { sender, args ->
            if(sender !is Player) {
                sender.sendMessage(Component.text("Only a player can use this command! For admin usages, please use /expeditions admin dequeue").color(NamedTextColor.RED))
                return@addOption true
            }
            val requests = queueManager.getQueuedRequests()
            for(request in requests) {
                if(!request.contains(sender)) continue
                queueManager.removePlayer(sender)
                sender.sendMessage(Component.text("You have been removed from the queue!").color(NamedTextColor.GREEN))
                return@addOption true
            }
            sender.sendMessage(Component.text("You are not currently queued for any expeditions!").color(NamedTextColor.YELLOW))
            return@addOption true
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
            .addOption("join", null, "<player> <expedition> - Force join an expedition", 3) { sender, args ->
                val player = sender as? Player ?: if (args.size > 2) plugin.server.getPlayer(args[2]) else null
                if(player == null) {
                    sender.sendMessage("${ChatColor.RED}Could not find player!")
                } else {
                    val expedition = expeditions.getMapByIdentifier(args[3])
                    if(expedition == null) {
                        sender.sendMessage("${ChatColor.RED}There is no such expedition called '${args[3]}'")
                    } else {
                        if(handler.tryJoinExpedition(expedition.identifier, player)) {
                            if(sender is Player) {
                                sender.sendMessage("${ChatColor.GREEN}${player.name} has forcefully joined the '${args[3]}' expedition!")
                                player.sendMessage("${ChatColor.GREEN}You were forced to join an expedition!")
                            }
                        } else {
                            sender.sendMessage("${ChatColor.RED}${player.name} cannot join that expedition at this time!")
                        }
                    }
                }
                return@addOption true
            }.buildTabCompletions { _, args ->
                return@buildTabCompletions when(args.size) {
                    3 -> null
                    4 -> expeditions.getMaps().stream().map { it.identifier }.toList()
                    else -> emptyList()
                }
            }.parent
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
                        if(sender is Player) {
                            sender.sendMessage("${ChatColor.GREEN}${player.name} has been force removed from their expedition!")
                            player.sendMessage("${ChatColor.GREEN}You have been force removed from your expedition!")
                        }

                    }
                }
                return@addOption true
            }.parent.addOption("reload", "expeditions.admin", "- Reloads the plugin", 2) { sender, _ ->
                sender.sendMessage("${ChatColor.YELLOW}Attempting to reload Expeditions... this may take a while.")
                plugin.reload()
                sender.sendMessage("${ChatColor.GREEN}Successfully reloaded Expeditions!")
            return@addOption true
            }.parent.addOption("lock", "expeditions.admin", "<on|off> - Set if joining expeditions should be locked", 2) { sender, args ->
                val intendedState : String = (if(args.size > 2) args[2] else if(handler.areExpeditionsLocked()) "off" else "on").lowercase()
                val state = when(intendedState) {
                    "on" -> true
                    "off" -> false
                    else -> {
                        sender.sendMessage("${ChatColor.RED}Unknown argument! Please specify either 'on' or 'off', or no argument to toggle!")
                        return@addOption true
                    }
                }
                if(state == handler.areExpeditionsLocked()) {
                    sender.sendMessage("${ChatColor.RED}You cannot set expedition lock to $intendedState as that value is already set!")
                    return@addOption true
                }
                handler.lockExpeditions(state)
                sender.sendMessage("${ChatColor.GREEN}Successfully set expeditions lock to '$intendedState'")
                return@addOption true
            }.buildTabCompletions { _, args ->
                if(args.size == 3) {
                    return@buildTabCompletions listOf("on", "off")
                }
                return@buildTabCompletions emptyList<String>()
            }

        addHelpOption(ChatColor.AQUA, ChatColor.DARK_AQUA)
    }

    /**
     * Try and queue the given player, including their party if they are in one and return the result as
     * true if a success, or false if something went wrong.
     */
    private fun tryQueuePlayer(player: Player, rotation: String) : Boolean {
        val queueManager = plugin.getModule(QueueManager::class.java)
        val provider = plugin.getModule(PartyManager::class.java).getProvider()
        var request: JoinRequest? = null
        if(provider != null) with(provider) {
            if(player.isInParty()) {
                val partyLeaderUUID = player.findPartyLeader()!!
                if(partyLeaderUUID == player.uniqueId) {
                    player.sendMessage(
                        Component.text("You cannot queue for any expeditions as you are in a party and " +
                                "only the party leader can queue for expeditions!")
                            .color(NamedTextColor.RED))
                    return false
                }
                request = PartyRequest(partyLeaderUUID, player.getPartyMembers())
            }
        }
        val finalRequest = request ?: PlayerRequest(player)
        finalRequest.players.forEach {
            it.sendMessage(Component.text("You have joined the expeditions queue for '$rotation'!").color(NamedTextColor.GRAY))
        }
        queueManager.enqueueRequest(rotation, finalRequest, false)
        return true
    }
}