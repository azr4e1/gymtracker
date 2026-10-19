package com.lorenzo.gymtracker.data.export

import android.content.Context
import android.net.Uri
import com.lorenzo.gymtracker.data.repository.ExerciseRepository
import com.lorenzo.gymtracker.data.repository.RoutineRepository
import com.lorenzo.gymtracker.data.repository.SessionRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

class ExportRepository(
    private val context: Context,
    private val sessions: SessionRepository,
    private val exercises: ExerciseRepository,
    private val routines: RoutineRepository,
) {
    /** Writes the Excel workbook to [uri]. Returns the number of workouts exported. */
    suspend fun exportExcel(uri: Uri): Int = withContext(Dispatchers.IO) {
        // Finished workouts only, oldest first; the one in progress isn't done yet.
        val finished = sessions.observeFinished().first().reversed()
        val output = context.contentResolver.openOutputStream(uri, "wt") ?: error("Cannot open file for writing")
        output.use { ExcelExporter.write(it, finished, exercises.observeAll().first(), routines.observeAll().first()) }
        finished.size
    }
}
