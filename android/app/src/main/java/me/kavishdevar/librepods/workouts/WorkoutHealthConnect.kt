package me.kavishdevar.librepods.workouts

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.metadata.Device
import androidx.health.connect.client.records.metadata.Metadata
import java.time.Instant
import java.time.ZoneId

object WorkoutHealthConnect {
    val permissions = setOf(HealthPermission.getWritePermission(HeartRateRecord::class),
        HealthPermission.getWritePermission(ExerciseSessionRecord::class))
    val types = linkedMapOf("Walking" to ExerciseSessionRecord.EXERCISE_TYPE_WALKING,
        "Running" to ExerciseSessionRecord.EXERCISE_TYPE_RUNNING,
        "Cycling" to ExerciseSessionRecord.EXERCISE_TYPE_BIKING,
        "Other workout" to ExerciseSessionRecord.EXERCISE_TYPE_OTHER_WORKOUT)

    internal fun records(workout: SavedWorkout): List<Record> {
        val w = workout.summary
        val end = requireNotNull(w.end) { "Stop the workout before exporting." }
        require(end > w.start && workout.points.isNotEmpty()) { "No heart-rate readings were saved." }
        require(workout.points.all { it.time in w.start until end && it.bpm in 30..220 }) { "Workout samples are outside the saved session." }
        val zone = ZoneId.systemDefault()
        val device = Device(type = Device.TYPE_UNKNOWN, manufacturer = "Apple", model = "AirPods Pro 3")
        fun metadata(id: String) = Metadata.activelyRecorded(device = device,
            clientRecordId = "librepods:${w.id}:$id", clientRecordVersion = 1)
        val result = mutableListOf<Record>()
        if (w.type !in listOf("Daily readings", "Activity readings")) result += ExerciseSessionRecord(
            startTime = Instant.ofEpochMilli(w.start), startZoneOffset = zone.rules.getOffset(Instant.ofEpochMilli(w.start)),
            endTime = Instant.ofEpochMilli(end), endZoneOffset = zone.rules.getOffset(Instant.ofEpochMilli(end)),
            exerciseType = types[w.type] ?: ExerciseSessionRecord.EXERCISE_TYPE_OTHER_WORKOUT,
            title = "AirPods ${w.type.lowercase()}", metadata = metadata("session"))
        workoutMinuteBuckets(w.start, workout.points).forEach { (bucket, points) ->
            val first = Instant.ofEpochMilli(points.first().time)
            val last = Instant.ofEpochMilli(points.last().time + 1)
            result += HeartRateRecord(startTime = first, startZoneOffset = zone.rules.getOffset(first),
                endTime = last, endZoneOffset = zone.rules.getOffset(last),
                samples = points.map { HeartRateRecord.Sample(Instant.ofEpochMilli(it.time), it.bpm.toLong()) },
                metadata = metadata("heart:$bucket"))
        }
        return result
    }
    suspend fun export(context: Context, id: String) {
        check(HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE) { "Health Connect needs to be installed or updated." }
        val client = HealthConnectClient.getOrCreate(context)
        check(client.permissionController.getGrantedPermissions().containsAll(permissions)) { "Allow LibrePods to write exercise and heart rate in Health Connect." }
        val store = WorkoutStore.get(context)
        val workout = store.load(id)
        // Stable client IDs make retrying a partially completed export idempotent.
        records(workout).chunked(100).forEach { client.insertRecords(it) }
        store.markExported(id)
    }
}
