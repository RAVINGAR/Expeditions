package com.ravingarinc.expeditions.queue

import com.ravingarinc.expeditions.api.atomic
import org.bukkit.entity.Player
import java.util.*

sealed interface JoinRequest {
    val joinTime: Long
    val players: Collection<Player>
    var score: Int
    val rotation: String

    fun contains(player: Player) : Boolean {
        return players.contains(player)
    }

    fun size() : Int {
        return players.size
    }
}

class PlayerRequest(override val rotation: String, player: Player, score: Int) : JoinRequest {
    override val joinTime = System.currentTimeMillis()
    override val players = listOf(player)
    override var score: Int by atomic(score)

    override fun size(): Int {
        return 1
    }
}

class PartyRequest(override val rotation: String, partyLeader: UUID, party: Collection<Player>, score: Int) : JoinRequest {
    override val players: Collection<Player> get() = innerPlayers
    override val joinTime: Long = System.currentTimeMillis()
    override var score: Int by atomic(score)

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