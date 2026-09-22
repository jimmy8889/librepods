package me.kavishdevar.librepods.experimental

import me.kavishdevar.librepods.bluetooth.HeartRateSample

/** Conservative experimental warm-up policy, not a medically validated confidence threshold. */
internal class HeartRateGate {
    private var count = 0
    private var lastCounter = -1
    var duplicates = 0
        private set
    var warmingUp = 0
        private set
    var lowQuality = 0
        private set
    fun accept(sample: HeartRateSample): Boolean {
        // The envelope sequence identifies protocol messages, not individual sensor samples.
        // The payload counter is an unsigned byte and may wrap from 255 to 0.
        if (sample.sampleCounter == lastCounter) { duplicates++; return false }
        lastCounter = sample.sampleCounter
        count++
        if (count <= 4) { warmingUp++; return false }
        if (sample.quality < 0x80) { lowQuality++; return false }
        return true
    }
}
