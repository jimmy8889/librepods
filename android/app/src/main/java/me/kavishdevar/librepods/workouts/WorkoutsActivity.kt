package me.kavishdevar.librepods.workouts

import android.content.*
import android.os.Bundle
import android.os.IBinder
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.*
import me.kavishdevar.librepods.services.AirPodsService
import java.text.DateFormat
import java.util.Date

class WorkoutsActivity : ComponentActivity() {
    private var service by mutableStateOf<AirPodsService?>(null)
    private var message by mutableStateOf("")
    private var busy by mutableStateOf(false)
    private var bound = false
    private var pendingExport: String? = null
    private var pendingMovement: Boolean? = null
    private val activityPermission = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) { granted ->
        pendingMovement = granted
        service?.experiments?.let { it.setMovementBoost(granted); pendingMovement = null }
        if (!granted) message = "Physical activity permission is needed for automatic movement detection. Manual workouts still work."
    }
    private val healthPermission = registerForActivityResult(PermissionController.createRequestPermissionResultContract()) { granted ->
        val id = pendingExport; pendingExport = null
        if (granted.containsAll(WorkoutHealthConnect.permissions)) {
            message = "LibrePods can write to Health Connect. Allow Samsung Health to read heart rate and exercise in its Health Connect settings."
            if (id != null) export(id)
        } else message = "Readings stay on this phone. Allow exercise and heart-rate write access to sync them."
    }
    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            service = (binder as? AirPodsService.LocalBinder)?.getService()
            pendingMovement?.let { service?.experiments?.setMovementBoost(it); pendingMovement = null }
        }
        override fun onServiceDisconnected(name: ComponentName?) { service = null }
    }
    private fun connectHealth(id: String? = null) {
        if (HealthConnectClient.getSdkStatus(this) != HealthConnectClient.SDK_AVAILABLE) {
            message = "Install or update Health Connect, then try again."; return
        }
        pendingExport = id
        healthPermission.launch(WorkoutHealthConnect.permissions)
    }
    private fun export(id: String) {
        if (busy) return
        busy = true
        lifecycleScope.launch {
            try {
                WorkoutHealthConnect.export(this@WorkoutsActivity, id)
                message = "Saved to Health Connect. Open Samsung Health to let it sync."
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { message = e.message ?: "Sync failed. Your local session is saved; you can retry." }
            finally { busy = false }
        }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pendingExport = savedInstanceState?.getString("pendingExport")
        if(savedInstanceState?.containsKey("pendingMovement") == true) pendingMovement = savedInstanceState.getBoolean("pendingMovement")
        val store = WorkoutStore.get(this)
        setContent { MaterialTheme { Surface(Modifier.fillMaxSize()) {
            val history by store.history.collectAsState()
            val storageError by store.error.collectAsState()
            var selectedType by remember { mutableStateOf("Walking") }
            var deleteId by remember { mutableStateOf<String?>(null) }
            Column(Modifier.systemBarsPadding().padding(20.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("AirPods heart rate", style = MaterialTheme.typography.headlineMedium)
                Text("Live readings are shown here. Samsung Health receives saved records through Health Connect, not a live sensor connection.")
                val experiments = service?.experiments
                if (experiments == null) Text("Connecting to LibrePods…") else {
                    val state by experiments.state.collectAsState()
                    Text(state.bpm?.let { "$it BPM" } ?: "— BPM", style = MaterialTheme.typography.displayMedium)
                    Text(state.heartStatus)
                    if (state.recentWorkoutPoints.size > 1) HeartGraph(state.recentWorkoutPoints)
                    state.recordingStart?.let { start ->
                        var elapsed by remember(start) { mutableLongStateOf(0L) }
                        LaunchedEffect(start) { while(isActive) { elapsed = ((System.currentTimeMillis()-start)/1000).coerceAtLeast(0); delay(1000) } }
                        Text("${state.recordingKind} • ${elapsed/60}:${(elapsed%60).toString().padStart(2,'0')} • ${state.heartSamples} readings")
                    }
                    if (state.recordingId != null) {
                        Button(onClick = { experiments.stopRecordingFromUser() }) { Text("Stop and save") }
                        Text("Recording continues with the screen off. Disconnecting ends and saves the session.")
                    } else {
                        Text("Workout type")
                        WorkoutHealthConnect.types.keys.chunked(2).forEach { row ->
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                row.forEach { type -> FilterChip(selected = selectedType == type, onClick = { selectedType = type }, label = { Text(type) }) }
                            }
                        }
                        Button(onClick = { experiments.startWorkout(selectedType) }, enabled = !state.micActive && !state.heartActive && storageError == null) { Text("Start workout") }
                    }
                    HorizontalDivider()
                    Text("Readings throughout the day", style = MaterialTheme.typography.titleMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Switch(checked = state.dailyEnabled, onCheckedChange = { experiments.setDailyReadings(it,state.dailyMinutes) })
                        Text(if(state.dailyEnabled) "Periodic readings enabled" else "Periodic readings off")
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(5,15,30).forEach { minutes -> FilterChip(selected = state.dailyMinutes == minutes,
                            onClick = { experiments.setDailyReadings(state.dailyEnabled,minutes) }, label = { Text("$minutes min") }) }
                    }
                    Text("While an AirPod is connected and worn, collect up to five accepted readings per interval, with a 30-second limit. Workouts take priority. Android may delay checks while asleep. Readings sync automatically when Health Connect permission is granted.")
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Switch(checked = state.movementBoost, enabled = state.dailyEnabled, onCheckedChange = {
                            if (it) activityPermission.launch(android.Manifest.permission.ACTIVITY_RECOGNITION)
                            else experiments.setMovementBoost(false)
                        })
                        Text("Increase readings while moving")
                    }
                    Text(state.movementStatus)
                    Text("After roughly 60 steps within 90 seconds, record about once per second until three minutes without steps. Carry your phone. Cycling and weight training may not be detected; start those workouts manually. Detected movement is saved as heart-rate history, not a guessed workout.")
                    if(state.syncStatus.isNotEmpty()) Text(state.syncStatus)
                }
                HorizontalDivider()
                Text("Samsung Health", style = MaterialTheme.typography.titleMedium)
                OutlinedButton(onClick = { connectHealth() }, enabled = !busy) { Text("Allow Health Connect sync") }
                Text("In Samsung Health → Settings → Health Connect, allow Samsung Health to read Heart rate and Exercise. Then open Samsung Health after syncing a session. Avoid recording the same workout in both apps.")
                TextButton(onClick = { runCatching { startActivity(Intent(HealthConnectClient.ACTION_HEALTH_CONNECT_SETTINGS)) }.onFailure { message = "Open Health Connect from your phone settings." } }) { Text("Open Health Connect") }
                TextButton(onClick = { startActivity(Intent(this@WorkoutsActivity, HealthPermissionsActivity::class.java)) }) { Text("Data and permissions") }
                if(message.isNotBlank()) Text(message)
                storageError?.let { Text(it) }
                Text("Saved sessions", style = MaterialTheme.typography.titleLarge)
                history.filter { it.end != null }.forEach { item ->
                    Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("${item.type} • ${DateFormat.getDateTimeInstance(DateFormat.SHORT,DateFormat.SHORT).format(Date(item.start))}")
                        Text("${item.count} readings • average ${item.average ?: "—"} BPM • ${item.minimum ?: "—"}–${item.maximum ?: "—"} BPM")
                        Text(item.outcome)
                        if (item.exported) Text("Sent to Health Connect")
                        if (!item.exported && item.count > 0) OutlinedButton(onClick = { connectHealth(item.id) }, enabled = !busy) { Text("Send to Health Connect") }
                        TextButton(onClick = { deleteId = item.id }, enabled = !busy) { Text("Delete local session") }
                    } }
                }
                TextButton(onClick = { finish() }) { Text("Back") }
            }
            deleteId?.let { id -> AlertDialog(onDismissRequest = { deleteId = null },
                title = { Text("Delete local session?") }, text = { Text("Copies already sent to Health Connect or Samsung Health are not deleted.") },
                confirmButton = { TextButton(onClick = { deleteId = null; lifecycleScope.launch { runCatching { store.delete(id) }.onFailure { message = "Could not delete this session." } } }) { Text("Delete") } },
                dismissButton = { TextButton(onClick = { deleteId = null }) { Text("Cancel") } }) }
        } } }
    }
    override fun onSaveInstanceState(outState: Bundle) { outState.putString("pendingExport",pendingExport); pendingMovement?.let { outState.putBoolean("pendingMovement",it) }; super.onSaveInstanceState(outState) }
    override fun onStart() { super.onStart(); bound = bindService(Intent(this,AirPodsService::class.java),connection,Context.BIND_AUTO_CREATE) }
    override fun onStop() { if(bound) unbindService(connection); bound=false; service=null; super.onStop() }
}

@Composable
private fun HeartGraph(points: List<WorkoutPoint>) {
    val color = MaterialTheme.colorScheme.primary
    Canvas(Modifier.fillMaxWidth().height(100.dp)) {
        val from = points.first().time
        val span = (points.last().time-from).coerceAtLeast(1)
        val low = (points.minOf { it.bpm }-5).coerceAtLeast(0)
        val range = (points.maxOf { it.bpm }+5-low).coerceAtLeast(1)
        fun position(p: WorkoutPoint) = Offset((p.time-from).toFloat()/span*size.width, size.height-(p.bpm-low).toFloat()/range*size.height)
        points.zipWithNext().filter { (a,b) -> b.time-a.time <= 4_000 }.forEach { (a,b) -> drawLine(color,position(a),position(b),strokeWidth=3.dp.toPx()) }
    }
}
