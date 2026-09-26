package me.kavishdevar.librepods.services

import me.kavishdevar.librepods.data.Battery
import me.kavishdevar.librepods.data.BatteryStatus

/** Unknown/disconnected battery values must not appear as a real percentage. */
internal fun quickSettingsBatteryValue(batteries: List<Battery>, component: Int): String {
    val battery = batteries.firstOrNull { it.component == component } ?: return "—"
    if (battery.level !in 0..100 || battery.status !in setOf(
            BatteryStatus.CHARGING, BatteryStatus.NOT_CHARGING, BatteryStatus.OPTIMIZED_CHARGING
        )) return "—"
    val charging = if (battery.status == BatteryStatus.CHARGING) "⚡" else ""
    return "$charging${battery.level}%"
}
