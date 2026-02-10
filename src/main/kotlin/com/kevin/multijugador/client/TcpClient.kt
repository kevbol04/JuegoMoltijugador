package com.kevin.multijugador.client

import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.Socket

class TcpClient(
    private val host: String,
    private val port: Int
) {
    private lateinit var socket: Socket
    private lateinit var reader: BufferedReader
    private lateinit var writer: PrintWriter

    var recordsJson: String = """{"players":{}}"""
        private set

    fun connect() {
        socket = Socket(host, port)
        reader = BufferedReader(InputStreamReader(socket.getInputStream()))
        writer = PrintWriter(socket.getOutputStream(), true)

        val first = reader.readLine() ?: throw IllegalStateException("Servidor no envió datos al conectar")

        if (!first.contains("\"type\":\"RECORDS_SYNC\"")) {
            throw IllegalStateException("Se esperaba RECORDS_SYNC y llegó: $first")
        }

        recordsJson = extractPayloadJson(first)
        println("Conectado a $host:$port")
        println("Records sincronizados")
    }

    fun sendRaw(jsonLine: String) {
        writer.println(jsonLine)
    }

    fun close() {
        try { socket.close() } catch (_: Exception) {}
    }

    private fun extractPayloadJson(msg: String): String {
        val key = "\"payload\":"
        val idx = msg.indexOf(key)
        if (idx == -1) return "{}"

        val payloadPlusEnding = msg.substring(idx + key.length).trim()

        return if (payloadPlusEnding.endsWith("}")) {
            payloadPlusEnding.dropLast(1).trim()
        } else {
            payloadPlusEnding
        }
    }
}