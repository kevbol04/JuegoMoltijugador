package com.kevin.multijugador.protocol

data class Envelope(
    val type: String,
    val payloadJson: String
)