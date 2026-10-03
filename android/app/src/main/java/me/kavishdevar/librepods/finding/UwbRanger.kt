package me.kavishdevar.librepods.finding

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.ranging.*
import android.ranging.raw.*
import android.ranging.uwb.*
import androidx.annotation.RequiresApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class UwbState(
    val status: String = "Checking UWB…",
    val available: Boolean = false,
    val active: Boolean = false,
    val reading: UwbReading? = null,
    val capabilities: String = "",
)

interface UwbRanger {
    val state: StateFlow<UwbState>
    fun observe()
    fun start(profile: UwbSessionProfile)
    fun stop()
    fun close()

    companion object {
        fun create(context: Context): UwbRanger {
            val reason = when {
                !context.packageManager.hasSystemFeature(PackageManager.FEATURE_UWB) -> "This phone does not advertise UWB hardware."
                Build.VERSION.SDK_INT < 36 -> "The UWB test requires Android 16 or newer."
                else -> null
            }
            if (Build.VERSION.SDK_INT >= 36 && reason == null) return PlatformUwbRanger(context)
            return object : UwbRanger {
                override val state = MutableStateFlow(UwbState(status = reason ?: "UWB ranging is unavailable."))
                override fun observe() = Unit
                override fun start(profile: UwbSessionProfile) = Unit
                override fun stop() = Unit
                override fun close() = Unit
            }
        }
    }
}

/** API 36 raw UWB only. No BLE RSSI fallback and no Apple case negotiation is assumed. */
@RequiresApi(36)
@SuppressLint("MissingPermission")
private class PlatformUwbRanger(private val context: Context) : UwbRanger {
    private val mutable = MutableStateFlow(UwbState())
    override val state: StateFlow<UwbState> = mutable
    private val manager = context.getSystemService(RangingManager::class.java)
    private val handler = Handler(Looper.getMainLooper())
    private var capabilities: UwbRangingCapabilities? = null
    private var registered = false
    private var session: RangingSession? = null
    private var generation = 0L
    private var lastTimestamp = -1L
    private val stale = Runnable { mutable.value = mutable.value.copy(reading = null, status = "Waiting for a fresh UWB reading…") }
    private val timeout = Runnable { end("UWB test timed out. Import a newly negotiated session before retrying.") }
    private val capabilityCallback = RangingManager.RangingCapabilitiesCallback { result ->
        if (!registered) return@RangingCapabilitiesCallback
        capabilities = result.uwbCapabilities
        val available = result.technologyAvailability[RangingManager.UWB] == RangingCapabilities.ENABLED
        val caps = capabilities
        val detail = caps?.let {
            "Channels: ${it.supportedChannels.joinToString()}; distance: ${it.isDistanceMeasurementSupported}; azimuth: ${it.isAzimuthalAngleSupported}; elevation: ${it.isElevationAngleSupported}."
        }.orEmpty()
        val status = when (result.technologyAvailability[RangingManager.UWB]) {
            RangingCapabilities.ENABLED -> "UWB is available. AirPods case session setup is not implemented."
            RangingCapabilities.DISABLED_USER -> "Turn on UWB in phone settings."
            RangingCapabilities.DISABLED_REGULATORY -> "UWB is unavailable under the current regional restrictions."
            RangingCapabilities.DISABLED_USER_RESTRICTIONS -> "Phone policy prevents UWB ranging."
            else -> "UWB ranging is unavailable on this phone."
        }
        if (!available && session != null) end(status)
        mutable.value = mutable.value.copy(available = available, capabilities = detail,
            status = if (available && mutable.value.active) mutable.value.status else status)
    }

    override fun observe() {
        if (registered) return
        if (context.checkSelfPermission(Manifest.permission.RANGING) != PackageManager.PERMISSION_GRANTED) {
            mutable.value = UwbState(status = "Nearby devices permission is needed to check UWB."); return
        }
        if (manager == null) { mutable.value = UwbState(status = "The phone's ranging service is unavailable."); return }
        registered = true
        runCatching { manager.registerCapabilitiesCallback(context.mainExecutor, capabilityCallback) }
            .onFailure { registered = false; mutable.value = UwbState(status = "Could not access the phone's UWB service.") }
    }

