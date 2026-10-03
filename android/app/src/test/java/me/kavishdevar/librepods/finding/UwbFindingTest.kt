package me.kavishdevar.librepods.finding

import org.junit.Assert.*
import org.junit.Test

class UwbFindingTest {
    private val profile = """
        version=1
        sessionId=42
        role=controller
        localAddress=1234
        peerAddress=5678
        channel=9
        preamble=10
        configId=1
        sessionKey=0102030405060708
        updateRate=1
        slotDuration=2
    """.trimIndent()

    @Test fun validatesAndRedactsNegotiatedParameters() {
        val value = UwbSessionProfile.parse(profile)
        assertEquals(42, value.sessionId)
        assertArrayEquals(byteArrayOf(0x12, 0x34), value.localAddress)
        assertEquals("UwbSessionProfile(redacted)", value.toString())
        value.destroy()
        assertTrue(value.key.all { it == 0.toByte() })
    }
    @Test fun rejectsIncompleteAmbiguousAndIncompatibleProfiles() {
        listOf(
            profile.replace("peerAddress=5678", "peerAddress=1234"),
            profile.replace("peerAddress=5678", "peerAddress=12345678"),
            profile.replace("channel=9", "channel=6"),
            profile.replace("configId=1", "configId=3"),
            profile.replace("sessionKey=0102030405060708", "sessionKey=0011"),
            profile.replace("sessionId=42", "sessionId=4294967296"),
            profile + "\nchannel=5", profile + "\nunknown=x",
            profile.replace("role=controller", "role=guess"),
            profile.replace("slotDuration=2", "slotDuration=0"),
            "x".repeat(4097), profile.replace("version=1", "version=2"),
        ).forEach { invalid ->
            assertThrows(IllegalArgumentException::class.java) { UwbSessionProfile.parse(invalid) }
        }
        val provisioned = UwbSessionProfile.parse(profile.replace("configId=1", "configId=3")
            .replace("sessionKey=0102030405060708", "sessionKey=00112233445566778899aabbccddeeff"))
        assertEquals(16, provisioned.key.size)
    }
    @Test fun staleLowConfidenceOutOfOrderAndNonfiniteReadingsAreHidden() {
        fun reading(metres: Double? = 1.5, confidence: Int = 2, at: Long = 5000, previous: Long = -1) =
            UwbReading.accept(metres, confidence, Double.NaN, 1.0, at, 5000, previous)
        assertEquals(1.5, reading()!!.metres, 0.0)
        assertNull(reading()!!.azimuthRadians)
        assertEquals(1.0, reading()!!.elevationRadians!!, 0.0)
        assertNull(reading(metres = null)); assertNull(reading(metres = Double.NaN))
        assertNull(reading(metres = Double.POSITIVE_INFINITY)); assertNull(reading(metres = -0.1))
        assertNull(reading(confidence = 0)); assertNull(reading(confidence = 3))
        assertNull(reading(at = 2999)); assertNull(reading(at = 5001))
        assertNull(reading(previous = 5000)); assertNull(reading(previous = 6000))
        assertNotNull(reading(metres = 0.0, confidence = 1, at = 3000))
        val angles = UwbReading.accept(1.0, 2, 4.0, 2.0, 5000, 5000, -1)!!
        assertNull(angles.azimuthRadians); assertNull(angles.elevationRadians)
    }
}
