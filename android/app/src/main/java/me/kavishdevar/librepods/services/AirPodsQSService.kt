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

import android.app.AlertDialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.graphics.drawable.Icon
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.widget.Toast
import androidx.core.content.ContextCompat
import me.kavishdevar.librepods.R
import me.kavishdevar.librepods.bluetooth.AACPManager
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

class AirPodsQSService : AirPodsStatusTile() {
    private var picker: AlertDialog? = null
    override fun renderTile() {
        val tile = qsTile ?: return
        tile.label = getString(R.string.qs_airpods_modes)
        tile.state = if (connected) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.subtitle = batteryText
        tile.contentDescription = "${tile.label}, ${modeName(ServiceManager.getService()?.getANC() ?: 0)}, ${tile.subtitle}"
        if (picker?.isShowing == true) picker?.setTitle(pickerTitle())
        tile.icon = Icon.createWithResource(this, R.drawable.airpods)
        tile.updateTile()
    }
    override fun onClick() {
        super.onClick()
        if (isLocked) unlockAndRun { openPicker() } else openPicker()
    }
    private fun openPicker() {
        picker?.dismiss()
        val service = ServiceManager.getService()
        val builder = AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle(pickerTitle())
            .setNegativeButton(android.R.string.cancel, null)
            .setNeutralButton(R.string.reconnect_widget_label) { _, _ ->
                ReconnectWidget.requestReconnect(this)
            }
        if (!connected || service == null) {
            builder.setMessage(R.string.qs_airpods_connect_first)
        } else if (service.airpodsInstance?.model?.capabilities?.contains(Capability.LISTENING_MODE) != true) {
            builder.setMessage(R.string.qs_airpods_unsupported)
        } else {
            val modes = availableQuickSettingsModes(
                service.airpodsInstance?.model?.capabilities?.contains(Capability.ADAPTIVE_AUDIO) == true,
                preferences.getBoolean("off_listening_mode", true)
            )
            builder.setSingleChoiceItems(modes.map(::modeName).toTypedArray(), modes.indexOf(service.getANC())) { dialog, index ->
                val liveService = ServiceManager.getService()
                val sent = connected && liveService?.aacpManager?.sendControlCommand(
                    AACPManager.Companion.ControlCommandIdentifiers.LISTENING_MODE.value, modes[index]
                ) == true
                if (!sent) Toast.makeText(this, R.string.qs_airpods_failed, Toast.LENGTH_SHORT).show()
                dialog.dismiss()
                // Current mode is refreshed from accessory status broadcasts, not guessed here.
                renderTile()
            }
        }
        picker = builder.create().also { showDialog(it) }
    }
    private fun pickerTitle() = "${getString(R.string.qs_airpods_modes)}\n$batteryText"
    private fun modeName(mode: Int): String = getString(when (mode) {
        1 -> R.string.qs_airpods_off
        2 -> R.string.qs_airpods_cancellation
        3 -> R.string.qs_airpods_transparency
        4 -> R.string.qs_airpods_adaptive
        else -> R.string.qs_airpods_unknown
    })
    override fun onDestroy() { picker?.dismiss(); picker = null; super.onDestroy() }
}

class AirPodsReconnectQSService : AirPodsStatusTile() {
    override fun renderTile() {
        qsTile?.apply {
            label = getString(R.string.reconnect_widget_name)
            subtitle = batteryText
            state = if (connected) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            icon = Icon.createWithResource(this@AirPodsReconnectQSService, R.drawable.reconnect_widget_icon)
            contentDescription = "$label, $subtitle"
            updateTile()
        }
    }
    override fun onClick() {
        super.onClick()
        if (isLocked) unlockAndRun { ReconnectWidget.requestReconnect(this) }
        else ReconnectWidget.requestReconnect(this)
    }
}
