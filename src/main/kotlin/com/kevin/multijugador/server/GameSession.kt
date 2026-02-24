package com.kevin.multijugador.server

import com.kevin.multijugador.protocol.Difficulty

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
    var next: Char
) {
    fun winsNeeded(): Int = (totalRounds / 2) + 1
}