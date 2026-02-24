package com.kevin.multijugador.server

class MatchmakingQueue {

    data class QueueEntry(
        val client: ClientConnection,
        val config: GameConfig
    )

    data class GameConfig(
        val boardSize: Int,
        val rounds: Int,
        val timeLimit: Int,
        val turbo: Boolean
    )

    @Volatile
    private var waiting: QueueEntry? = null

    @Synchronized
    fun tryEnqueue(entry: QueueEntry): Pair<QueueEntry?, QueueEntry?> {
        val prev = waiting
        return if (prev == null) {
            waiting = entry
            Pair(null, entry)
        } else {
            waiting = null
            Pair(prev, entry)
        }
    }

    @Synchronized
    fun removeIfWaiting(client: ClientConnection) {
        if (waiting?.client == client) waiting = null
    }
}