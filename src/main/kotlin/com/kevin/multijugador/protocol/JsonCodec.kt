package com.kevin.multijugador.protocol

object JsonCodec {

    private val typeRegex = """"type"\s*:\s*"([^"]+)"""".toRegex()

    fun encode(type: String, payloadJson: String): String =
        """{"type":"$type","payload":$payloadJson}"""

    fun decode(line: String): Envelope? {
        val type = typeRegex.find(line)?.groupValues?.getOrNull(1) ?: return null

        val payload = extractPayload(line) ?: "null"
        return Envelope(type, payload)
    }

    private fun extractPayload(json: String): String? {
        val key = """"payload":"""
        val idx = json.indexOf(key)
        if (idx == -1) return null

        var payload = json.substring(idx + key.length).trim()

        if (payload.endsWith("}")) payload = payload.dropLast(1).trim()

        return payload
    }
}