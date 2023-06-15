package com.ravingarinc.expeditions.command

import com.ravingarinc.api.command.BaseCommand
import com.ravingarinc.api.module.RavinPlugin
import com.ravingarinc.expeditions.locale.ExpeditionManager
import org.bukkit.ChatColor

class ExpeditionCommand(plugin: RavinPlugin) : BaseCommand(plugin, "expeditions", null) {
    init {
        val expeditions = plugin.getModule(ExpeditionManager::class.java)

        addOption("admin", "expeditions.admin", "- Admin command for Expeditions", 2) { _, _ -> false }
            .addOption("maps", "expeditions.admin", "- View currently loaded maps", 2) { sender, args ->

                return@addOption true
            }

        addOption("view", null, "- View and join available expeditions", 1) { sender, args ->

            return@addOption true
        }

        addOption("extract", null, "- Extract from your current expedition if you're in one", 1) { sender, args ->

            return@addOption true
        }

        addHelpOption(ChatColor.AQUA, ChatColor.DARK_AQUA)
    }
}