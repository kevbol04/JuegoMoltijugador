package com.kevin.multijugador.client

import com.kevin.multijugador.protocol.JsonCodec
import com.kevin.multijugador.protocol.MessageType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.util.concurrent.atomic.AtomicReference

object ClientMain {

    private enum class ClientState { MENU, QUEUE, IN_GAME }

    data class GameConfig(
        var boardSize: Int = 3,
        var rounds: Int = 3,
        var difficulty: String = "EASY",
        var timeLimit: Int = 30,
        var turbo: Boolean = false
    )

    @JvmStatic
    fun main(args: Array<String>) = runBlocking {
        val cfg = ClientConfigLoader.load()
        val client = TcpClient(cfg.host, cfg.port)

        val mySymbol = AtomicReference<String?>(null)
        val lastState = AtomicReference<String?>(null)
        val clientState = AtomicReference(ClientState.MENU)
        val usernameRef = AtomicReference<String?>(null)
        val configRef = AtomicReference(GameConfig())

        try {
            client.connect()

            var username: String

            while (true) {
                println("===== LOGIN =====")
                print("Introduce tu nombre de usuario: ")
                username = readLine()?.trim().orEmpty()

                client.send(MessageType.LOGIN, """{"username":"$username"}""")

                val line = client.readBlockingLine()
                val env = JsonCodec.decode(line) ?: continue

                when (env.type) {
                    MessageType.LOGIN_OK -> {
                        println("Sesión iniciada como $username")
                        usernameRef.set(username.lowercase())
                        break
                    }

                    MessageType.LOGIN_ERROR -> {
                        val msg = extractString(env.payloadJson, "message") ?: "Error"
                        println(" $msg")
                        println("Inténtalo de nuevo.\n")
                    }
                }
            }

            while (true) {
                val line = client.readBlockingLine()
                val env = JsonCodec.decode(line) ?: continue
                if (env.type == MessageType.RECORDS_SYNC) {
                    client.setRecordsJson(env.payloadJson)
                    println("Records sincronizados\n")
                    break
                }
            }

            launch(Dispatchers.IO) {
                client.readLoop { line ->
                    val env = JsonCodec.decode(line) ?: return@readLoop

                    when (env.type) {

                        MessageType.QUEUE_STATUS -> {
                            if (env.payloadJson.contains("WAITING")) {
                                println("\nEsperando rival...")
                                clientState.set(ClientState.QUEUE)
                            }
                            if (env.payloadJson.contains("MATCHED")) {
                                println("\nRival encontrado.")
                            }
                        }

                        MessageType.GAME_START -> {
                            val sym = extractString(env.payloadJson, "yourSymbol")
                            mySymbol.set(sym)
                            clientState.set(ClientState.IN_GAME)

                            val round = extractInt(env.payloadJson, "round")
                            val totalRounds = extractInt(env.payloadJson, "totalRounds")
                            val xWins = extractInt(env.payloadJson, "xWins")
                            val oWins = extractInt(env.payloadJson, "oWins")
                            val needed = extractInt(env.payloadJson, "winsNeeded")

                            if (round != null && totalRounds != null) {
                                println("\n=== RONDA $round/$totalRounds ===")
                                if (xWins != null && oWins != null && needed != null) {
                                    println("Marcador -> X:$xWins | O:$oWins | (necesitas $needed)")
                                }
                            } else {
                                println("\nPartida iniciada.")
                            }

                            println("Tu símbolo: $sym")
                        }

                        MessageType.GAME_STATE -> {
                            lastState.set(env.payloadJson)

                            val size = extractInt(env.payloadJson, "boardSize") ?: 3
                            printGameState(env.payloadJson, size)

                            val next = extractString(env.payloadJson, "nextPlayer")
                            val mine = mySymbol.get()
                            if (mine != null && next == mine) {
                                val (r, c) = askMove(size)
                                client.send(MessageType.MAKE_MOVE, """{"row":$r,"col":$c}""")
                            }
                        }

                        MessageType.ROUND_END -> {
                            val roundWinner = extractString(env.payloadJson, "roundWinner") ?: "DRAW"
                            val seriesOver = env.payloadJson.contains(""""seriesOver":true""")

                            val xWins = extractInt(env.payloadJson, "xWins") ?: 0
                            val oWins = extractInt(env.payloadJson, "oWins") ?: 0
                            val round = extractInt(env.payloadJson, "round") ?: 1
                            val totalRounds = extractInt(env.payloadJson, "totalRounds") ?: 3
                            val needed = extractInt(env.payloadJson, "winsNeeded") ?: ((totalRounds / 2) + 1)

                            println("\n=== FIN DE RONDA $round/$totalRounds ===")
                            when (roundWinner) {
                                "DRAW" -> println("🤝 Empate en la ronda.")
                                "X", "O" -> println("🏁 Ronda ganada por: $roundWinner")
                                else -> println("Fin de ronda.")
                            }
                            println("Marcador -> X:$xWins | O:$oWins | (necesitas $needed)")

                            if (seriesOver) {
                                val winner = extractString(env.payloadJson, "winner") ?: "DRAW"
                                val winnerUser = extractString(env.payloadJson, "winnerUser")
                                val loserUser = extractString(env.payloadJson, "loserUser")

                                when (winner) {
                                    "DRAW" -> println("\n🏁 SERIE FINALIZADA: EMPATE.")
                                    "X", "O" -> {
                                        println("\n🏆 SERIE FINALIZADA. Ganador: $winner ${winnerUser?.let { "($it)" } ?: ""}")
                                        if (winnerUser != null && loserUser != null) {
                                            println("Resultado final: $winnerUser vs $loserUser")
                                        }
                                    }
                                    else -> println("\n🏁 SERIE FINALIZADA.")
                                }

                                mySymbol.set(null)
                                lastState.set(null)
                                clientState.set(ClientState.MENU)
                            } else {
                                clientState.set(ClientState.IN_GAME)
                            }
                        }

                        MessageType.ERROR -> {
                            val msg = extractString(env.payloadJson, "message") ?: "Error desconocido"
                            println("\nERROR: $msg")

                            val mine = mySymbol.get()
                            val stateJson = lastState.get()
                            val next = if (stateJson != null) extractString(stateJson, "nextPlayer") else null
                            val size = if (stateJson != null) extractInt(stateJson, "boardSize") ?: 3 else 3

                            if (clientState.get() == ClientState.IN_GAME && mine != null && next == mine) {
                                println("Repite la tirada.")
                                val (r, c) = askMove(size)
                                client.send(MessageType.MAKE_MOVE, """{"row":$r,"col":$c}""")
                            }
                        }

                        MessageType.RECORDS_SYNC -> {
                            client.setRecordsJson(env.payloadJson)
                        }
                    }
                }
            }

            while (true) {
                if (clientState.get() != ClientState.MENU) {
                    Thread.sleep(200)
                    continue
                }

                val cfgLocal = configRef.get()

                println("===== MENÚ PRINCIPAL =====")
                println("1. Nueva Partida PVP")
                println("2. Nueva Partida PVE")
                println("3. Ver Records")
                println("4. Configuración")
                println("5. Salir")
                println("Config actual: tablero ${cfgLocal.boardSize}x${cfgLocal.boardSize}, mejor de ${cfgLocal.rounds}, IA ${cfgLocal.difficulty}, timeLimit ${cfgLocal.timeLimit}, turbo ${cfgLocal.turbo}")
                print("Elige opción: ")

                when (readLine()?.trim()) {
                    "1" -> {
                        val cfgSend = configRef.get()
                        client.send(
                            MessageType.JOIN_QUEUE,
                            """{"boardSize":${cfgSend.boardSize},"rounds":${cfgSend.rounds},"timeLimit":${cfgSend.timeLimit},"turbo":${cfgSend.turbo}}"""
                        )
                        clientState.set(ClientState.QUEUE)
                    }

                    "2" -> {
                        val cfgSend = configRef.get()
                        client.send(
                            MessageType.START_PVE,
                            """{"boardSize":${cfgSend.boardSize},"rounds":${cfgSend.rounds},"difficulty":"${cfgSend.difficulty}","timeLimit":${cfgSend.timeLimit},"turbo":${cfgSend.turbo}}"""
                        )
                        clientState.set(ClientState.QUEUE)
                    }

                    "3" -> {
                        println("\n===== RECORDS =====")
                        printFormattedRecords(client.recordsJson)
                        println()
                    }

                    "4" -> {
                        configMenu(configRef)
                    }

                    "5" -> {
                        println("Saliendo...")
                        mySymbol.set(null)
                        lastState.set(null)
                        clientState.set(ClientState.MENU)
                        client.close()
                        return@runBlocking
                    }

                    else -> println("Opción no válida.\n")
                }
            }

        } catch (e: Exception) {
            println("Error cliente: ${e.message}")
        } finally {
            client.close()
        }
    }

