package me.kavishdevar.librepods.finding

/** Only platform UWB time-of-flight measurements can enter this model. */
data class UwbReading(val metres: Double, val azimuthRadians: Double?, val elevationRadians: Double?, val atMillis: Long) {
    companion object {
        const val FRESH_MS = 2000L
        fun accept(metres: Double?, confidence: Int, azimuth: Double?, elevation: Double?, at: Long, now: Long, previous: Long): UwbReading? {
            if (metres == null || !metres.isFinite() || metres < 0 || confidence !in 1..2) return null
            if (at < 0 || at > now || now - at > FRESH_MS || at <= previous) return null
            return UwbReading(metres, azimuth?.takeIf { it.isFinite() && it in -Math.PI..Math.PI },
                elevation?.takeIf { it.isFinite() && it in -Math.PI / 2..Math.PI / 2 }, at)
        }
    }
}
