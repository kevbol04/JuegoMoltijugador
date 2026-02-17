package com.kevin.multijugador.protocol

object MessageType {
    const val RECORDS_SYNC = "RECORDS_SYNC"

    const val JOIN_QUEUE = "JOIN_QUEUE"
    const val QUEUE_STATUS = "QUEUE_STATUS"

    const val GAME_START = "GAME_START"
    const val MAKE_MOVE = "MAKE_MOVE"

    const val GAME_STATE = "GAME_STATE"
    const val ROUND_END = "ROUND_END"
    const val ERROR = "ERROR"
}