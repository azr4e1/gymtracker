package com.lorenzo.gymtracker.data.export

import com.lorenzo.gymtracker.data.db.RoutineDetails
import com.lorenzo.gymtracker.data.db.RoutineExerciseDetails
import com.lorenzo.gymtracker.data.db.SessionDetails
import com.lorenzo.gymtracker.data.db.SessionExerciseDetails
import com.lorenzo.gymtracker.data.db.entity.Exercise
import com.lorenzo.gymtracker.data.db.entity.Muscle
import com.lorenzo.gymtracker.data.db.entity.Routine
import com.lorenzo.gymtracker.data.db.entity.RoutineExercise
import com.lorenzo.gymtracker.data.db.entity.Session
import com.lorenzo.gymtracker.data.db.entity.SessionExercise
import com.lorenzo.gymtracker.data.db.entity.SessionSet
import java.io.File
import java.time.ZoneOffset
import java.util.zip.ZipFile
import org.junit.Assert.assertTrue
import org.junit.Test

class ExcelExporterTest {
    @Test
    fun writesWorkbookWithAllSheetsAndTheData() {
        val bench = Exercise(id = 1, name = "Bench press", primaryMuscles = listOf(Muscle.CHEST), secondaryMuscles = listOf(Muscle.TRICEPS))
        val plank = Exercise(id = 2, name = "Plank", primaryMuscles = listOf(Muscle.ABS), timed = true, weighted = false)
        val start = 1_791_000_000_000L
        val session = SessionDetails(
            Session(id = 1, name = "Push day", startedAt = start, endedAt = start + 3_600_000),
            listOf(
                SessionExerciseDetails(
                    SessionExercise(id = 1, sessionId = 1, exerciseId = 1, position = 0, timed = false, restSec = 120),
                    bench,
                    listOf(SessionSet(sessionExerciseId = 1, position = 0, reps = 8, weightKg = 60.0, done = true)),
                ),
                SessionExerciseDetails(
                    SessionExercise(id = 2, sessionId = 1, exerciseId = 2, position = 1, timed = true, restSec = 30),
                    plank,
                    listOf(SessionSet(sessionExerciseId = 2, position = 0, durationSec = 45, done = true)),
                ),
            ),
        )
        val routine = RoutineDetails(
            Routine(id = 1, name = "Upper A"),
            listOf(RoutineExerciseDetails(RoutineExercise(routineId = 1, exerciseId = 1, position = 0, sets = 3, reps = 8, restSec = 120, durationSec = 45), bench)),
        )

        val file = File.createTempFile("export", ".xlsx").apply { deleteOnExit() }
        file.outputStream().use { ExcelExporter.write(it, listOf(session), listOf(bench, plank), listOf(routine), ZoneOffset.UTC) }

        // Read via the central directory, like Excel does (the zip is written in streaming mode).
        val files = ZipFile(file).use { zip ->
            zip.entries().asSequence().associate { it.name to zip.getInputStream(it).readBytes().decodeToString() }
        }
        assertTrue(files.keys.toString(), (1..5).all { "xl/worksheets/sheet$it.xml" in files })
        val workbook = files.getValue("xl/workbook.xml")
        listOf("Sets", "Workouts", "Muscles per week", "Exercises", "Routines").forEach { assertTrue(it, workbook.contains("name=\"$it\"")) }
        val strings = files.getValue("xl/sharedStrings.xml")
        listOf("Push day", "Bench press", "Plank", "Upper A", "Upper back").forEach { assertTrue(it, strings.contains(it)) }
        // Volume of the bench set: 60 × 8.
        assertTrue(files.getValue("xl/worksheets/sheet1.xml").contains("<v>480"))
    }
}
