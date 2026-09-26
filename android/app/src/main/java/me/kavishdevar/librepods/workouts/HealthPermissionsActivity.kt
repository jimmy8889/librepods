package me.kavishdevar.librepods.workouts

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

class HealthPermissionsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme { Surface(Modifier.fillMaxSize()) {
            Column(Modifier.systemBarsPadding().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("Your heart-rate data", style = MaterialTheme.typography.headlineSmall)
                Text("LibrePods saves accepted AirPods heart-rate measurements and workout times on this phone. It does not read your other health records.")
                Text("When you send a saved workout, LibrePods writes its exercise session and heart-rate samples to Health Connect. If you enable periodic readings and grant permission, those readings are also sent to Health Connect automatically.")
                Text("Samsung Health can read these records only with your separate permission. This does not provide a live sensor inside Samsung Health. No heart-rate data is sent to our servers or Google Drive.")
                Text("You can disable periodic readings in LibrePods, revoke write permissions in Health Connect, and delete local sessions. Deleting a local session does not delete copies already exported to Health Connect or Samsung Health; manage those in the respective apps.")
                Button(onClick = { finish() }) { Text("Done") }
            }
        } } }
    }
}
