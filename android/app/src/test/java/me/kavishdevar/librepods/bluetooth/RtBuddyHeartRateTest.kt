package me.kavishdevar.librepods.bluetooth

import org.junit.Assert.*
import org.junit.Test
import java.util.Random

class RtBuddyHeartRateTest {
    private fun decoder() = RtBuddyHeartRateDecoder({ 1234L }, { 5678L })
    private fun frame(body: ByteArray): ByteArray = byteArrayOf(4,0,4,0,0x17,0,0,0,0x10,0,body.size.toByte(),(body.size ushr 8).toByte()) + body
    private fun sample(service: Int = 19, bpm: Int = 72, logType: Int = 1): ByteArray {
        val payload = ByteArray(18).apply { this[1] = bpm.toByte(); this[2] = 200.toByte(); this[15] = 0x10 }
        val command = byteArrayOf(8,service.toByte(),0x1a,18) + payload
        return frame(byteArrayOf(8,1,0x10,logType.toByte(),0x2a,command.size.toByte()) + command)
    }
    private fun metadata(service: Int, name: String): ByteArray {
        val record = byteArrayOf(8,service.toByte(),0x12,name.length.toByte()) + name.toByteArray()
        return frame(byteArrayOf(0x2a,record.size.toByte()) + record)
    }
    @Test fun readsValidatedSampleWithInjectedClock() {
        val result = decoder().feed(sample())
        assertEquals(72, result.samples.single().bpm)
        assertEquals(200, result.samples.single().quality)
        assertEquals(1234L, result.samples.single().receivedAtMillis)
        assertTrue(result.suppressRawLogging)
    }
    @Test fun reassemblesEverySplitAndMultipleFrames() {
        val packet = sample()
        for (split in 1 until packet.size) {
            val parser = decoder()
            assertTrue(parser.feed(packet.copyOfRange(0,split)).samples.isEmpty())
            assertEquals(72, parser.feed(packet.copyOfRange(split,packet.size)).samples.single().bpm)
        }
        assertEquals(2, decoder().feed(packet + packet).samples.size)
    }
    @Test fun rejectsAcknowledgmentsInvalidStatusAndOutOfRangeReadings() {
        assertTrue(decoder().feed(frame(byteArrayOf(8,1,0x4a,2,8,19))).samples.isEmpty())
        assertTrue(decoder().feed(sample(logType=2)).samples.isEmpty())
        assertTrue(decoder().feed(sample(bpm=255)).samples.isEmpty())
        val badTail = sample().apply { this[lastIndex] = 0x7f }
        assertTrue(decoder().feed(badTail).samples.isEmpty())
    }
    @Test fun discoversServiceAndNeverUsesKnownNonHeartFallback() {
        val parser = decoder()
        parser.feed(metadata(19,"HostLibHID"))
        assertNull(parser.heartRateServiceIdForControl())
        assertTrue(parser.feed(sample()).samples.isEmpty())
        parser.feed(metadata(23,"HeartRateService"))
        assertEquals(23, parser.heartRateServiceIdForControl())
        assertEquals(72, parser.feed(sample(service=23)).samples.single().bpm)
        assertTrue(parser.feed(sample()).samples.isEmpty())
    }
    @Test fun stopRemainsPinnedToStartedServiceAndFailedStopCanRetry() {
        val parser = decoder()
        val session = RtBuddyHeartRateControlSession(parser)
        assertFalse(session.sendStop { error("Must not stop before start") }.attempted)
        assertEquals(19,session.sendStart { true }.serviceId)
        parser.feed(metadata(23,"HeartRateService"))
        assertEquals(19,session.sendStop { false }.serviceId)
        assertEquals(19,session.sendStop { true }.serviceId)
        assertEquals(23,session.sendStart { true }.serviceId)
    }
    @Test fun malformedInputIsBoundedAndDoesNotCrash() {
        val random = Random(5)
        repeat(2000) {
            val body = ByteArray(random.nextInt(256)).also { random.nextBytes(it) }
            decoder().feed(frame(body))
        }
        val oversized = frame(byteArrayOf()).apply { this[10] = -1; this[11] = -1 }
        val parser = decoder()
        assertTrue(parser.feed(oversized).samples.isEmpty())
        assertEquals(72, parser.feed(sample()).samples.single().bpm)
    }
}
