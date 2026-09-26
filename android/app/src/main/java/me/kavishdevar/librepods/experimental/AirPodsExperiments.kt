package me.kavishdevar.librepods.experimental

import android.Manifest
import android.content.pm.PackageManager
import android.media.AudioManager
import android.os.SystemClock
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import me.kavishdevar.librepods.audio.AacEldDecoder
import me.kavishdevar.librepods.audio.WaveSample
import me.kavishdevar.librepods.bluetooth.AACPManager.Companion.ControlCommandIdentifiers
import me.kavishdevar.librepods.bluetooth.BluetoothConnectionManager
import me.kavishdevar.librepods.bluetooth.HeartRateSample
import me.kavishdevar.librepods.data.Capability
import me.kavishdevar.librepods.services.AirPodsService
import java.io.ByteArrayOutputStream
import java.io.File

/** Explicit foreground experiments only. No automatic recording or Health Connect export. */
class AirPodsExperiments(private val service: AirPodsService) {
    data class State(
        val heartActive: Boolean = false,
        val heartStatus: String = "Not started",
        val bpm: Int? = null,
        val heartSamples: Int = 0,
        val heartDiagnostics: String = "",
        val micActive: Boolean = false,
        val micStatus: String = "Not started",
        val pcmBytes: Int = 0,
        val sampleRate: Int = 0,
        val sampleFile: File? = null
    )
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val mutableState = MutableStateFlow(State())
    val state = mutableState.asStateFlow()
    private val manager get() = service.aacpManager
    @Volatile private var heartGeneration = 0
    private var heartJob: Job? = null
    private var micJob: Job? = null
    private var gate = HeartRateGate()
    private var lastHeartSampleAt = 0L
    private var heartBefore: Byte? = null
    private var motionProbeService: Int? = null
    private var conversationBefore: Byte? = null
    private var sessionSocket: android.bluetooth.BluetoothSocket? = null
    @Volatile private var decoder: AacEldDecoder? = null
    @Volatile private var generation = 0
    private var closed = false
    private val pcmLock = Any()
    private var pcm = ByteArrayOutputStream()
    private var pcmRate = 0
    private var pcmChannels = 0
    private val startMicPacket = byteArrayOf(4, 0, 4, 0, 0x58, 0, 0, 0, 9, 0, 0, 1, 0x82.toByte(), 0, 0, 0, 4, 0x96.toByte(), 0)
    private val stopMicPacket = byteArrayOf(4, 0, 4, 0, 0x58, 0, 0, 0, 2, 0, 3, 1)
    private fun connected() = BluetoothConnectionManager.aacpSocket?.isConnected == true
    private fun sameConnection() = connected() && sessionSocket === BluetoothConnectionManager.aacpSocket

