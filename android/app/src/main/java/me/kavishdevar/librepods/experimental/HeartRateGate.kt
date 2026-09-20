package me.kavishdevar.librepods.experimental

import me.kavishdevar.librepods.bluetooth.HeartRateSample

/** Conservative experimental warm-up policy, not a medically validated confidence threshold. */
internal class HeartRateGate {
    private var count = 0
    private var lastSequence = -1
    fun accept(sample: HeartRateSample): Boolean {
        if (sample.sequence == lastSequence) return false
        lastSequence = sample.sequence
        count++
        return count > 4 && sample.quality >= 0x80
    }
}
