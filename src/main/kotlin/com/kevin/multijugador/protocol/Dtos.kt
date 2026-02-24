package com.kevin.multijugador.protocol

enum class Difficulty { EASY, MEDIUM, HARD }

data class GameConfigDto(
    val boardSize: Int,
    val rounds: Int,
    val difficulty: Difficulty,
    val timeLimit: Int,
    val turbo: Boolean
)