package me.kavishdevar.librepods.presentation.widgets

import me.kavishdevar.librepods.workouts.WorkoutPoint
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Absolute timestamps stay truthful without a periodic widget wake-up. */
data class HeartRateDisplay(val value: String, val time: String, val date: String, val description: String) {
    companion object {
        fun from(point: WorkoutPoint?, zone: ZoneId = ZoneId.systemDefault()): HeartRateDisplay {
            if (point == null) return HeartRateDisplay("—", "", "", "No recorded heart rate. Open workouts")
            val at = Instant.ofEpochMilli(point.time).atZone(zone)
            return HeartRateDisplay(point.bpm.toString(), at.format(DateTimeFormatter.ofPattern("HH:mm")),
                at.format(DateTimeFormatter.ofPattern("dd/MM")),
                "Last recorded heart rate: ${point.bpm} beats per minute, ${at.format(DateTimeFormatter.ofPattern("d MMM yyyy HH:mm", Locale.getDefault()))}. Open workouts")
        }
    }
}
