package me.kavishdevar.librepods.finding

/** Negotiated OOB parameters, never a guessed configuration derived from a BLE address. */
class UwbSessionProfile private constructor(
    val sessionId: Int,
    val controller: Boolean,
    val localAddress: ByteArray,
    val peerAddress: ByteArray,
    val channel: Int,
    val preamble: Int,
    val configId: Int,
    val key: ByteArray,
    val updateRate: Int,
    val slotDuration: Int,
) {
    // Do not use a data class: generated toString would expose session material.
    override fun toString() = "UwbSessionProfile(redacted)"
    fun destroy() { key.fill(0) }

    companion object {
        const val MAX_BYTES = 4096
        /** Strict UTF-8 key=value file; no Java Properties escaping, continuation or defaults. */
        fun parse(text: String): UwbSessionProfile {
            require(text.toByteArray(Charsets.UTF_8).size <= MAX_BYTES) { "Session file is too large." }
            val fields = mutableMapOf<String, String>()
            text.lineSequence().forEach { line ->
                val trimmed = line.trim()
                if (trimmed.isNotEmpty() && !trimmed.startsWith('#')) {
                    val parts = trimmed.split('=', limit = 2)
                    require(parts.size == 2 && fields.put(parts[0].trim(), parts[1].trim()) == null) { "Malformed or duplicate session field." }
                }
            }
            val names = setOf("version", "sessionId", "role", "localAddress", "peerAddress", "channel", "preamble", "configId", "sessionKey", "updateRate", "slotDuration")
            require(fields.keys == names && fields["version"] == "1") { "Unsupported or incomplete session format." }
            fun number(name: String) = fields.getValue(name).toIntOrNull()
                ?: throw IllegalArgumentException("Invalid $name.")
            fun hex(name: String): ByteArray {
                val value = fields.getValue(name)
                require(value.length in 4..64 && value.length % 2 == 0 && value.all { it in "0123456789abcdefABCDEF" }) { "Invalid $name encoding." }
                return value.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
            }
            val local = hex("localAddress")
            val peer = hex("peerAddress")
            require(local.size in setOf(2, 8) && local.size == peer.size && !local.contentEquals(peer)) { "UWB addresses must be distinct and have matching length (2 or 8 bytes)." }
            val config = number("configId")
            require(config in setOf(1, 3)) { "Only static/provisioned unicast DS-TWR is supported." }
            val key = hex("sessionKey")
            require(if (config == 1) key.size == 8 else key.size in setOf(16, 32)) { "Incorrect session-key length." }
            val channel = number("channel")
            val preamble = number("preamble")
            val rate = number("updateRate")
            val slot = number("slotDuration")
            require(channel in setOf(5, 9) && preamble in 9..12 && rate in 1..3 && slot in 1..2) { "Unsupported radio parameters." }
            val role = fields.getValue("role")
            require(role in setOf("controller", "controlee")) { "Invalid role." }
            return UwbSessionProfile(number("sessionId"), role == "controller", local, peer, channel, preamble, config, key, rate, slot)
        }
    }
}
