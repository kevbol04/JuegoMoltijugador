package com.kevin.multijugador.server

import com.kevin.multijugador.protocol.Difficulty
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.atomic.AtomicLong

enum class GameMode { PVP, PVE }

data class GameSession(
    val id: String,
    val mode: GameMode,
    val playerX: ClientConnection,
    val playerO: ClientConnection?,
    val difficulty: Difficulty? = null,

    val totalRounds: Int = 3,
    var round: Int = 1,
    var xWins: Int = 0,
    var oWins: Int = 0,

    var board: Array<CharArray>,
    var next: Char,

    val timeLimitSec: Int = 30,
    val turbo: Boolean = false,

    var turnTimer: ScheduledFuture<*>? = null,
    val turnToken: AtomicLong = AtomicLong(0)
) {
    fun winsNeeded(): Int = (totalRounds / 2) + 1
}