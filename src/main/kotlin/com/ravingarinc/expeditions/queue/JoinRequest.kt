package com.ravingarinc.expeditions.queue

import org.bukkit.entity.Player
import java.util.*

sealed interface JoinRequest {
    val joinTime: Long
    val players: Collection<Player>

    fun contains(player: Player) : Boolean {
        return players.contains(player)
    }

    fun size() : Int {
        return players.size
    }
}

class PlayerRequest(player: Player) : JoinRequest {
    override val joinTime = System.currentTimeMillis()
    override val players = listOf(player)

    override fun size(): Int {
        return 1
    }
}

class PartyRequest(partyLeader: UUID, party: Collection<Player>) : JoinRequest {
    override val players: Collection<Player> get() = innerPlayers
    override val joinTime: Long = System.currentTimeMillis()

    val partyLeader: UUID get() = innerPartyLeader
    private var innerPartyLeader: UUID = partyLeader

    private val innerPlayers: MutableCollection<Player> = HashSet()

    init {
        innerPlayers.addAll(party)
    }

    fun remove(player: Player) {
        innerPlayers.remove(player)
        if(innerPlayers.isNotEmpty() && partyLeader == player.uniqueId) {
            innerPartyLeader = innerPlayers.firstOrNull()?.uniqueId ?: throw IllegalStateException("Players in Party Request should not be empty at this state!")
        }
    }
}