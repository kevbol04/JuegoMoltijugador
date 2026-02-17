package com.kevin.multijugador.server

data class GameSession(
    val id: String,
    val playerX: ClientConnection,
    val playerO: ClientConnection,
    var board: Array<CharArray>,
    var next: Char
)