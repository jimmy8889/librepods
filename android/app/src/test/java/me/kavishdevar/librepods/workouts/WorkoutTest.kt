package me.kavishdevar.librepods.workouts

import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import org.junit.Assert.*
import org.junit.Test

class WorkoutTest {
    @Test fun isolatedStepsDoNotStartBoost() {
        val window = StepActivityWindow()
        repeat(59) { window.step(it * 1000L) }
        assertFalse(window.isActive(60_000))
        window.step(60_000)
        assertTrue(window.isActive(60_000))
    }
    @Test fun slowStepsDoNotBecomeSustainedActivity() {
        val window = StepActivityWindow()
        repeat(60) { window.step(it * 10_000L) }
        assertFalse(window.isActive(600_000))
    }
    @Test fun restEndsBoostAndSingleNewStepDoesNotRestartIt() {
        val window = StepActivityWindow()
        repeat(60) { window.step(it * 1000L) }
        assertTrue(window.isActive(200_000))
        assertFalse(window.isActive(239_000))
        window.step(300_000)
        assertFalse(window.isActive(300_000))
    }
    @Test fun longGapExpiresEvenWithoutAnIntermediatePoll() {
        val window = StepActivityWindow()
        repeat(60) { window.step(it * 1000L) }
        window.step(300_000)
        assertFalse(window.isActive(300_000))
    }
    @Test fun duplicateSensorTimestampsDoNotInventSteps() {
        val window = StepActivityWindow()
        repeat(100) { window.step(1) }
        assertFalse(window.isActive(2))
    }
    @Test fun elapsedClockKeepsSessionTimeOrdered() {
        val clock = WorkoutTimeline(1_000_000,20_000)
        assertEquals(1_010_000,clock.timeAt(30_000))
        assertEquals(1_000_000,clock.timeAt(19_000))
    }
    private fun workout(type: String = "Walking", end: Long? = 121_000) = SavedWorkout(
        WorkoutSummary("stable-id",type,1_000,end,3,80,70,90,false,"Stopped"),
        listOf(WorkoutPoint(2_000,70),WorkoutPoint(3_000,80),WorkoutPoint(62_000,90)))
    @Test fun exportUsesStableIdsAndDoesNotInventMetrics() {
        val first = WorkoutHealthConnect.records(workout())
        val retry = WorkoutHealthConnect.records(workout())
        assertEquals(first.map { it.metadata.clientRecordId },retry.map { it.metadata.clientRecordId })
        assertEquals(1,first.filterIsInstance<ExerciseSessionRecord>().size)
        val heart = first.filterIsInstance<HeartRateRecord>()
        assertEquals(2,heart.size)
        assertEquals(listOf(70L,80L,90L),heart.flatMap { it.samples }.map { it.beatsPerMinute })
        assertEquals(3,first.size)
    }
    @Test fun periodicAndDetectedMovementDoNotCreateWorkoutSessions() {
        listOf("Daily readings","Activity readings").forEach {
            assertTrue(WorkoutHealthConnect.records(workout(it)).all { record -> record is HeartRateRecord })
        }
    }
    @Test(expected = IllegalArgumentException::class) fun unfinishedSessionCannotBeExported() { WorkoutHealthConnect.records(workout(end=null)) }
    @Test(expected = IllegalArgumentException::class) fun invalidSampleCannotBeExported() {
        WorkoutHealthConnect.records(workout().copy(points=listOf(WorkoutPoint(500,75))))
    }
    @Test fun minuteBucketsAreStableAcrossGapsAndFilterDuplicateTimestamps() {
        val buckets = workoutMinuteBuckets(1_000,listOf(WorkoutPoint(2_000,75),WorkoutPoint(2_000,75),WorkoutPoint(181_000,80)))
        assertEquals(setOf(0L,3L),buckets.keys)
        assertEquals(2,buckets.values.sumOf { it.size })
    }
}
