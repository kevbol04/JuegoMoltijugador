package com.kevin.multijugador.server

import java.util.concurrent.ConcurrentHashMap

object ActiveUsers {
    private val users = ConcurrentHashMap.newKeySet<String>()

    fun tryAdd(username: String): Boolean = users.add(username)

    fun remove(username: String) {
        users.remove(username)
    }

    fun snapshot(): Set<String> = users.toSet()
}