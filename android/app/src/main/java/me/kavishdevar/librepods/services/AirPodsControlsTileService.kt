/*
    LibrePods - AirPods liberated from Apple’s ecosystem
    Copyright (C) 2025 LibrePods contributors

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see <https://www.gnu.org/licenses/>.
*/

package me.kavishdevar.librepods.services

import android.app.PendingIntent
import android.os.Build
import me.kavishdevar.librepods.workouts.WorkoutStore
import me.kavishdevar.librepods.workouts.WorkoutsActivity
import me.kavishdevar.librepods.presentation.widgets.HeartRateDisplay
import android.app.AlertDialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.graphics.drawable.Icon
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.core.content.ContextCompat
import me.kavishdevar.librepods.R
import me.kavishdevar.librepods.bluetooth.BluetoothConnectionManager
import me.kavishdevar.librepods.data.AirPodsNotifications
import me.kavishdevar.librepods.data.Capability
import me.kavishdevar.librepods.data.BatteryComponent
import me.kavishdevar.librepods.presentation.widgets.ReconnectWidget

/** Observe status only while Quick Settings is visible; no timer or Bluetooth scan. */
abstract class AirPodsStatusTile : TileService() {
    private var disconnectedNotice = false
    protected val connected get() = !disconnectedNotice && BluetoothConnectionManager.aacpSocket?.isConnected == true
    protected val preferences get() = getSharedPreferences("settings", MODE_PRIVATE)
    protected val batteryText: String
        get() {
            if (!connected) return getString(R.string.qs_airpods_disconnected)
            val batteries = ServiceManager.getService()?.getBattery() ?: emptyList()
            return getString(R.string.qs_airpods_battery_summary,
                quickSettingsBatteryValue(batteries, BatteryComponent.LEFT),
                quickSettingsBatteryValue(batteries, BatteryComponent.RIGHT),
                quickSettingsBatteryValue(batteries, BatteryComponent.CASE))
        }
    private var listening = false
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                AirPodsNotifications.AIRPODS_DISCONNECTED -> disconnectedNotice = true
                AirPodsNotifications.AIRPODS_CONNECTED -> disconnectedNotice = false
            }
            renderTile()
        }
    }
    private val changes = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == "name" || key == "off_listening_mode") renderTile()
    }
    override fun onStartListening() {
        super.onStartListening()
        disconnectedNotice = false
        if (!listening) {
            ContextCompat.registerReceiver(this, receiver, IntentFilter().apply {
                addAction(WorkoutStore.ACTION_LATEST_CHANGED)
                addAction(AirPodsNotifications.ANC_DATA)
                addAction(AirPodsNotifications.BATTERY_DATA)
                addAction(AirPodsNotifications.AIRPODS_CONNECTED)
                addAction(AirPodsNotifications.AIRPODS_DISCONNECTED)
            }, ContextCompat.RECEIVER_NOT_EXPORTED)
            preferences.registerOnSharedPreferenceChangeListener(changes)
            listening = true
        }
        renderTile()
    }
    private fun stopObserving() {
        if (!listening) return
        unregisterReceiver(receiver)
        preferences.unregisterOnSharedPreferenceChangeListener(changes)
        listening = false
    }
    override fun onStopListening() { stopObserving(); super.onStopListening() }
    override fun onDestroy() { stopObserving(); super.onDestroy() }
    protected abstract fun renderTile()
}

class AirPodsControlsTileService : AirPodsStatusTile() {
    private var picker: AlertDialog? = null
    override fun renderTile() {
        val tile = qsTile ?: return
        tile.label = getString(R.string.row_name)
        tile.state = if (connected) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.subtitle = batteryText
        tile.contentDescription = "${tile.label}, ${modeName(ServiceManager.getService()?.getANC() ?: 0)}, ${tile.subtitle}"
        tile.icon = Icon.createWithResource(this, R.drawable.airpods)
        tile.updateTile()
        renderControls()
    }
    override fun onClick() {
        super.onClick()
        if (isLocked) unlockAndRun { openPicker() } else openPicker()
    }
    private var controlRow: android.view.View? = null

