package me.kavishdevar.librepods.audio

import java.nio.ByteBuffer
import java.nio.ByteOrder

/** A bounded PCM16 WAV for a user-requested microphone test, never a background recording. */
object WaveSample {
    fun encode(pcm: ByteArray, sampleRate: Int, channels: Int): ByteArray {
        require(sampleRate in 8_000..192_000 && channels in 1..2)
        require(pcm.size <= 2 * 1024 * 1024 && pcm.size % (channels * 2) == 0)
        return ByteBuffer.allocate(44 + pcm.size).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray()); putInt(36 + pcm.size); put("WAVEfmt ".toByteArray())
            putInt(16); putShort(1); putShort(channels.toShort()); putInt(sampleRate)
            putInt(sampleRate * channels * 2); putShort((channels * 2).toShort()); putShort(16)
            put("data".toByteArray()); putInt(pcm.size); put(pcm)
        }.array()
    }
}