    fun startHeartRate() = scope.launch {
        if (closed) return@launch
        if (!connected() || service.airpodsInstance?.model?.capabilities?.contains(Capability.HRM) != true) {
            mutableState.update { it.copy(heartStatus = "Connect supported AirPods Pro 3 before starting.") }; return@launch
        }
        if (state.value.micActive || state.value.heartActive) return@launch
        val token = ++heartGeneration
        sessionSocket = BluetoothConnectionManager.aacpSocket
        val baseline = manager.heartRateDiagnostics()
        heartBefore = manager.getControlCommandStatus(ControlCommandIdentifiers.HRM_STATE)?.value?.firstOrNull()
        mutableState.update { it.copy(heartActive = true, bpm = null, heartSamples = 0, heartDiagnostics = "Initializing sensor channel…", heartStatus = "Starting — wear at least one earbud") }
        heartJob = scope.launch {
            try {
                // A freshly connected RTBuddy channel can remain silent for roughly 30 seconds.
                // Bounded retries allow that startup window without rebuilding the user's connection.
                repeat(3) { attempt ->
                    if (!sameConnection()) error("AirPods disconnected")
                    gate = HeartRateGate()
                    lastHeartSampleAt = 0L
                    mutableState.update { it.copy(bpm = null, heartStatus = "Starting, attempt ${attempt + 1}/3 — keep an earbud in your ear") }
                    for (send in listOf(manager::sendHeartRateConnectService0, manager::sendHeartRateCapabilitiesService0, manager::sendHeartRateConnectService4, manager::sendHeartRateCapabilitiesService4)) {
                        check(withContext(Dispatchers.IO) { sendHeart(token, send) }) { "RTBuddy initialization failed" }
                        delay(220)
                    }
                    // After the baseline attempt, test whether warming the advertised motion
                    // service wakes RTBuddy. Never guess service IDs or stop existing head tracking.
                    if (attempt > 0 && !service.isHeadTrackingActive) {
                        val before = manager.heartRateDiagnostics()
                        val motionId = before.motionServiceId
                        if (motionId != null && motionId != before.serviceId) {
                            mutableState.update { it.copy(heartStatus = "Waking sensor channel — attempt ${attempt + 1}/3") }
                            if (withContext(Dispatchers.IO) { sendHeart(token) {
                                manager.sendMotionProbeStart(motionId).also { sent ->
                                    if (sent) motionProbeService = motionId
                                }
                            } }) {
                                val warmupDeadline = SystemClock.elapsedRealtime() + 8_000
                                while (sameConnection() && SystemClock.elapsedRealtime() < warmupDeadline &&
                                    manager.heartRateDiagnostics().otherSensorDataFrames <= before.otherSensorDataFrames) delay(250)
                                stopMotionProbe()
                            }
                        }
                    }
                    check(manager.awaitHeartRateServiceResolution()) { "No usable heart-rate service advertised" }
                    check(withContext(Dispatchers.IO) { sendHeart(token) { manager.sendControlCommand(ControlCommandIdentifiers.HRM_STATE.value, true) } }) { "Heart-rate enable failed" }
                    delay(120)
                    check(withContext(Dispatchers.IO) { sendHeart(token) { manager.sendHeartRateStartFrame() } }) { "Heart-rate stream request failed" }
                    val deadline = SystemClock.elapsedRealtime() + 30_000
                    while (sameConnection() && (lastHeartSampleAt > 0 || SystemClock.elapsedRealtime() < deadline)) {
                        delay(500)
                        val diagnostic = manager.heartRateDiagnostics()
                        val text = "Service: ${diagnostic.serviceId ?: "unavailable"} (${if (diagnostic.discovered) "advertised" else "fallback"})\n" +
                            "Sensor packets: ${diagnostic.rtBuddyChunks - baseline.rtBuddyChunks}; parsed samples: ${diagnostic.parsedSamples - baseline.parsedSamples}; rejected frames: ${diagnostic.rejectedFrames - baseline.rejectedFrames}\n" +
                            "Heart-rate acknowledgements: ${diagnostic.acknowledgements - baseline.acknowledgements}; other sensor data frames: ${diagnostic.otherSensorDataFrames - baseline.otherSensorDataFrames}; motion service: ${diagnostic.motionServiceId ?: "not advertised"}\n" +
                            "This attempt: warm-up ${gate.warmingUp}, low quality ${gate.lowQuality}, duplicates ${gate.duplicates}\n" +
                            "Last parser rejection: ${if (diagnostic.rejectedFrames > baseline.rejectedFrames) diagnostic.lastRejection else "none"}"
                        mutableState.update { it.copy(heartDiagnostics = text) }
                        if (lastHeartSampleAt > 0 && SystemClock.elapsedRealtime() - lastHeartSampleAt > 4_000) {
                            mutableState.update { it.copy(bpm = null, heartStatus = "Stream stalled — retrying") }
                            break
                        }
                    }
                    if (sameConnection()) withContext(Dispatchers.IO) { sendHeart(token) { manager.sendHeartRateStopFrame() } }
                    delay(1000)
                }
                val diagnostic = manager.heartRateDiagnostics()
                val reason = when {
                    diagnostic.parsedSamples > baseline.parsedSamples -> "Sensor data arrived, but no sustained quality reading. Reseat the earbud and retry."
                    diagnostic.acknowledgements > baseline.acknowledgements -> "Start acknowledged, but no heart-rate measurements. Sensor activation remains unresolved."
                    else -> "No heart-rate acknowledgement or measurements. Reconnect and retry."
                }
                mutableState.update { it.copy(heartStatus = reason) }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { mutableState.update { it.copy(heartStatus = error.message ?: "Heart-rate test failed") } }
            finally { if (token == heartGeneration) { stopHeartCommands(); mutableState.update { it.copy(heartActive = false, bpm = null) } } }
        }
    }

    fun onHeartRate(sample: HeartRateSample) {
        scope.launch {
            if (!state.value.heartActive || !sameConnection()) return@launch
            if (!gate.accept(sample)) {
                if (lastHeartSampleAt == 0L) {
                    mutableState.update { it.copy(heartStatus = "Calibrating — waiting for a stable signal") }
                }
                return@launch
            }
            lastHeartSampleAt = sample.receivedAtElapsedRealtime
            mutableState.update { it.copy(bpm = sample.bpm, heartSamples = it.heartSamples + 1, heartStatus = "Live experimental reading") }
        }
    }
    private fun stopMotionProbe() = synchronized(manager) {
        val id = motionProbeService ?: return@synchronized
        if (sameConnection() && !service.isHeadTrackingActive) manager.sendMotionProbeStop(id)
        motionProbeService = null
    }

    private fun stopHeartCommands() {
        stopMotionProbe()
        if (sameConnection()) {
            manager.sendHeartRateStopFrame()
            manager.sendControlCommand(ControlCommandIdentifiers.HRM_STATE.value, byteArrayOf(heartBefore ?: 2))
        }
        heartBefore = null
    }
    private fun sendHeart(token: Int, send: () -> Boolean): Boolean = synchronized(manager) {
        token == heartGeneration && sameConnection() && send()
    }
    private fun sendMic(token: Int, send: () -> Boolean): Boolean = synchronized(manager) {
        token == generation && sameConnection() && send()
    }
    fun stopHeartRate() {
        heartGeneration++
        if (state.value.heartActive) stopHeartCommands()
        heartJob?.cancel(); heartJob = null
        mutableState.update { it.copy(heartActive = false, bpm = null, heartStatus = "Stopped") }
    }

    fun startMicrophoneTest() = scope.launch {
        if (closed || state.value.micActive || state.value.heartActive) return@launch
        if (ContextCompat.checkSelfPermission(service, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            mutableState.update { it.copy(micStatus = "Microphone permission is required for the recording test.") }; return@launch
        }
        if (!connected()) { mutableState.update { it.copy(micStatus = "Connect AirPods first.") }; return@launch }
        if (service.getSystemService(AudioManager::class.java).mode != AudioManager.MODE_NORMAL) {
            mutableState.update { it.copy(micStatus = "End the current call before running this microphone test.") }; return@launch
        }
        val previous = manager.getControlCommandStatus(ControlCommandIdentifiers.CONVERSATION_DETECT_CONFIG)?.value?.firstOrNull()
        if (previous != 1.toByte() && previous != 2.toByte()) {
            mutableState.update { it.copy(micStatus = "Waiting for conversation-awareness settings. Reconnect and try again.") }; return@launch
        }
        generation++
        val token = generation
        conversationBefore = previous
        sessionSocket = BluetoothConnectionManager.aacpSocket
        synchronized(pcmLock) { pcm = ByteArrayOutputStream(); pcmRate = 0; pcmChannels = 0 }
        state.value.sampleFile?.delete()
        mutableState.update { it.copy(micActive = true, pcmBytes = 0, sampleRate = 0, sampleFile = null, micStatus = "Starting a 10-second recording test…") }
        micJob = scope.launch {
            try {
                val candidate = AacEldDecoder(object : AacEldDecoder.Listener {
                    override fun onPcmData(data: ByteArray, sampleRate: Int, channelCount: Int) {
                        if (token != generation || data.isEmpty()) return
                        synchronized(pcmLock) {
                            if (token != generation) return
                            if (pcmRate != 0 && (pcmRate != sampleRate || pcmChannels != channelCount)) error("PCM format changed during capture")
                            pcmRate = sampleRate; pcmChannels = channelCount
                            check(pcm.size() + data.size <= 2 * 1024 * 1024) { "Recording size limit exceeded" }
                            pcm.write(data)
                        }
                    }
                    override fun onDecoderError(message: String, cause: Throwable?) {
                        scope.launch {
                            if (token == generation) {
                                stopMicrophone(false)
                                mutableState.update { it.copy(micStatus = message, sampleFile = null) }
                            }
                        }
                    }
                })
                decoder = candidate
                check(withContext(Dispatchers.IO) { candidate.start() }) { "Android's AAC-ELD decoder is unavailable" }
                ensureActive()
                check(withContext(Dispatchers.IO) { sendMic(token) { manager.sendControlCommand(ControlCommandIdentifiers.CONVERSATION_DETECT_CONFIG.value, false) } }) { "Could not pause conversation awareness" }
                delay(150)
                check(withContext(Dispatchers.IO) { sendMic(token) { manager.sendPacket(startMicPacket) } }) { "Microphone stream request failed" }
                repeat(40) {
                    delay(250)
                    check(sameConnection()) { "AirPods disconnected" }
                    check(service.getSystemService(AudioManager::class.java).mode == AudioManager.MODE_NORMAL) { "Call started; microphone test stopped" }
                    val captured = synchronized(pcmLock) { pcm.size() to pcmRate }
                    mutableState.update { it.copy(pcmBytes = captured.first, sampleRate = captured.second, micStatus = if (captured.first > 0) "Receiving decoded microphone audio" else "Waiting for microphone audio…") }
                }
                stopMicrophone(true)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) {
                if (token == generation) {
                    stopMicrophone(false)
                    mutableState.update { it.copy(micStatus = error.message ?: "Microphone test failed") }
                }
            }
        }
    }

    fun onAudioPacket(packet: ByteArray) { decoder?.offer(packet) }

    fun stopMicrophone(save: Boolean = true) {
        generation++
        micJob?.cancel(); micJob = null
        val old = decoder; decoder = null; old?.close()
        if (conversationBefore != null && sameConnection()) {
            manager.sendPacket(stopMicPacket)
            manager.sendControlCommand(ControlCommandIdentifiers.CONVERSATION_DETECT_CONFIG.value, byteArrayOf(conversationBefore!!))
        }
        conversationBefore = null
        val file = if (save) runCatching {
            synchronized(pcmLock) {
                if (pcm.size() == 0) null else File(service.cacheDir, "mic-tests/airpods-test.wav").also {
                    it.parentFile?.mkdirs(); it.writeBytes(WaveSample.encode(pcm.toByteArray(), pcmRate, pcmChannels))
                }
            }
        }.getOrNull() else null
        synchronized(pcmLock) { pcm.reset() }
        mutableState.update { it.copy(micActive = false, sampleFile = file, micStatus = if (file != null) "Test recording ready to play or share" else "Stopped — no recording saved") }
    }

    fun stopAll() { stopHeartRate(); if (state.value.micActive) stopMicrophone(false) }
    fun disconnected() = scope.launch { stopAll(); mutableState.update { it.copy(bpm = null, heartStatus = "Disconnected", micStatus = "Disconnected") } }
    fun close() { closed = true; stopAll(); scope.cancel() }
}
