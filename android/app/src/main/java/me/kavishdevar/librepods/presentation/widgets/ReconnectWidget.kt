package me.kavishdevar.librepods.presentation.widgets

import android.Manifest
import android.app.PendingIntent
import android.content.ComponentName
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.core.content.ContextCompat
import me.kavishdevar.librepods.R
import me.kavishdevar.librepods.services.AirPodsService

/** A tap-only widget: no periodic updates, scans or background polling. */
class ReconnectWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        AirPodsControlRow.update(context, force = true)
    }

    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager,
        appWidgetId: Int, newOptions: android.os.Bundle) {
        AirPodsControlRow.update(context, force = true)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        when (intent.action) {
            ACTION_PINNED -> {
                AirPodsControlRow.update(context, force = true)
                Toast.makeText(context, R.string.widget_added, Toast.LENGTH_LONG).show()
            }
            ACTION_RECONNECT -> requestReconnect(context)
            AirPodsControlRow.ACTION_MODE -> AirPodsControlRow.selectMode(context, intent.getIntExtra("mode", -1))
        }
    }

    companion object {
        private const val ACTION_PINNED = "me.kavishdevar.librepods.WIDGET_PINNED"

        fun requestHomeWidget(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            if (!manager.isRequestPinAppWidgetSupported) {
                Toast.makeText(context, R.string.widget_add_manually, Toast.LENGTH_LONG).show()
                return
            }
            val callback = PendingIntent.getBroadcast(context, 0,
                Intent(context, ReconnectWidget::class.java).setAction(ACTION_PINNED),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            try {
                val accepted = manager.requestPinAppWidget(
                    ComponentName(context, ReconnectWidget::class.java), null, callback)
                // An accepted request only opens the launcher's placement flow. The callback
                // above is the confirmation that the launcher actually added the widget.
                if (!accepted) Toast.makeText(context, R.string.widget_add_manually, Toast.LENGTH_LONG).show()
            } catch (_: RuntimeException) {
                Toast.makeText(context, R.string.widget_add_manually, Toast.LENGTH_LONG).show()
            }
        }

        const val ACTION_RECONNECT = "me.kavishdevar.librepods.WIDGET_RECONNECT"

        fun requestReconnect(context: Context) {
            val message = when {
                ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED ||
                    ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED -> R.string.reconnect_widget_setup
                context.getSharedPreferences("settings", Context.MODE_PRIVATE).getString("mac_address", "").isNullOrBlank() -> R.string.reconnect_widget_setup
                context.getSystemService(BluetoothManager::class.java).adapter?.isEnabled != true -> R.string.reconnect_widget_bluetooth_off
                else -> null
            }
            if (message != null) {
                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                return
            }
            try {
                ContextCompat.startForegroundService(context,
                    Intent(context, AirPodsService::class.java).setAction(ACTION_RECONNECT))
            } catch (_: RuntimeException) {
                Toast.makeText(context, R.string.reconnect_widget_open_app, Toast.LENGTH_SHORT).show()
            }
        }
    }
}
