package me.kavishdevar.librepods.audio

import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.*
import org.junit.Test

class WaveSampleTest {
    @Test fun preservesActualCodecRateAndPcm() {
        val pcm = byteArrayOf(1,2,3,4)
        val wav = WaveSample.encode(pcm,64000,1)
        val header = ByteBuffer.wrap(wav).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals("RIFF",String(wav,0,4))
        assertEquals(wav.size-8,header.getInt(4))
        assertEquals(64000,header.getInt(24))
        assertEquals(128000,header.getInt(28))
        assertEquals(pcm.size,header.getInt(40))
        assertArrayEquals(pcm,wav.copyOfRange(44,wav.size))
    }
    @Test(expected=IllegalArgumentException::class) fun rejectsPartialPcmFrame() { WaveSample.encode(byteArrayOf(1),48000,1) }
    @Test(expected=IllegalArgumentException::class) fun boundsRecordingSize() { WaveSample.encode(ByteArray(2*1024*1024+2),48000,1) }
}
