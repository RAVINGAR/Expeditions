package com.ravingarinc.expeditions.queue

import org.bukkit.entity.Player

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

class PartyRequest(party: Collection<Player>) : JoinRequest {
    override val players: MutableCollection<Player> = HashSet()
    override val joinTime: Long = System.currentTimeMillis()

    init {
        players.addAll(party)
    }

    fun remove(player: Player) {
        players.remove(player)
    }
}