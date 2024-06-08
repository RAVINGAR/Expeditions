package com.ravingarinc.expeditions.party

import com.alessiodp.parties.api.Parties
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import java.util.*

interface PartyProvider {

    fun Player.isInParty() : Boolean

    fun Player.isPartyLeader() : Boolean

    fun Player.getPartyMembers() : Collection<Player>

    fun Player.findPartyLeader() : UUID?
}

class PartiesPluginProvider : PartyProvider {
    private val api = Parties.getApi()
    override fun Player.isInParty(): Boolean {
        val partyPlayer = api.getPartyPlayer(this.uniqueId)
        return partyPlayer?.isInParty ?: false
    }

    override fun Player.isPartyLeader(): Boolean {
        val leaderUUID = findPartyLeader() ?: return false
        return leaderUUID == this.uniqueId
    }

    override fun Player.findPartyLeader(): UUID? {
        val partyPlayer = api.getPartyPlayer(this.uniqueId) ?: return null
        val uuid = partyPlayer.partyId ?: return null
        val party = api.getParty(uuid) ?: return null
        return party.leader
    }

    override fun Player.getPartyMembers(): Collection<Player> {
        val partyPlayer = api.getPartyPlayer(this.uniqueId) ?: return emptyList()
        val uuid = partyPlayer.partyId ?: return emptyList()
        val party = api.getParty(uuid) ?: return emptyList()
        return party.getOnlineMembers(true).mapNotNull { p -> Bukkit.getPlayer(p.playerUUID) }.toList()
    }

}