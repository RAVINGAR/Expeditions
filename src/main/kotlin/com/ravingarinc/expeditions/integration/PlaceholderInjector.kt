package com.ravingarinc.expeditions.integration

import com.ravingarinc.api.module.ModuleLoadException
import com.ravingarinc.api.module.RavinPlugin
import com.ravingarinc.api.module.SuspendingModule
import com.ravingarinc.expeditions.play.PlayHandler
import me.clip.placeholderapi.expansion.PlaceholderExpansion
import org.bukkit.OfflinePlayer
import org.bukkit.entity.Player

class PlaceholderInjector(plugin: RavinPlugin) : SuspendingModule(PlaceholderInjector::class.java, plugin, false) {

    override suspend fun suspendLoad() {
        if(plugin.server.pluginManager.getPlugin("PlaceholderAPI") == null) {
            throw ModuleLoadException(this, ModuleLoadException.Reason.EXCEPTION, IllegalStateException("Could not find PlaceholderAPI plugin!"))
        }
        ExpeditionsExpansion(plugin).register()
    }
    override suspend fun suspendCancel() {

    }
}

class ExpeditionsExpansion(val plugin: RavinPlugin) : PlaceholderExpansion() {
    val manager = plugin.getModule(PlayHandler::class.java)
    private val placeholders: Map<String, (Player) -> String> = buildMap {
        this["time"] = {
            val exp = manager.getJoinedExpedition(it)
            if(exp == null) {
                "0"
            } else {
                (exp.getTimeLeft() / 1000).toString()
            }
        }
        this["area"] = {
            val exp = manager.getJoinedExpedition(it)
            if(exp == null) {
                ""
            } else {
                exp.getPlayerArea(it)
            }
        }
        this["phase"] = {
            val exp = manager.getJoinedExpedition(it)
            if(exp == null) {
                ""
            } else {
                exp.getPhaseName()
            }
        }
    }

    override fun onRequest(offlinePlayer: OfflinePlayer?, params: String): String? {
        if(offlinePlayer == null) return null
        val player = offlinePlayer.player ?: return null
        return placeholders[params]?.invoke(player)
    }

    override fun persist(): Boolean {
        return true
    }
    override fun getIdentifier(): String {
        return "expeditions"
    }

    override fun getAuthor(): String {
        return "RAVINGAR"
    }

    override fun getVersion(): String {
        return "1.0.0"
    }
}