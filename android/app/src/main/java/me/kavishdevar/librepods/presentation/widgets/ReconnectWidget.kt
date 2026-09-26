package me.kavishdevar.librepods.presentation.widgets

import android.Manifest
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.widget.RemoteViews
import android.widget.Toast
import androidx.core.content.ContextCompat
import me.kavishdevar.librepods.R
import me.kavishdevar.librepods.services.AirPodsService

/** A tap-only widget: no periodic updates, scans or background polling. */
class ReconnectWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val click = PendingIntent.getBroadcast(context, 0,
            Intent(context, ReconnectWidget::class.java).setAction(ACTION_RECONNECT),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val views = RemoteViews(context.packageName, R.layout.reconnect_widget)
        views.setOnClickPendingIntent(R.id.reconnect_widget_button, click)
        manager.updateAppWidget(ids, views)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action != ACTION_RECONNECT) return
        requestReconnect(context)
    }

    companion object {
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