    private fun askMove(size: Int): Pair<Int, Int> {
        val max = size - 1
        while (true) {
            print("Fila (0-$max): ")
            val r = readLine()?.trim()?.toIntOrNull()
            print("Col  (0-$max): ")
            val c = readLine()?.trim()?.toIntOrNull()

            if (r == null || c == null) {
                println("Debes escribir números.")
                continue
            }
            if (r !in 0..max || c !in 0..max) {
                println("Fuera de rango. Debe ser entre 0 y $max.")
                continue
            }
            return r to c
        }
    }

    private fun printGameState(payload: String, size: Int) {
        val board = extractBoard(payload, size)
        val next = extractString(payload, "nextPlayer")

        println()
        print("   ")
        for (c in 0 until size) print("$c   ")
        println()

        for (r in 0 until size) {
            print("$r  ")
            for (c in 0 until size) {
                val v = board[r][c].ifBlank { " " }
                print(v)
                if (c != size - 1) print(" | ")
            }
            println()
            if (r != size - 1) {
                print("   ")
                for (c in 0 until size) {
                    print("---")
                    if (c != size - 1) print("+")
                }
                println()
            }
        }
        println("Turno: $next")
    }

    private fun extractBoard(payload: String, size: Int): List<List<String>> {
        val boardStart = payload.indexOf("\"board\":")
        if (boardStart == -1) return List(size) { List(size) { "" } }

        val firstBracket = payload.indexOf('[', boardStart)
        val nextPlayerIdx = payload.indexOf("\"nextPlayer\"", boardStart).let { if (it == -1) payload.length else it }
        val boardChunk = payload.substring(firstBracket, nextPlayerIdx)

        val values = """"([^"]*)"""".toRegex()
            .findAll(boardChunk)
            .map { it.groupValues[1].trim() }
            .toList()

        val total = size * size
        val cells = values.take(total) + List((total - values.take(total).size).coerceAtLeast(0)) { "" }

        val grid = mutableListOf<List<String>>()
        var idx = 0
        for (r in 0 until size) {
            val row = mutableListOf<String>()
            for (c in 0 until size) {
                row.add(cells[idx++])
            }
            grid.add(row)
        }
        return grid
    }

