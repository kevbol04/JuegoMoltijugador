package com.kevin.multijugador.client

import com.kevin.multijugador.protocol.JsonCodec
import com.kevin.multijugador.protocol.MessageType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.util.concurrent.atomic.AtomicReference

object ClientMain {

    @JvmStatic
    fun main(args: Array<String>) = runBlocking {
        val cfg = ClientConfigLoader.load()
        println("DEBUG -> Conectando a ${cfg.host}:${cfg.port}")

        val client = TcpClient(cfg.host, cfg.port)

        val mySymbol = AtomicReference<String?>(null)
        val lastState = AtomicReference<String?>(null)

        try {
            client.connect()

            val readerJob = launch(Dispatchers.IO) {
                println("DEBUG -> readLoop iniciado")
                client.readLoop { line ->
                    println("<< SERVER: $line")

                    val env = JsonCodec.decode(line) ?: return@readLoop

                    when (env.type) {
                        MessageType.QUEUE_STATUS -> {
                            if (env.payloadJson.contains("WAITING")) println("Esperando rival...")
                            if (env.payloadJson.contains("MATCHED")) println("Rival encontrado.")
                        }

                        MessageType.GAME_START -> {
                            val sym = extractString(env.payloadJson, "yourSymbol")
                            mySymbol.set(sym)
                            println("🎮 Partida iniciada. Tu símbolo: $sym")
                        }

                        MessageType.GAME_STATE -> {
                            lastState.set(env.payloadJson)
                            printGameState(env.payloadJson)

                            val next = extractString(env.payloadJson, "nextPlayer")
                            val mine = mySymbol.get()
                            if (mine != null && next == mine) {
                                val (r, c) = askMove()
                                client.send(MessageType.MAKE_MOVE, """{"row":$r,"col":$c}""")
                            }
                        }

                        MessageType.ROUND_END -> {
                            val winner = extractString(env.payloadJson, "winner")
                            when (winner) {
                                "DRAW" -> println("🤝 Empate.")
                                "X", "O" -> println("🏆 Ganador: $winner")
                                else -> println("Fin de ronda.")
                            }
                            mySymbol.set(null)
                            lastState.set(null)
                        }

                        MessageType.ERROR -> {
                            val msg = extractString(env.payloadJson, "message")
                            println("ERROR: $msg")

                            val state = lastState.get()
                            val mine = mySymbol.get()
                            if (state != null && mine != null) {
                                val next = extractString(state, "nextPlayer")
                                if (next == mine) {
                                    val (r, c) = askMove()
                                    client.send(MessageType.MAKE_MOVE, """{"row":$r,"col":$c}""")
                                }
                            }
                        }
                    }
                }
            }

            while (true) {
                println()
                println("===== MENÚ PRINCIPAL =====")
                println("1. Nueva Partida PVP")
                println("2. Nueva Partida PVE (pendiente)")
                println("3. Ver Records")
                println("4. Configuración (pendiente)")
                println("5. Salir")
                print("Elige opción: ")

                when (readLine()?.trim()) {
                    "1" -> {
                        println(">> ENVIANDO JOIN_QUEUE")
                        client.send(MessageType.JOIN_QUEUE, """{}""")
                    }
                    "3" -> {
                        println("\n--- RECORDS (descargados del servidor) ---")
                        println(client.recordsJson)
                    }
                    "5" -> {
                        println("Saliendo...")
                        client.close()
                        break
                    }
                    else -> println("Opción no válida o pendiente.")
                }
            }

            readerJob.cancel()

        } catch (e: Exception) {
            println("Error cliente: ${e.message}")
        } finally {
            client.close()
        }
    }

    private fun askMove(): Pair<Int, Int> {
        while (true) {
            print("Fila (0-2): ")
            val r = readLine()?.trim()?.toIntOrNull()
            print("Col  (0-2): ")
            val c = readLine()?.trim()?.toIntOrNull()

            if (r != null && c != null && r in 0..2 && c in 0..2) return r to c
            println("Entrada inválida. Prueba otra vez.")
        }
    }

    private fun printGameState(payload: String) {
        val board = extractBoard(payload)
        val next = extractString(payload, "nextPlayer")

        println()
        println("   0   1   2")
        for (r in 0..2) {
            val a = cell(board, r, 0)
            val b = cell(board, r, 1)
            val c = cell(board, r, 2)
            println("$r  $a | $b | $c")
            if (r != 2) println("  ---+---+---")
        }
        println("Turno: $next")
    }

    private fun cell(board: List<List<String>>, r: Int, c: Int): String {
        val v = board.getOrNull(r)?.getOrNull(c) ?: ""
        return if (v.isBlank()) " " else v
    }

    private fun extractString(json: String, field: String): String? {
        val key = """"$field":"""
        val idx = json.indexOf(key)
        if (idx == -1) return null
        val afterKey = idx + key.length
        if (afterKey >= json.length || json[afterKey] != '"') return null
        val start = afterKey + 1
        val end = json.indexOf('"', start)
        if (end == -1) return null
        return json.substring(start, end)
    }

    private fun extractBoard(payload: String): List<List<String>> {
        val key = "\"board\":"
        val idx = payload.indexOf(key)
        if (idx == -1) return List(3) { List(3) { "" } }

        val start = payload.indexOf('[', idx + key.length)
        val end = payload.lastIndexOf(']')
        val boardStr = payload.substring(start, end + 1)

        val rows = boardStr.trim().removePrefix("[").removeSuffix("]")
            .split("],[")
            .map { it.replace("[", "").replace("]", "") }

        return rows.map { row ->
            row.split(",").map { cell ->
                cell.trim().removePrefix("\"").removeSuffix("\"")
            }
        }
    }
}