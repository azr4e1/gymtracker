package com.lorenzo.gymtracker.data.backup

import android.content.Context
import android.net.Uri
import com.lorenzo.gymtracker.data.db.AppDatabase
import com.lorenzo.gymtracker.data.repository.ExerciseRepository
import java.time.OffsetDateTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

class BackupRepository(
    private val context: Context,
    private val database: AppDatabase,
    private val exercises: ExerciseRepository,
) {
    private val json = Json {
        prettyPrint = true
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    /** Writes a full backup to [uri]. Returns the number of workouts exported. */
    suspend fun export(uri: Uri): Int = withContext(Dispatchers.IO) {
        val dao = database.backupDao()
        val backup = Backup(
            schemaVersion = AppDatabase.VERSION,
            exportedAt = OffsetDateTime.now().toString(),
            exercises = dao.exercises(),
            routines = dao.routines(),
            routineExercises = dao.routineExercises(),
            sessions = dao.sessions(),
            sessionExercises = dao.sessionExercises(),
            sessionSets = dao.sessionSets(),
        )
        val output = context.contentResolver.openOutputStream(uri, "wt") ?: error("Cannot open file for writing")
        output.bufferedWriter().use { it.write(json.encodeToString(Backup.serializer(), backup)) }
        backup.sessions.size
    }

    /** Permanently deletes all data, then puts back the default exercises. */
    suspend fun deleteAll() = withContext(Dispatchers.IO) {
        database.backupDao().deleteAll()
        exercises.addDefaultsIfEmpty()
    }

    /** Replaces all data with the backup at [uri]. Returns the number of workouts restored. */
    suspend fun import(uri: Uri): Int = withContext(Dispatchers.IO) {
        val input = context.contentResolver.openInputStream(uri) ?: error("Cannot open file")
        val backup = input.bufferedReader().use { json.decodeFromString(Backup.serializer(), it.readText()) }
        require(backup.schemaVersion <= AppDatabase.VERSION) {
            "Backup is from a newer version of the app (schema ${backup.schemaVersion})"
        }
        database.backupDao().replaceAll(backup)
        exercises.addDefaultsIfEmpty()
        backup.sessions.size
    }
}
