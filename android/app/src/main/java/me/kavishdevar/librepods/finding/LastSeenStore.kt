package me.kavishdevar.librepods.finding

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.SystemClock
import androidx.core.content.ContextCompat
import androidx.core.content.edit

/** Saved phone location observed while the selected AirPods are connected. */
object LastSeenStore {
    fun record(context: Context, address: String) {
        if (address.isBlank()) return
        val prefs = context.getSharedPreferences("find_airpods_$address", Context.MODE_PRIVATE)
        prefs.edit { putLong("seen_at", System.currentTimeMillis()) }
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) return
        val manager = context.getSystemService(LocationManager::class.java)
        val location = runCatching {
            manager.getProviders(true).mapNotNull { provider ->
                runCatching { manager.getLastKnownLocation(provider) }.getOrNull()
            }
                .filter { SystemClock.elapsedRealtimeNanos() - it.elapsedRealtimeNanos in 0..120_000_000_000L }
                .maxByOrNull { it.elapsedRealtimeNanos }
        }.getOrNull() ?: return
        saveLocation(context, address, location)
    }

    fun saveLocation(context: Context, address: String, location: Location) {
        context.getSharedPreferences("find_airpods_$address", Context.MODE_PRIVATE).edit {
            putString("latitude", location.latitude.toString())
            putString("longitude", location.longitude.toString())
            putFloat("accuracy", location.accuracy)
            putLong("location_at", location.time)
        }
    }
}
