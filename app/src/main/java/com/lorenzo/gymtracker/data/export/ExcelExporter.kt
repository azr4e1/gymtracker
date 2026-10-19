package com.lorenzo.gymtracker.data.export

import com.lorenzo.gymtracker.data.db.RoutineDetails
import com.lorenzo.gymtracker.data.db.SessionDetails
import com.lorenzo.gymtracker.data.db.dao.SetRecord
import com.lorenzo.gymtracker.data.db.entity.Exercise
import com.lorenzo.gymtracker.data.db.entity.Muscle
import com.lorenzo.gymtracker.domain.CountPeriod
import com.lorenzo.gymtracker.domain.muscleSets
import com.lorenzo.gymtracker.domain.startOf
import java.io.OutputStream
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import org.dhatim.fastexcel.Workbook
import org.dhatim.fastexcel.Worksheet

/**
 * Writes the training log as an .xlsx workbook: every set, one row per workout, sets per muscle
 * per week, the exercises and the routines. Dates and times are real Excel values, so they sort
 * and filter correctly. Weights are in kg.
 */
object ExcelExporter {
    private const val DATE_FORMAT = "yyyy-mm-dd"
    private const val TIME_FORMAT = "hh:mm"
    private const val NUMBER_FORMAT = "0.0"

    /** [sessions] are finished workouts, oldest first. */
    fun write(
        output: OutputStream,
        sessions: List<SessionDetails>,
        exercises: List<Exercise>,
        routines: List<RoutineDetails>,
        zone: ZoneId = ZoneId.systemDefault(),
    ) {
        val workbook = Workbook(output, "GymTracker", "1.0")
        writeSets(workbook.newWorksheet("Sets"), sessions, zone)
        writeWorkouts(workbook.newWorksheet("Workouts"), sessions, zone)
        writeMuscles(workbook.newWorksheet("Muscles per week"), sessions, exercises, zone)
        writeExercises(workbook.newWorksheet("Exercises"), exercises)
        writeRoutines(workbook.newWorksheet("Routines"), routines)
        workbook.finish()
    }

    private fun writeSets(ws: Worksheet, sessions: List<SessionDetails>, zone: ZoneId) {
        header(ws, "Date", "Time", "Workout", "Exercise", "Set", "Tracked by", "Weight (kg)", "Reps", "Time (s)", "Volume (kg)", "Done")
        var r = 1
        sessions.forEach { d ->
            val start = d.session.startedAt.toDateTime(zone)
            d.exercises.forEach { e ->
                e.sets.forEachIndexed { i, set ->
                    dateTime(ws, r, 0, start)
                    ws.value(r, 2, d.session.name)
                    ws.value(r, 3, e.exercise.name)
                    ws.value(r, 4, i + 1)
                    ws.value(r, 5, if (e.entry.timed) "Time" else "Reps")
                    set.weightKg?.let { numbers(ws, r, 6, it) }
                    set.reps?.let { ws.value(r, 7, it) }
                    set.durationSec?.let { ws.value(r, 8, it) }
                    if (!e.entry.timed && set.weightKg != null && set.reps != null) numbers(ws, r, 9, set.weightKg * set.reps)
                    ws.value(r, 10, if (set.done) "Yes" else "No")
                    r++
                }
            }
        }
        widths(ws, 12, 8, 20, 28, 6, 11, 12, 7, 9, 12, 7)
    }

    private fun writeWorkouts(ws: Worksheet, sessions: List<SessionDetails>, zone: ZoneId) {
        header(ws, "Date", "Start", "End", "Minutes", "Workout", "Exercises", "Sets done", "Volume (kg)")
        sessions.forEachIndexed { i, d ->
            val r = i + 1
            val s = d.session
            dateTime(ws, r, 0, s.startedAt.toDateTime(zone))
            s.endedAt?.let {
                time(ws, r, 2, it.toDateTime(zone))
                ws.value(r, 3, (it - s.startedAt) / 60_000)
            }
            ws.value(r, 4, s.name)
            ws.value(r, 5, d.exercises.joinToString(", ") { it.exercise.name })
            ws.value(r, 6, d.doneSets)
            val volume = d.exercises.filter { !it.entry.timed }.sumOf { e ->
                e.sets.filter { it.done }.sumOf { (it.weightKg ?: 0.0) * (it.reps ?: 0) }
            }
            numbers(ws, r, 7, volume)
        }
        widths(ws, 12, 8, 8, 9, 20, 60, 10, 12)
    }

