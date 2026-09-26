package me.kavishdevar.librepods.workouts

/** Session time follows elapsed realtime, so a wall-clock correction cannot reorder samples. */
internal class WorkoutTimeline(val startedAt: Long, private val startedElapsed: Long) {
    fun timeAt(elapsed: Long): Long = startedAt + (elapsed - startedElapsed).coerceAtLeast(0)
}

data class WorkoutPoint(val time: Long, val bpm: Int)
data class WorkoutSummary(val id: String, val type: String, val start: Long, val end: Long?,
    val count: Int, val average: Int?, val minimum: Int?, val maximum: Int?, val exported: Boolean,
    val outcome: String)
data class SavedWorkout(val summary: WorkoutSummary, val points: List<WorkoutPoint>)

/** Stable minute buckets allow a partially failed Health Connect export to be retried safely. */
internal fun workoutMinuteBuckets(start: Long, points: List<WorkoutPoint>): Map<Long, List<WorkoutPoint>> =
    points.filter { it.time >= start && it.bpm in 30..220 }.sortedBy { it.time }
        .distinctBy { it.time }.groupBy { (it.time - start) / 60_000 }
