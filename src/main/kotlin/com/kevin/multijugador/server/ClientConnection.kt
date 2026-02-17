package com.kevin.multijugador.server

import com.kevin.multijugador.protocol.JsonCodec
import java.io.PrintWriter
import java.net.Socket
import java.util.concurrent.atomic.AtomicBoolean

class ClientConnection(
    val socket: Socket,
    private val writer: PrintWriter
) {
    private val closed = AtomicBoolean(false)

    fun send(type: String, payloadJson: String) {
        if (closed.get()) {
            println("SERVER -> intento enviar pero conexión marcada como cerrada")
            return
        }

        val msg = JsonCodec.encode(type, payloadJson)

        try {
            writer.println(msg)
            writer.flush()
            println("SERVER ENVÍA RAW -> $msg")
        } catch (e: Exception) {
            println("SERVER -> fallo enviando: ${e.message}")
            e.printStackTrace()
            throw e
        }
    }

    fun close() {
        if (closed.compareAndSet(false, true)) {
            try { socket.close() } catch (_: Exception) {}
        }
    }
}