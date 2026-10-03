package me.kavishdevar.librepods.finding

import android.Manifest
import android.annotation.SuppressLint
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Lab entry point for actual OOB-negotiated peers; not an AirPods precision-finding claim. */
@SuppressLint("InvalidFragmentVersionForActivityResult")
class UwbExperimentActivity : ComponentActivity() {
    private lateinit var ranger: UwbRanger
    private lateinit var bleInspector: PairedBleInspector
    private var profile: UwbSessionProfile? = null
    private var importedAt = 0L
    private var loaded by mutableStateOf(false)
    private var message by mutableStateOf("")
    private val permission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted && lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) ranger.observe()
        else message = "Nearby devices permission was not granted."
    }
    private val importSession = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@registerForActivityResult
        lifecycleScope.launch {
            val parsed = withContext(Dispatchers.IO) {
                runCatching {
                    val bytes = contentResolver.openInputStream(uri)?.use { it.readNBytes(UwbSessionProfile.MAX_BYTES + 1) }
                        ?: throw IllegalArgumentException("Cannot open session file.")
                    require(bytes.size <= UwbSessionProfile.MAX_BYTES) { "Session file is too large." }
                    UwbSessionProfile.parse(bytes.toString(Charsets.UTF_8))
                }
            }
            if (!lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) { parsed.getOrNull()?.destroy(); return@launch }
            parsed.onSuccess {
                clearProfile(); profile = it; loaded = true; importedAt = SystemClock.elapsedRealtime()
                message = "Session loaded into memory. Import does not authenticate an AirPods case; only use parameters from a peer you have negotiated with."
            }.onFailure { message = "Invalid session file. Check the documented format; no ranging was started." }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ranger = UwbRanger.create(this)
        bleInspector = PairedBleInspector(this)
        setContent {
            val state by ranger.state.collectAsState()
            val inspection by bleInspector.state.collectAsState()
            val locale = LocalConfiguration.current.locales[0]
            MaterialTheme {
                Surface(Modifier.fillMaxSize()) {
                    Column(Modifier.systemBarsPadding().padding(20.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("UWB experiment", style = MaterialTheme.typography.headlineMedium)
                        Text("AirPods case authentication and session negotiation are not implemented. This test runs the phone's UWB radio with parameters already negotiated with a compatible peer. It cannot find your case automatically.")
                        Text(state.status)
                        if (state.capabilities.isNotEmpty()) Text(state.capabilities)
                        Button(onClick = { checkPermission() }, enabled = !state.active) { Text("Check phone UWB") }
                        val reading = state.reading
                        Text(reading?.let { String.format(locale, "%.2f m", it.metres) } ?: "— m", style = MaterialTheme.typography.displayMedium)
                        if (reading != null) {
                            Text(reading.azimuthRadians?.let { String.format(locale, "Azimuth: %.1f°", Math.toDegrees(it)) } ?: "Direction unavailable")
                            reading.elevationRadians?.let { Text(String.format(locale, "Elevation: %.1f°", Math.toDegrees(it))) }
                        }
                        Text("Low-confidence and old readings are hidden. Ranging stops when you leave this screen or after two minutes. Session data is cleared on exit and is never saved or logged by LibrePods.")
                        OutlinedButton(onClick = { importSession.launch(arrayOf("text/*", "application/octet-stream")) }, enabled = state.available && !state.active) { Text("Import negotiated session") }
                        Button(onClick = {
                            if (state.active) { ranger.stop(); clearProfile() }
                            else profile?.let {
                                if (SystemClock.elapsedRealtime() - importedAt > 300_000) {
                                    clearProfile(); message = "Session expired. Import freshly negotiated parameters."
                                } else { ranger.start(it); clearProfile() }
                            }
                        }, enabled = state.active || (loaded && state.available && !inspection.active)) { Text(if (state.active) "Stop UWB test" else "Start UWB test") }
                        if (message.isNotEmpty()) Text(message)
                        HorizontalDivider()
                        Text("Case protocol investigation", style = MaterialTheme.typography.titleLarge)
                        Text("Service discovery only for a BLE peer resolved with your saved AirPods identity key. It may be an earbud interface; it does not authenticate the charging case. No characteristic values are read, no commands are written and no pairing changes are made. Stops after 25 seconds or when you leave.")
                        OutlinedButton(onClick = { if (inspection.active) bleInspector.close() else bleInspector.start() }, enabled = !state.active) {
                            Text(if (inspection.active) "Stop BLE inspection" else "Inspect paired BLE interface")
                        }
                        Text(inspection.status)
                        inspection.metadata.forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
                        TextButton(onClick = { finish() }) { Text("Back") }
                    }
                }
            }
        }
    }
    private fun checkPermission() {
        if (Build.VERSION.SDK_INT >= 36 && packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_UWB))
            permission.launch(Manifest.permission.RANGING) else ranger.observe()
    }
    private fun clearProfile() { profile?.destroy(); profile = null; loaded = false; importedAt = 0 }
    override fun onStart() { super.onStart(); ranger.observe() }
    override fun onStop() { ranger.close(); bleInspector.close(); clearProfile(); super.onStop() }
    override fun onDestroy() { ranger.close(); bleInspector.close(); super.onDestroy() }
}
