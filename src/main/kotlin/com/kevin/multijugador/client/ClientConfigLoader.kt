package com.kevin.multijugador.client

import java.io.InputStream
import java.util.Properties

data class ClientServerConfig(val host: String, val port: Int)

object ClientConfigLoader {
    fun load(resourceName: String = "server.properties"): ClientServerConfig {
        val props = Properties()
        val input: InputStream = Thread.currentThread().contextClassLoader
            .getResourceAsStream(resourceName)
            ?: throw IllegalStateException("No se encontró $resourceName en resources")

        input.use { props.load(it) }

        val host = props.getProperty("server.host") ?: "localhost"
        val port = (props.getProperty("server.port") ?: "5678").toInt()
        return ClientServerConfig(host, port)
    }
}