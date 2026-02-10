package com.kevin.multijugador.client

object ClientMain {
    @JvmStatic
    fun main(args: Array<String>) {
        val host = "localhost"
        val port = 5678

        val client = TcpClient(host, port)

        try {
            client.connect()
            menuLoop(client)
        } catch (e: Exception) {
            println("Error cliente: ${e.message}")
        } finally {
            client.close()
        }
    }

    private fun menuLoop(client: TcpClient) {
        while (true) {
            println()
            println("===== MENÚ PRINCIPAL =====")
            println("1. Nueva Partida PVP (pendiente)")
            println("2. Nueva Partida PVE (pendiente)")
            println("3. Ver Records")
            println("4. Configuración (pendiente)")
            println("5. Salir")
            print("Elige opción: ")

            when (readlnOrNull()?.trim()) {
                "1" -> println("PVP aún no implementado.")
                "2" -> println("PVE aún no implementado.")
                "3" -> {
                    println("\n--- RECORDS (descargados del servidor) ---")
                    println(client.recordsJson)
                }
                "4" -> println("Configuración aún no implementada.")
                "5" -> {
                    println("Saliendo...")
                    return
                }
                else -> println("Opción no válida.")
            }
        }
    }
}