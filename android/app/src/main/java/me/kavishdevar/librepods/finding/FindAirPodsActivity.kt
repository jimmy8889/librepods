package me.kavishdevar.librepods.finding

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.LocationManager
import android.media.AudioAttributes
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRouting
import android.media.AudioTrack
import android.os.Bundle
import android.os.CancellationSignal
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import java.text.DateFormat
import java.util.Date
import kotlin.math.PI
import kotlin.math.sin

/** Foreground, opt-in finding tools. Does not use Apple's Find My network. */
// This activity extends ComponentActivity directly and never hosts AndroidX fragments.
// The activity-result fragment-version check refers to an unused transitive dependency.
@SuppressLint("MissingPermission", "InvalidFragmentVersionForActivityResult")
class FindAirPodsActivity : ComponentActivity() {
    private val handler = Handler(Looper.getMainLooper())
    private val audio by lazy { getSystemService(AudioManager::class.java) }
    private val adapter by lazy { getSystemService(BluetoothManager::class.java).adapter }
    private val address by lazy { getSharedPreferences("settings", MODE_PRIVATE).getString("mac_address", "").orEmpty() }
    private val prefs by lazy { getSharedPreferences("find_airpods_$address", MODE_PRIVATE) }
    private var message by mutableStateOf("Connect your AirPods in LibrePods first. Nearby scanning only finds discoverable earbuds; a closed case may not respond.")
    private var lastSeen by mutableStateOf("")
    private var scanning by mutableStateOf(false)
    private var ringing by mutableStateOf(false)
    private var confirmSide by mutableStateOf<Int?>(null)
    private var track: AudioTrack? = null
    private var locationRequest: CancellationSignal? = null
    private var receiverRegistered = false
    private var foundDuringScan = false
    private val stopScan = Runnable { finishScan() }
    private val stopTone = Runnable { stopRinging() }
    private val requestBluetooth = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
        if (grants.values.all { it }) scan() else { message = "Nearby devices permission is needed to search." }
    }
    private val requestLocation = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        if (has(Manifest.permission.ACCESS_COARSE_LOCATION)) saveLocation() else { message = "Location permission was not granted. No location was saved." }
    }
    private fun has(permission: String) = ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
    private fun output(): AudioDeviceInfo? = if (!has(Manifest.permission.BLUETOOTH_CONNECT)) null else
        audio.getDevices(AudioManager.GET_DEVICES_OUTPUTS).firstOrNull {
            it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP && it.address.equals(address, ignoreCase = true) && address.isNotBlank()
        }
    private val deviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) {
            if (output() == null) { stopRinging(); locationRequest?.cancel() }
        }
    }
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == BluetoothAdapter.ACTION_DISCOVERY_FINISHED) { finishScan(); return }
            val device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java) ?: return
            if (!device.address.equals(address, ignoreCase = true)) return
            if (!scanning) return
            foundDuringScan = true
            val rssi = intent.getShortExtra(BluetoothDevice.EXTRA_RSSI, Short.MIN_VALUE)
            message = if (rssi == Short.MIN_VALUE) "Your AirPods were detected nearby." else "AirPods detected: $rssi dBm. A less negative value generally means a stronger signal; this is not a distance measurement."
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ContextCompat.registerReceiver(this, receiver, IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_FOUND)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
        }, ContextCompat.RECEIVER_EXPORTED)
        receiverRegistered = true
        audio.registerAudioDeviceCallback(deviceCallback, handler)
        if (output() != null) LastSeenStore.record(this, address)
        refreshLastSeen()
        setContent {
            MaterialTheme {
                Surface(Modifier.fillMaxSize()) {
                    Column(Modifier.systemBarsPadding().padding(20.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Find my AirPods", style = MaterialTheme.typography.headlineMedium)
                        Text(message)
                        Button(onClick = { if (scanning) finishScan() else scan() }, enabled = address.isNotBlank()) { Text(if (scanning) "Stop search" else "Search nearby") }
                        Text("Ringing plays a short sound through connected earbuds, not the charging case. Remove both earbuds from your ears first. Your media volume is unchanged.")
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { confirmSide = 0 }, enabled = !ringing) { Text("Ring left") }
                            OutlinedButton(onClick = { confirmSide = 1 }, enabled = !ringing) { Text("Ring right") }
                        }
                        if (ringing) Button(onClick = { stopRinging() }) { Text("Stop ringing") }
                        HorizontalDivider()
                        Text("Last seen", style = MaterialTheme.typography.titleLarge)
                        Text(lastSeen)
                        Text("This saves this phone's location while the AirPods are connected. It is not live tracking. Saved locations may be approximate. Android backup settings apply.")
                        Button(onClick = { saveLocation() }) { Text("Save connected location") }
                        OutlinedButton(onClick = { openMap() }, enabled = prefs.contains("latitude")) { Text("Open saved location in maps") }
                        HorizontalDivider()
                        Text("Call audio", style = MaterialTheme.typography.titleLarge)
                        Text("Phone and video-call apps control their microphone route. LibrePods cannot force high-quality AirPods microphone audio in other apps. Select the AirPods as the call's Bluetooth device.")
                        OutlinedButton(onClick = { startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)) }) { Text("Bluetooth settings") }
                        TextButton(onClick = { finish() }) { Text("Back") }
                    }
                    confirmSide?.let { side ->
                        AlertDialog(onDismissRequest = { confirmSide = null }, title = { Text("Earbuds out of your ears?") },
                            text = { Text("A five-second tone will play in the selected earbud. Do not wear it while ringing.") },
                            confirmButton = { TextButton(onClick = { confirmSide = null; ring(side) }) { Text("Ring earbud") } },
                            dismissButton = { TextButton(onClick = { confirmSide = null }) { Text("Cancel") } })
                    }
                }
            }
        }
    }

    private fun scan() {
        if (!has(Manifest.permission.BLUETOOTH_SCAN) || !has(Manifest.permission.BLUETOOTH_CONNECT)) {
            requestBluetooth.launch(arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)); return
        }
        if (adapter?.isEnabled != true) { message = "Turn Bluetooth on to search."; return }
        if (adapter?.isDiscovering == true) { message = "Another Bluetooth search is running. Try again when it finishes."; return }
        foundDuringScan = false
        message = "Searching for your saved AirPods for 15 seconds. Bluetooth discovery may briefly interrupt audio. No result does not mean they are absent."
        scanning = runCatching { adapter?.startDiscovery() == true }.getOrDefault(false)
        if (scanning) handler.postDelayed(stopScan, 15_000) else message = "Bluetooth discovery could not start. Check nearby-device permissions and Bluetooth settings."
    }
    private fun finishScan() {
        handler.removeCallbacks(stopScan)
        val wasScanning = scanning
        scanning = false
        if (wasScanning && !foundDuringScan) message = "Search finished without detecting the saved AirPods. They may be nearby but not discoverable; this is not proof they are out of range."
        if (wasScanning && has(Manifest.permission.BLUETOOTH_SCAN)) runCatching { adapter?.cancelDiscovery() }
    }
    private fun ring(side: Int) {
        val device = output() ?: run { message = "Connect the saved AirPods for media audio before ringing. No sound was played."; return }
        finishScan()
        stopRinging()
        runCatching {
            val samples = ShortArray(48_000 * 2)
            for (i in 0 until 48_000) {
                val envelope = if (i % 24_000 < 12_000) 0.08 else 0.0
                samples[i * 2 + side] = (sin(2 * PI * 880 * i / 48_000) * Short.MAX_VALUE * envelope).toInt().toShort()
            }
            val player = AudioTrack.Builder()
                .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
                .setAudioFormat(AudioFormat.Builder().setSampleRate(48_000).setEncoding(AudioFormat.ENCODING_PCM_16BIT).setChannelMask(AudioFormat.CHANNEL_OUT_STEREO).build())
                .setBufferSizeInBytes(samples.size * 2).setTransferMode(AudioTrack.MODE_STREAM).build()
            track = player
            ringing = true
            check(player.setPreferredDevice(device)) { "Could not select the AirPods output" }
            player.addOnRoutingChangedListener(AudioRouting.OnRoutingChangedListener {
                if (track === player && player.routedDevice?.id != device.id) stopRinging()
            }, handler)
            player.play()
            // Start with silence; never enqueue the tone until Android confirms the route.
            player.write(ShortArray(4_800), 0, 4_800, AudioTrack.WRITE_NON_BLOCKING)
            handler.postDelayed({
                if (track !== player) return@postDelayed
                if (player.routedDevice?.id != device.id) { stopRinging(); message = "Android did not route audio to the AirPods. Ringing cancelled."; return@postDelayed }
                ringing = true
                fun enqueue() {
                    if (track !== player || player.routedDevice?.id != device.id) return
                    player.write(samples, 0, samples.size, AudioTrack.WRITE_NON_BLOCKING)
                    handler.postDelayed({ enqueue() }, 1_000)
                }
                enqueue()
                handler.postDelayed(stopTone, 5_000)
            }, 200)
        }.onFailure { stopRinging(); message = "Could not ring the AirPods: ${it.message}" }
    }
    private fun stopRinging() {
        handler.removeCallbacks(stopTone)
        val player = track
        track = null
        runCatching { player?.pause(); player?.flush(); player?.release() }
        ringing = false
    }
    private fun saveLocation() {
        if (output() == null) { message = "Connect the saved AirPods for media audio before saving their location."; return }
        if (!has(Manifest.permission.ACCESS_COARSE_LOCATION)) {
            requestLocation.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)); return
        }
        val manager = getSystemService(LocationManager::class.java)
        val provider = if (has(Manifest.permission.ACCESS_FINE_LOCATION) && manager.isProviderEnabled(LocationManager.GPS_PROVIDER)) LocationManager.GPS_PROVIDER else LocationManager.NETWORK_PROVIDER
        if (!manager.isProviderEnabled(provider)) { message = "Enable phone location services first."; return }
        locationRequest?.cancel()
        val signal = CancellationSignal().also { locationRequest = it }
        message = "Getting this phone's location…"
        runCatching {
            manager.getCurrentLocation(provider, signal, mainExecutor) { location ->
                if (signal.isCanceled) return@getCurrentLocation
                locationRequest = null
                if (location != null && output() != null) {
                    LastSeenStore.record(this, address)
                    LastSeenStore.saveLocation(this, address, location)
                    message = "Saved this phone's location while your AirPods were connected."
                    refreshLastSeen()
                } else message = "No current location could be saved. Keep the AirPods connected and try outdoors."
            }
            handler.postDelayed({ if (locationRequest === signal) { signal.cancel(); locationRequest = null; message = "Location request timed out. Try again outdoors." } }, 30_000)
        }.onFailure { signal.cancel(); locationRequest = null; message = "Location unavailable: ${it.message}" }
    }
    private fun refreshLastSeen() {
        val seen = prefs.getLong("seen_at", 0)
        val located = prefs.getLong("location_at", 0)
        lastSeen = if (seen == 0L) "No connection recorded yet." else "Last recorded connection: ${DateFormat.getDateTimeInstance().format(Date(seen))}."
        lastSeen += if (located == 0L) " No phone location saved." else " Phone location from ${DateFormat.getDateTimeInstance().format(Date(located))}, accuracy approximately ${prefs.getFloat("accuracy", 0f).toInt()} m."
    }
    private fun openMap() {
        val lat = prefs.getString("latitude", null) ?: return
        val lon = prefs.getString("longitude", null) ?: return
        runCatching { startActivity(Intent(Intent.ACTION_VIEW, "geo:$lat,$lon?q=$lat,$lon(Last%20seen%20AirPods)".toUri())) }
            .onFailure { message = "No maps app is installed." }
    }
    override fun onStop() {
        stopRinging(); finishScan(); locationRequest?.cancel(); locationRequest = null
        super.onStop()
    }
    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        if (receiverRegistered) unregisterReceiver(receiver)
        audio.unregisterAudioDeviceCallback(deviceCallback)
        super.onDestroy()
    }
}