    /** Ticked sets per muscle each week (from Monday); a muscle that only helps counts half. */
    private fun writeMuscles(ws: Worksheet, sessions: List<SessionDetails>, exercises: List<Exercise>, zone: ZoneId) {
        header(ws, "Week of", *Muscle.entries.map { it.excelLabel }.toTypedArray())
        val byId = exercises.associateBy { it.id }
        val weeks = sessions.groupBy { CountPeriod.WEEK.startOf(it.session.startedAt.toDateTime(zone).toLocalDate()) }
        weeks.entries.forEachIndexed { i, (monday, inWeek) ->
            val r = i + 1
            date(ws, r, 0, monday)
            val totals = muscleSets(inWeek.flatMap { it.records() }, byId)
            Muscle.entries.forEachIndexed { c, m -> totals[m]?.let { numbers(ws, r, c + 1, it) } }
        }
        widths(ws, 12, *IntArray(Muscle.entries.size) { 11 })
    }

    private fun writeExercises(ws: Worksheet, exercises: List<Exercise>) {
        header(
            ws, "Name", "Main muscles", "Also works", "Tracked by", "Weight", "Sets", "Reps", "Time (s)", "Rest (s)", "Built in", "Deleted",
        )
        exercises.sortedBy { it.name.lowercase() }.forEachIndexed { i, e ->
            val r = i + 1
            ws.value(r, 0, e.name)
            ws.value(r, 1, e.primaryMuscles.joinToString(", ") { it.excelLabel })
            ws.value(r, 2, e.secondaryMuscles.joinToString(", ") { it.excelLabel })
            ws.value(r, 3, if (e.timed) "Time" else "Reps")
            ws.value(r, 4, if (e.weighted) "Yes" else "No")
            ws.value(r, 5, e.sets)
            ws.value(r, 6, e.reps)
            ws.value(r, 7, e.durationSec)
            ws.value(r, 8, e.restSec)
            ws.value(r, 9, if (e.builtIn) "Yes" else "No")
            ws.value(r, 10, if (e.hidden) "Yes" else "No")
        }
        widths(ws, 28, 24, 30, 11, 8, 6, 6, 9, 9, 9, 8)
    }

    private fun writeRoutines(ws: Worksheet, routines: List<RoutineDetails>) {
        header(ws, "Routine", "Order", "Exercise", "Tracked by", "Sets", "Reps", "Time (s)", "Weight (kg)", "Rest (s)")
        var r = 1
        routines.forEach { routine ->
            routine.exercises.forEachIndexed { i, (planned, exercise) ->
                ws.value(r, 0, routine.routine.name)
                ws.value(r, 1, i + 1)
                ws.value(r, 2, exercise.name)
                ws.value(r, 3, if (planned.timed) "Time" else "Reps")
                ws.value(r, 4, planned.sets)
                if (planned.timed) ws.value(r, 6, planned.durationSec) else ws.value(r, 5, planned.reps)
                planned.weightKg?.let { numbers(ws, r, 7, it) }
                ws.value(r, 8, planned.restSec)
                r++
            }
        }
        widths(ws, 20, 7, 28, 11, 6, 6, 9, 12, 9)
    }

    /** Ticked sets of a workout as stats records. */
    private fun SessionDetails.records(): List<SetRecord> = exercises.flatMap { e ->
        e.sets.filter { it.done }.map { SetRecord(session.id, session.startedAt, e.exercise.id, e.entry.timed, it.reps, it.weightKg, it.durationSec) }
    }

    private fun Long.toDateTime(zone: ZoneId): LocalDateTime = Instant.ofEpochMilli(this).atZone(zone).toLocalDateTime()

    private val Muscle.excelLabel: String get() = name.lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() }

    private fun header(ws: Worksheet, vararg titles: String) {
        titles.forEachIndexed { c, title -> ws.value(0, c, title) }
        ws.range(0, 0, 0, titles.lastIndex).style().bold().set()
        ws.freezePane(0, 1)
    }

    private fun date(ws: Worksheet, r: Int, c: Int, date: LocalDate) {
        ws.value(r, c, date)
        ws.style(r, c).format(DATE_FORMAT).set()
    }

    /** Date in column [c], time of day in the next one. */
    private fun dateTime(ws: Worksheet, r: Int, c: Int, at: LocalDateTime) {
        date(ws, r, c, at.toLocalDate())
        time(ws, r, c + 1, at)
    }

    private fun time(ws: Worksheet, r: Int, c: Int, at: LocalDateTime) {
        ws.value(r, c, at.toLocalTime().toSecondOfDay() / 86_400.0)
        ws.style(r, c).format(TIME_FORMAT).set()
    }

    /** Writes consecutive numeric cells starting at column [start]. */
    private fun numbers(ws: Worksheet, r: Int, start: Int, vararg values: Double) {
        values.forEachIndexed { i, v ->
            ws.value(r, start + i, v)
            ws.style(r, start + i).format(NUMBER_FORMAT).set()
        }
    }

    private fun widths(ws: Worksheet, vararg widths: Int) {
        widths.forEachIndexed { c, w -> ws.width(c, w.toDouble()) }
    }
}