    override fun start(profile: UwbSessionProfile) {
        stop()
        val caps = capabilities
        if (!registered || !mutable.value.available || caps == null) return
        if (context.checkSelfPermission(Manifest.permission.RANGING) != PackageManager.PERMISSION_GRANTED) {
            end("Nearby devices permission was revoked."); return
        }
        if (!caps.isDistanceMeasurementSupported || profile.channel !in caps.supportedChannels ||
            profile.preamble !in caps.supportedPreambleIndexes || profile.configId !in caps.supportedConfigIds ||
            profile.updateRate !in caps.supportedRangingUpdateRates || profile.slotDuration !in caps.supportedSlotDurations) {
            end("The negotiated session is incompatible with this phone's UWB capabilities."); return
        }
        val token = ++generation
        val peer = RangingDevice.Builder().build()
        mutable.value = mutable.value.copy(active = true, reading = null, status = "Opening the negotiated UWB session…")
        runCatching {
            val params = UwbRangingParams.Builder(profile.sessionId, profile.configId,
                UwbAddress.fromBytes(profile.localAddress), UwbAddress.fromBytes(profile.peerAddress))
                .setComplexChannel(UwbComplexChannel.Builder().setChannel(profile.channel).setPreambleIndex(profile.preamble).build())
                .setSessionKeyInfo(profile.key.copyOf()).setRangingUpdateRate(profile.updateRate)
                .setSlotDuration(profile.slotDuration).build()
            val device = RawRangingDevice.Builder().setRangingDevice(peer).setUwbRangingParams(params).build()
            val config: RangingConfig = if (profile.controller) RawInitiatorRangingConfig.Builder().addRawRangingDevice(device).build()
                else RawResponderRangingConfig.Builder().setRawRangingDevice(device).build()
            val preference = RangingPreference.Builder(
                if (profile.controller) RangingPreference.DEVICE_ROLE_INITIATOR else RangingPreference.DEVICE_ROLE_RESPONDER, config)
                .setSessionConfig(SessionConfig.Builder().setAngleOfArrivalNeeded(caps.isAzimuthalAngleSupported || caps.isElevationAngleSupported)
                    .setDataNotificationConfig(DataNotificationConfig.Builder().setNotificationConfigType(DataNotificationConfig.NOTIFICATION_CONFIG_ENABLE).build()).build()).build()
            val callback = object : RangingSession.Callback {
                private fun current() = token == generation && mutable.value.active
                override fun onOpened() { if (current()) mutable.value = mutable.value.copy(status = "Session open; waiting for the peer.") }
                override fun onOpenFailed(reason: Int) { if (current()) end("UWB session could not open (reason $reason).") }
                override fun onClosed(reason: Int) { if (current()) end("UWB session closed (reason $reason).") }
                override fun onStarted(device: RangingDevice, technology: Int) {
                    if (current() && device == peer && technology == RangingManager.UWB) mutable.value = mutable.value.copy(status = "UWB started; waiting for a valid measurement.")
                }
                override fun onStopped(device: RangingDevice, reason: Int) { if (current() && device == peer) end("UWB peer stopped (reason $reason).") }
                override fun onResults(device: RangingDevice, data: RangingData) {
                    if (!current() || device != peer || data.rangingTechnology != RangingManager.UWB) return
                    val distance = data.distance
                    fun angle(value: RangingMeasurement?) = value?.takeIf { it.confidence in 1..2 }?.measurement
                    val now = SystemClock.elapsedRealtime()
                    val reading = UwbReading.accept(distance?.measurement, distance?.confidence ?: 0,
                        angle(data.azimuth), angle(data.elevation), data.timestampMillis, now, lastTimestamp) ?: return
                    lastTimestamp = reading.atMillis
                    mutable.value = mutable.value.copy(reading = reading, status = "Live UWB measurement from the negotiated peer.")
                    handler.removeCallbacks(stale)
                    handler.postDelayed(stale, UwbReading.FRESH_MS - (now - reading.atMillis))
                }
            }
            session = manager!!.createRangingSession(context.mainExecutor, callback)
            session!!.start(preference)
            handler.postDelayed(timeout, 120_000)
        }.onFailure { end("UWB session failed. Check permissions, radio availability and negotiated parameters.") }
    }

    private fun end(message: String) {
        ++generation
        handler.removeCallbacks(stale); handler.removeCallbacks(timeout)
        val old = session; session = null; lastTimestamp = -1
        mutable.value = mutable.value.copy(active = false, reading = null, status = message)
        runCatching { old?.stop() }; runCatching { old?.close() }
    }
    override fun stop() { if (session != null || mutable.value.active) end("UWB test stopped.") }
    override fun close() {
        stop()
        val wasRegistered = registered; registered = false
        if (wasRegistered) runCatching { manager?.unregisterCapabilitiesCallback(capabilityCallback) }
        capabilities = null
        mutable.value = mutable.value.copy(available = false, reading = null)
        handler.removeCallbacksAndMessages(null)
    }
}