    private fun openPicker() {
        picker?.dismiss()
        controlRow = android.view.LayoutInflater.from(this).inflate(R.layout.airpods_control_row, null)
        val container = android.widget.FrameLayout(this).apply {
            addView(controlRow, android.widget.FrameLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                (72 * resources.displayMetrics.density).toInt()))
        }
        picker = AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle(R.string.row_name)
            .setView(container)
            .setNegativeButton(android.R.string.cancel, null)
            .create().also { dialog ->
                dialog.setOnDismissListener { controlRow = null }
                showDialog(dialog)
                dialog.window?.setLayout(android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                    android.view.ViewGroup.LayoutParams.WRAP_CONTENT)
            }
        renderControls()
    }

    private fun renderControls() {
        val row = controlRow ?: return
        val service = ServiceManager.getService()
        val batteries = if (connected) service?.getBattery() ?: emptyList() else emptyList()
        row.findViewById<android.widget.TextView>(R.id.row_battery_buds).text = getString(
            R.string.row_battery_buds, quickSettingsBatteryValue(batteries, BatteryComponent.LEFT),
            quickSettingsBatteryValue(batteries, BatteryComponent.RIGHT))
        row.findViewById<android.widget.TextView>(R.id.row_battery_case).text =
            if (connected) getString(R.string.row_battery_case, quickSettingsBatteryValue(batteries, BatteryComponent.CASE))
            else getString(R.string.qs_airpods_disconnected)
        val heart = HeartRateDisplay.from(WorkoutStore.get(this).latestSample.value)
        row.findViewById<android.widget.TextView>(R.id.row_heart_value).text = heart.value
        row.findViewById<android.widget.TextView>(R.id.row_heart_time).text = heart.time
        row.findViewById<android.widget.TextView>(R.id.row_heart_date).text = heart.date
        row.findViewById<android.view.View>(R.id.row_heart).apply {
            contentDescription = heart.description
            setOnClickListener {
                val intent = Intent(this@AirPodsControlsTileService, WorkoutsActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                if (Build.VERSION.SDK_INT >= 34) startActivityAndCollapse(PendingIntent.getActivity(
                    this@AirPodsControlsTileService, 21, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
                else { @Suppress("DEPRECATION") startActivityAndCollapse(intent) }
                picker?.dismiss()
            }
        }
        val capabilities = service?.airpodsInstance?.model?.capabilities
        val modes = availableQuickSettingsModes(capabilities == null || Capability.ADAPTIVE_AUDIO in capabilities,
            preferences.getBoolean("off_listening_mode", true))
        val ids = listOf(R.id.row_transparency, R.id.row_cancellation, R.id.row_adaptive)
        val icons = listOf(R.id.row_transparency_icon, R.id.row_cancellation_icon, R.id.row_adaptive_icon)
        val labels = listOf(R.id.row_transparency_text, R.id.row_cancellation_text, R.id.row_adaptive_text)
        listOf(3, 2, 4).forEachIndexed { index, mode ->
            val button = row.findViewById<android.view.View>(ids[index])
            button.visibility = if (mode in modes) android.view.View.VISIBLE else android.view.View.GONE
            button.isEnabled = connected && capabilities?.contains(Capability.LISTENING_MODE) == true
            val selected = connected && service?.getANC() == mode
            button.setBackgroundResource(if (selected) R.drawable.airpods_row_selected else R.drawable.airpods_row_button)
            val color = when {
                !button.isEnabled -> android.graphics.Color.GRAY
                selected -> android.graphics.Color.rgb(24, 24, 27)
                else -> android.graphics.Color.WHITE
            }
            row.findViewById<android.widget.ImageView>(icons[index]).setColorFilter(color)
            row.findViewById<android.widget.TextView>(labels[index]).setTextColor(color)
            button.contentDescription = modeName(mode) + if (selected) getString(R.string.row_selected) else ""
            button.setOnClickListener {
                me.kavishdevar.librepods.presentation.widgets.AirPodsControlRow.selectMode(this, mode)
            }
        }
        row.findViewById<android.view.View>(R.id.row_reconnect).setOnClickListener {
            ReconnectWidget.requestReconnect(this)
        }
    }
    private fun modeName(mode: Int): String = getString(when (mode) {
        1 -> R.string.qs_airpods_off
        2 -> R.string.qs_airpods_cancellation
        3 -> R.string.qs_airpods_transparency
        4 -> R.string.qs_airpods_adaptive
        else -> R.string.qs_airpods_unknown
    })
    override fun onDestroy() { picker?.dismiss(); picker = null; super.onDestroy() }
}