    private fun printFormattedRecords(json: String) {
        val playersSection = """"players"\s*:\s*\{(.*)\}""".toRegex()
            .find(json)?.groupValues?.getOrNull(1)
            ?: run {
                println("No hay estadísticas aún.")
                return
            }

        val playerRegex = """"([^"]+)"\s*:\s*\{([^}]*)\}""".toRegex()
        val players = playerRegex.findAll(playersSection).toList()

        if (players.isEmpty()) {
            println("No hay estadísticas aún.")
            return
        }

        for (match in players) {
            val username = match.groupValues[1]
            val stats = match.groupValues[2]

            val wins = extractStat(stats, "wins")
            val losses = extractStat(stats, "losses")
            val draws = extractStat(stats, "draws")
            val streak = extractStat(stats, "streak")
            val bestStreak = extractStat(stats, "bestStreak")

            println("Usuario: $username")
            println("   🏆 Victorias: $wins")
            println("   ❌ Derrotas: $losses")
            println("   🤝 Empates: $draws")
            println("   🔥 Racha actual: $streak")
            println("   ⭐ Mejor racha: $bestStreak")
            println("---------------------------------")
        }
    }

    private fun extractStat(stats: String, field: String): Int {
        val regex = """"$field"\s*:\s*(\d+)""".toRegex()
        return regex.find(stats)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 0
    }

