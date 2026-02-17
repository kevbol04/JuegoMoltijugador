package com.kevin.multijugador.client

import com.kevin.multijugador.protocol.JsonCodec
import com.kevin.multijugador.protocol.MessageType
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.Socket
import java.util.concurrent.atomic.AtomicBoolean

class TcpClient(
    private val host: String,
    private val port: Int
) {
    private lateinit var socket: Socket
    private lateinit var reader: BufferedReader
    private lateinit var writer: PrintWriter
    private val closed = AtomicBoolean(false)

    var recordsJson: String = """{"players":{}}"""
        private set

    fun connect() {
        socket = Socket(host, port)
        reader = BufferedReader(InputStreamReader(socket.getInputStream()))
        writer = PrintWriter(socket.getOutputStream(), true)

        val first = reader.readLine() ?: throw IllegalStateException("Servidor no envió datos al conectar")

        if (!first.contains("\"type\":\"${MessageType.RECORDS_SYNC}\"")) {
            throw IllegalStateException("Se esperaba RECORDS_SYNC y llegó: $first")
        }

        recordsJson = extractPayloadJson(first)
        println("Conectado a $host:$port")
        println("Records sincronizados")
    }

    fun send(type: String, payloadJson: String) {
        sendRaw(JsonCodec.encode(type, payloadJson))
    }

    fun sendRaw(jsonLine: String) {
        if (closed.get()) return
        writer.println(jsonLine)
    }

    fun close() {
        if (closed.compareAndSet(false, true)) {
            try { socket.close() } catch (_: Exception) {}
        }
    }

    fun isClosed(): Boolean = closed.get()

    fun readLoop(onLine: (String) -> Unit) {
        try {
            while (true) {
                val line = reader.readLine() ?: break
                onLine(line)
            }
        } finally {
            close()
        }
    }

    private fun extractPayloadJson(msg: String): String {
        val key = "\"payload\":"
        val idx = msg.indexOf(key)
        if (idx == -1) return "{}"

        val start = idx + key.length
        val payload = msg.substring(start).trim()
        return if (payload.endsWith("}")) payload.dropLast(1).trim() else payload
    }
}