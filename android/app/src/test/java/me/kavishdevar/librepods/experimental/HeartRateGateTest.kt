package me.kavishdevar.librepods.experimental

import me.kavishdevar.librepods.bluetooth.HeartRateSample
import org.junit.Assert.*
import org.junit.Test

class HeartRateGateTest {
    private fun sample(sequence: Int, quality: Int=200) = HeartRateSample(72,sequence,quality,0,0)
    @Test fun rejectsWarmupLowQualityAndDuplicatePackets() {
        val gate = HeartRateGate()
        repeat(4) { assertFalse(gate.accept(sample(it))) }
        assertFalse(gate.accept(sample(4,20)))
        assertTrue(gate.accept(sample(5)))
        assertFalse(gate.accept(sample(5)))
        assertTrue(gate.accept(sample(6)))
    }
    @Test fun acceptsAdvancingPayloadCountersWithAnUnchangedEnvelopeSequence() {
        val gate = HeartRateGate()
        repeat(4) { assertFalse(gate.accept(sample(9059).copy(sampleCounter=it))) }
        assertTrue(gate.accept(sample(9059).copy(sampleCounter=4)))
        assertTrue(gate.accept(sample(9059).copy(sampleCounter=5)))
        assertFalse(gate.accept(sample(9060).copy(sampleCounter=5)))
        assertEquals(1, gate.duplicates)
    }
    @Test fun acceptsCounterWrapAndKeepsLowQualityHidden() {
        val gate = HeartRateGate()
        repeat(4) { assertFalse(gate.accept(sample(1).copy(sampleCounter=250+it))) }
        assertFalse(gate.accept(sample(1,20).copy(sampleCounter=254)))
        assertTrue(gate.accept(sample(1).copy(sampleCounter=255)))
        assertTrue(gate.accept(sample(1).copy(sampleCounter=0)))
        assertEquals(4, gate.warmingUp)
        assertEquals(1, gate.lowQuality)
    }
}
