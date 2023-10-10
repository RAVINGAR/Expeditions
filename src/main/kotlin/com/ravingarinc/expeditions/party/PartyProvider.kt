package com.ravingarinc.expeditions.party

import com.alessiodp.parties.api.Parties
import org.bukkit.Bukkit
import org.bukkit.entity.Player

interface PartyProvider {

    fun Player.isInParty() : Boolean

    fun Player.isPartyLeader() : Boolean

    fun Player.getPartyMembers() : Collection<Player>
}

class PartiesPluginProvider : PartyProvider {
    private val api = Parties.getApi()
    override fun Player.isInParty(): Boolean {
        val partyPlayer = api.getPartyPlayer(this.uniqueId)
        return partyPlayer?.isInParty ?: false
    }

    override fun Player.isPartyLeader(): Boolean {
        val partyPlayer = api.getPartyPlayer(this.uniqueId) ?: return false
        val uuid = partyPlayer.partyId ?: return false
        val party = api.getParty(uuid) ?: return false
        return party.leader == this.uniqueId
    }

    override fun Player.getPartyMembers(): Collection<Player> {
        val partyPlayer = api.getPartyPlayer(this.uniqueId) ?: return emptyList()
        val uuid = partyPlayer.partyId ?: return emptyList()
        val party = api.getParty(uuid) ?: return emptyList()
        return party.getOnlineMembers(true).mapNotNull { p -> Bukkit.getPlayer(p.playerUUID) }.toList()
    }

}