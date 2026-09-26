package me.kavishdevar.librepods.experimental

import android.Manifest
import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
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
import androidx.core.content.FileProvider
import androidx.lifecycle.Lifecycle
import me.kavishdevar.librepods.services.AirPodsService

// No FragmentActivity or fragments are used in this screen.
@SuppressLint("InvalidFragmentVersionForActivityResult")
class ExperimentsActivity : ComponentActivity() {
    private var service by mutableStateOf<AirPodsService?>(null)
    private var message by mutableStateOf("")
    private var bound = false
    private val permissions = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted && lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) service?.experiments?.startMicrophoneTest()
        else message = "Microphone permission is required to run the recording test."
    }
    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) { service = (binder as? AirPodsService.LocalBinder)?.getService() }
        override fun onServiceDisconnected(name: ComponentName?) { service = null }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(Modifier.fillMaxSize()) {
                    Column(Modifier.systemBarsPadding().padding(20.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("AirPods experiments", style = MaterialTheme.typography.headlineMedium)
                        Text("Early protocol support for your AirPods. Tests stop when you leave this screen. Connect the AirPods in LibrePods before starting.")
                        val experiments = service?.experiments
                        if (experiments == null) Text("Connecting to LibrePods service…") else {
                            val state by experiments.state.collectAsState()
                            Text("Heart rate", style = MaterialTheme.typography.titleLarge)
                            Text(state.bpm?.let { "$it BPM" } ?: "— BPM", style = MaterialTheme.typography.displaySmall)
                            Text(state.heartStatus)
                            if (state.heartDiagnostics.isNotEmpty()) Text(state.heartDiagnostics)
                            Text("Accepted samples: ${state.heartSamples}")
                            if (state.heartDiagnostics.isNotEmpty()) TextButton(onClick = {
                                getSystemService(android.content.ClipboardManager::class.java).setPrimaryClip(
                                    android.content.ClipData.newPlainText("LibrePods sensor diagnostics",
                                        "${state.heartStatus}\n${state.heartDiagnostics}\nAccepted samples: ${state.heartSamples}"))
                            }) { Text("Copy sensor diagnostics") }
                            Text("Wear at least one AirPod Pro 3. Startup can take up to two minutes. Experimental readings are not for medical decisions; unknown or poor-quality samples are hidden.")
                            Button(onClick = { if (state.heartActive) experiments.stopHeartRate() else experiments.startHeartRate() }, enabled = !state.micActive) {
                                Text(if (state.heartActive) "Stop heart-rate test" else "Start heart-rate test")
                            }
                            HorizontalDivider()
                            Text("High-quality microphone", style = MaterialTheme.typography.titleLarge)
                            Text(state.micStatus)
                            if (state.pcmBytes > 0) Text("Decoded ${state.pcmBytes} bytes at ${state.sampleRate} Hz")
                            Text("Records a 10-second test using the AirPods' experimental audio stream. Conversation awareness pauses during the test and is restored afterwards. The sample stays in this app's cache unless you share it.")
                            Text("You can leave music playing to test simultaneous playback. This does not replace the microphone used by phone calls, WhatsApp, or other apps. End calls before testing.")
                            Button(onClick = {
                                if (state.micActive) experiments.stopMicrophone() else permissions.launch(Manifest.permission.RECORD_AUDIO)
                            }, enabled = !state.heartActive) { Text(if (state.micActive) "Stop and save sample" else "Record microphone test") }
                            state.sampleFile?.let { file ->
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedButton(onClick = {
                                        val uri = FileProvider.getUriForFile(this@ExperimentsActivity, "$packageName.provider", file)
                                        runCatching { startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, "audio/wav").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)) }
                                            .onFailure { message = "No app available to play WAV recordings. Use Share sample instead." }
                                    }) { Text("Play sample") }
                                    OutlinedButton(onClick = {
                                        val uri = FileProvider.getUriForFile(this@ExperimentsActivity, "$packageName.provider", file)
                                        startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("audio/wav").putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), "Share test recording"))
                                    }) { Text("Share sample") }
                                }
                            }
                        }
                        if (message.isNotEmpty()) Text(message)
                        TextButton(onClick = { finish() }) { Text("Back") }
                    }
                }
            }
        }
    }
    override fun onStart() {
        super.onStart()
        bound = bindService(Intent(this, AirPodsService::class.java), connection, Context.BIND_AUTO_CREATE)
        if (!bound) message = "Could not connect to LibrePods. Open the main app and retry."
    }
    override fun onStop() {
        service?.experiments?.stopAll()
        if (bound) unbindService(connection)
        bound = false; service = null
        super.onStop()
    }
}