    private fun extractString(json: String, field: String): String? {
        val regex = """"$field"\s*:\s*"([^"]*)"""".toRegex()
        return regex.find(json)?.groupValues?.getOrNull(1)
    }

    private fun extractInt(json: String, field: String): Int? {
        val regex = """"$field"\s*:\s*(\d+)""".toRegex()
        return regex.find(json)?.groupValues?.getOrNull(1)?.toIntOrNull()
    }

    private fun configMenu(configRef: AtomicReference<GameConfig>) {
        while (true) {
            val cfg = configRef.get()
            println("\n===== CONFIGURACIÓN =====")
            println("1. Tamaño tablero (actual: ${cfg.boardSize}x${cfg.boardSize})")
            println("2. Número de partidas (al mejor de) (actual: ${cfg.rounds})")
            println("3. Dificultad IA PVE (actual: ${cfg.difficulty})")
            println("4. Tiempo por movimiento (seg) (actual: ${cfg.timeLimit})")
            println("5. Modo Turbo (actual: ${if (cfg.turbo) "ON" else "OFF"})")
            println("6. Volver al menú")
            print("Elige opción: ")

            when (readLine()?.trim()) {
                "1" -> cfg.boardSize = askBoardSize()
                "2" -> cfg.rounds = askRounds()
                "3" -> cfg.difficulty = askDifficulty()
                "4" -> cfg.timeLimit = askTimeLimit(cfg.turbo)
                "5" -> {
                    cfg.turbo = !cfg.turbo
                    if (cfg.turbo && cfg.timeLimit in 1..9) cfg.timeLimit = 10
                }
                "6" -> {
                    configRef.set(cfg)
                    println()
                    return
                }
                else -> println("Opción no válida.")
            }
            configRef.set(cfg)
        }
    }

    private fun askBoardSize(): Int {
        while (true) {
            println("\nTamaño tablero:")
            println("1. 3x3")
            println("2. 4x4")
            println("3. 5x5")
            print("Elige: ")
            return when (readLine()?.trim()) {
                "1" -> 3
                "2" -> 4
                "3" -> 5
                else -> {
                    println("Opción no válida.")
                    continue
                }
            }
        }
    }

    private fun askRounds(): Int {
        while (true) {
            println("\nMejor de:")
            println("1. 3")
            println("2. 5")
            println("3. 7")
            print("Elige: ")
            return when (readLine()?.trim()) {
                "1" -> 3
                "2" -> 5
                "3" -> 7
                else -> {
                    println("Opción no válida.")
                    continue
                }
            }
        }
    }

    private fun askDifficulty(): String {
        while (true) {
            println("\nDificultad IA:")
            println("1. EASY")
            println("2. MEDIUM")
            println("3. HARD")
            print("Elige: ")
            return when (readLine()?.trim()) {
                "1" -> "EASY"
                "2" -> "MEDIUM"
                "3" -> "HARD"
                else -> {
                    println("Opción no válida.")
                    continue
                }
            }
        }
    }

    private fun askTimeLimit(turbo: Boolean): Int {
        while (true) {
            print("\nTiempo por movimiento en segundos (0 = sin límite): ")
            val v = readLine()?.trim()?.toIntOrNull()
            if (v == null || v < 0) {
                println("Valor inválido.")
                continue
            }
            if (turbo && v in 1..9) {
                println("Con Turbo ON, el tiempo mínimo es 10 segundos (o 0 sin límite).")
                continue
            }
            return v
        }
    }
}