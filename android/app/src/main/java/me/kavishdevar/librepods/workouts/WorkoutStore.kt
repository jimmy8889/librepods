package me.kavishdevar.librepods.workouts

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import java.util.UUID
import java.util.concurrent.Executors
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Serialized writes persist each accepted sample. The database is excluded from device backups. */
class WorkoutStore private constructor(context: Context) {
    private val worker = Executors.newSingleThreadExecutor()
    private val helper = object : SQLiteOpenHelper(context.applicationContext,
        File(context.noBackupFilesDir, "airpods-workouts.db").absolutePath, null, 1) {
        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL("CREATE TABLE workouts (id TEXT PRIMARY KEY, type TEXT NOT NULL, start INTEGER NOT NULL, end INTEGER, exported INTEGER NOT NULL DEFAULT 0, outcome TEXT NOT NULL DEFAULT '')")
            db.execSQL("CREATE TABLE samples (workout TEXT NOT NULL, time INTEGER NOT NULL, bpm INTEGER NOT NULL, PRIMARY KEY(workout,time))")
        }
        override fun onUpgrade(db: SQLiteDatabase, old: Int, new: Int) = Unit
    }
    private val mutableHistory = MutableStateFlow<List<WorkoutSummary>>(emptyList())
    val history = mutableHistory.asStateFlow()
    private val mutableError = MutableStateFlow<String?>(null)
    val error = mutableError.asStateFlow()
    init {
        enqueue {
            // A process restart never silently resumes recording or invents time after the last sample.
            helper.writableDatabase.execSQL("UPDATE workouts SET end=MAX(start+1,COALESCE((SELECT MAX(time)+1 FROM samples WHERE workout=workouts.id),start+1)), outcome='Interrupted — saved through last reading' WHERE end IS NULL")
        }
    }
    private fun enqueue(action: () -> Unit) = worker.execute {
        try { action(); refresh() }
        catch (_: Exception) { mutableError.value = "Could not save workout data. Stop recording and check available phone storage." }
    }
    fun begin(type: String, start: Long): String {
        val id = UUID.randomUUID().toString()
        enqueue { helper.writableDatabase.insertOrThrow("workouts", null, ContentValues().apply {
            put("id", id); put("type", type); put("start", start)
        }) }
        return id
    }
    fun record(id: String, time: Long, bpm: Int) = enqueue {
        if (bpm !in 30..220) return@enqueue
        helper.writableDatabase.execSQL("INSERT OR IGNORE INTO samples(workout,time,bpm) SELECT id,?,? FROM workouts WHERE id=? AND end IS NULL AND start<=?",
            arrayOf<Any>(time, bpm, id, time))
    }
    fun finish(id: String, end: Long, outcome: String) = enqueue {
        helper.writableDatabase.execSQL("UPDATE workouts SET end=MAX(start+1,?,COALESCE((SELECT MAX(time)+1 FROM samples WHERE workout=workouts.id),start+1)), outcome=? WHERE id=? AND end IS NULL", arrayOf<Any>(end, outcome, id))
    }
    private fun refresh() {
        val list = mutableListOf<WorkoutSummary>()
        helper.readableDatabase.rawQuery("SELECT w.id,w.type,w.start,w.end,COUNT(s.time),AVG(s.bpm),MIN(s.bpm),MAX(s.bpm),w.exported,w.outcome FROM workouts w LEFT JOIN samples s ON s.workout=w.id GROUP BY w.id ORDER BY w.start DESC", null).use { c ->
            while(c.moveToNext()) list += WorkoutSummary(c.getString(0),c.getString(1),c.getLong(2),if(c.isNull(3)) null else c.getLong(3),c.getInt(4),
                if(c.isNull(5)) null else c.getDouble(5).toInt(),if(c.isNull(6)) null else c.getInt(6),if(c.isNull(7)) null else c.getInt(7),c.getInt(8)!=0,c.getString(9))
        }
        mutableHistory.value = list
    }
    suspend fun load(id: String): SavedWorkout = execute {
        refresh()
        val summary = mutableHistory.value.first { it.id == id }
        val points = mutableListOf<WorkoutPoint>()
        helper.readableDatabase.rawQuery("SELECT time,bpm FROM samples WHERE workout=? ORDER BY time", arrayOf(id)).use { c ->
            while(c.moveToNext()) points += WorkoutPoint(c.getLong(0),c.getInt(1))
        }
        SavedWorkout(summary,points)
    }
    suspend fun markExported(id: String) = execute {
        helper.writableDatabase.execSQL("UPDATE workouts SET exported=1 WHERE id=?", arrayOf(id)); refresh()
    }
    suspend fun delete(id: String) = execute {
        val db = helper.writableDatabase
        db.beginTransaction()
        try {
            check(mutableHistory.value.none { it.id == id && it.end == null })
            db.delete("samples","workout=?",arrayOf(id)); db.delete("workouts","id=?",arrayOf(id)); db.setTransactionSuccessful()
        } finally { db.endTransaction() }
        refresh()
    }
    private suspend fun <T> execute(action: () -> T): T = suspendCancellableCoroutine { continuation ->
        worker.execute {
            try { continuation.resume(action()) } catch(e: Exception) { continuation.resumeWithException(e) }
        }
    }
    companion object {
        @Volatile private var instance: WorkoutStore? = null
        fun get(context: Context): WorkoutStore = instance ?: synchronized(this) {
            instance ?: WorkoutStore(context.applicationContext).also { instance = it }
        }
    }
}
