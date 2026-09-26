@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package me.kavishdevar.librepods.presentation.widgets

import me.kavishdevar.librepods.workouts.WorkoutStore
import me.kavishdevar.librepods.workouts.WorkoutPoint
import me.kavishdevar.librepods.workouts.WorkoutsActivity
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.view.View
import android.widget.RemoteViews
import android.widget.Toast
import me.kavishdevar.librepods.MainActivity
import me.kavishdevar.librepods.R
import me.kavishdevar.librepods.bluetooth.AACPManager
import me.kavishdevar.librepods.bluetooth.BluetoothConnectionManager
import me.kavishdevar.librepods.data.BatteryComponent
import me.kavishdevar.librepods.data.Capability
import me.kavishdevar.librepods.services.ServiceManager
import me.kavishdevar.librepods.services.availableQuickSettingsModes
import me.kavishdevar.librepods.services.quickSettingsBatteryValue

/** Event-driven RemoteViews row. Identical state is never republished. */
object AirPodsControlRow {
    const val ACTION_MODE = "me.kavishdevar.librepods.WIDGET_LISTENING_MODE"
    private data class State(val connected: Boolean, val left: String, val right: String,
        val case: String, val mode: Int, val modes: List<Int>, val supported: Boolean, val heartRate: WorkoutPoint?)
    private var lastState: State? = null
    private var lastIds = emptyList<Int>()

    @Synchronized
    fun update(context: Context, force: Boolean = false, connectionHint: Boolean? = null) {
        val manager = AppWidgetManager.getInstance(context)
        val ids = manager.getAppWidgetIds(ComponentName(context, ReconnectWidget::class.java))
        if (ids.isEmpty()) { lastState = null; lastIds = emptyList(); return }
        val service = ServiceManager.getService()
        val connected = connectionHint != false && BluetoothConnectionManager.aacpSocket?.isConnected == true && service != null
        val batteries = if (connected) service!!.getBattery() else emptyList()
        val capabilities = service?.airpodsInstance?.model?.capabilities
        val modes = availableQuickSettingsModes(
            capabilities == null || Capability.ADAPTIVE_AUDIO in capabilities,
            context.getSharedPreferences("settings", Context.MODE_PRIVATE).getBoolean("off_listening_mode", true))
        val state = State(connected,
            quickSettingsBatteryValue(batteries, BatteryComponent.LEFT),
            quickSettingsBatteryValue(batteries, BatteryComponent.RIGHT),
            quickSettingsBatteryValue(batteries, BatteryComponent.CASE),
            if (connected) service!!.getANC() else 0, modes,
            capabilities?.contains(Capability.LISTENING_MODE) == true, WorkoutStore.get(context).latestSample.value)
        if (!force && lastState == state && lastIds == ids.toList()) return
        val views = RemoteViews(context.packageName, R.layout.airpods_control_row)
        views.setTextViewText(R.id.row_battery_buds, context.getString(R.string.row_battery_buds, state.left, state.right))
        views.setTextViewText(R.id.row_battery_case, if (connected) context.getString(R.string.row_battery_case, state.case)
            else context.getString(R.string.qs_airpods_disconnected))
        views.setContentDescription(R.id.row_battery_group, context.getString(R.string.row_battery_accessibility, state.left, state.right, state.case))
        views.setOnClickPendingIntent(R.id.row_battery_group, PendingIntent.getActivity(context, 20,
            Intent(context, MainActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
        val heart = HeartRateDisplay.from(state.heartRate)
        views.setTextViewText(R.id.row_heart_value, heart.value)
        views.setTextViewText(R.id.row_heart_time, heart.time)
        views.setTextViewText(R.id.row_heart_date, heart.date)
        views.setContentDescription(R.id.row_heart, heart.description)
        views.setOnClickPendingIntent(R.id.row_heart, PendingIntent.getActivity(context, 21,
            Intent(context, WorkoutsActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
        val buttons = listOf(
            Triple(3, R.id.row_transparency, R.string.qs_airpods_transparency),
            Triple(2, R.id.row_cancellation, R.string.qs_airpods_cancellation),
            Triple(4, R.id.row_adaptive, R.string.qs_airpods_adaptive))
        val imageIds = listOf(R.id.row_transparency_icon, R.id.row_cancellation_icon, R.id.row_adaptive_icon)
        val labelIds = listOf(R.id.row_transparency_text, R.id.row_cancellation_text, R.id.row_adaptive_text)
        buttons.forEachIndexed { index, (mode, id, label) ->
            val selected = connected && state.mode == mode
            val enabled = connected && state.supported
            views.setViewVisibility(id, if (mode in modes) View.VISIBLE else View.GONE)
            views.setBoolean(id, "setEnabled", enabled)
            views.setInt(id, "setBackgroundResource", if (selected) R.drawable.airpods_row_selected else R.drawable.airpods_row_button)
            val color = when {
                !enabled -> Color.rgb(130, 130, 134)
                selected -> Color.rgb(24, 24, 27)
                else -> Color.WHITE
            }
            views.setInt(imageIds[index], "setColorFilter", color)
            views.setTextColor(labelIds[index], color)
            views.setContentDescription(id, context.getString(label) + if (selected) context.getString(R.string.row_selected) else "")
            views.setOnClickPendingIntent(id, PendingIntent.getBroadcast(context, mode,
                Intent(context, ReconnectWidget::class.java).setAction(ACTION_MODE).putExtra("mode", mode),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
        }
        views.setOnClickPendingIntent(R.id.row_reconnect, PendingIntent.getBroadcast(context, 0,
            Intent(context, ReconnectWidget::class.java).setAction(ReconnectWidget.ACTION_RECONNECT),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
        manager.updateAppWidget(ids, views)
        lastState = state; lastIds = ids.toList()
    }

    fun selectMode(context: Context, mode: Int) {
        val service = ServiceManager.getService()
        val capabilities = service?.airpodsInstance?.model?.capabilities
        val allowed = availableQuickSettingsModes(capabilities?.contains(Capability.ADAPTIVE_AUDIO) == true,
            context.getSharedPreferences("settings", Context.MODE_PRIVATE).getBoolean("off_listening_mode", true))
        val sent = mode in allowed && capabilities?.contains(Capability.LISTENING_MODE) == true &&
            BluetoothConnectionManager.aacpSocket?.isConnected == true &&
            service?.aacpManager?.sendControlCommand(AACPManager.Companion.ControlCommandIdentifiers.LISTENING_MODE.value, mode) == true
        if (!sent) Toast.makeText(context, R.string.qs_airpods_failed, Toast.LENGTH_SHORT).show()
        // Selected state changes only after the AirPods report their new mode.
        update(context)
    }
}
