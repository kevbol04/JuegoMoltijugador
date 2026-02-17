package com.kevin.multijugador.server

import java.util.concurrent.atomic.AtomicReference

class MatchmakingQueue {
    private val waiting = AtomicReference<ClientConnection?>(null)

    fun tryEnqueue(client: ClientConnection): Pair<ClientConnection?, ClientConnection?> {
        val prev = waiting.getAndSet(client)
        return if (prev == null) {
            Pair(null, client)
        } else {
            waiting.set(null)
            Pair(prev, client)
        }
    }

    fun removeIfWaiting(client: ClientConnection) {
        waiting.compareAndSet(client, null)
    }
}