package com.lorenzo.gymtracker.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.lorenzo.gymtracker.data.DEFAULT_EXERCISES

/**
 * 1 → 2: reps or time is chosen per routine exercise instead of per routine. Each routine
 * exercise takes its routine's old setting; the column is dropped from routines and sessions.
 */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE routine_exercises ADD COLUMN timed INTEGER NOT NULL DEFAULT 0")
        db.execSQL("UPDATE routine_exercises SET timed = (SELECT r.timed FROM routines r WHERE r.id = routine_exercises.routineId)")

        // SQLite can't drop a column here, so both tables are rebuilt. Room turns foreign keys on
        // only after migrating, so dropping the old tables doesn't touch the rows that refer to them.
        db.execSQL(
            "CREATE TABLE routines_new (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `createdAt` INTEGER NOT NULL)",
        )
        db.execSQL("INSERT INTO routines_new (id, name, createdAt) SELECT id, name, createdAt FROM routines")
        db.execSQL("DROP TABLE routines")
        db.execSQL("ALTER TABLE routines_new RENAME TO routines")

        db.execSQL(
            "CREATE TABLE sessions_new (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `routineId` INTEGER, `name` TEXT NOT NULL, " +
                "`startedAt` INTEGER NOT NULL, `endedAt` INTEGER, " +
                "FOREIGN KEY(`routineId`) REFERENCES `routines`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL )",
        )
        db.execSQL("INSERT INTO sessions_new (id, routineId, name, startedAt, endedAt) SELECT id, routineId, name, startedAt, endedAt FROM sessions")
        db.execSQL("DROP TABLE sessions")
        db.execSQL("ALTER TABLE sessions_new RENAME TO sessions")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_sessions_routineId` ON `sessions` (`routineId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_sessions_startedAt` ON `sessions` (`startedAt`)")
    }
}

/**
 * 2 → 3: exercises can track a weight or not. Timed exercises and the built-in bodyweight ones
 * start without; the built-in farmer's carry is added as a timed exercise with weight.
 */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE exercises ADD COLUMN weighted INTEGER NOT NULL DEFAULT 1")
        db.execSQL("UPDATE exercises SET weighted = 0 WHERE timed = 1")
        val bodyweight = DEFAULT_EXERCISES.filter { !it.timed && !it.weighted }.map { it.name }
        db.execSQL(
            "UPDATE exercises SET weighted = 0 WHERE builtIn = 1 AND name IN (${bodyweight.joinToString(",") { "?" }})",
            bodyweight.toTypedArray(),
        )
        DEFAULT_EXERCISES.filter { it.timed && it.weighted }.forEach { e ->
            db.execSQL(
                "INSERT INTO exercises (name, primaryMuscles, secondaryMuscles, timed, weighted, sets, reps, restSec, durationSec, builtIn, hidden, createdAt) " +
                    "SELECT ?, ?, ?, 1, 1, ?, ?, ?, ?, 1, 0, ? WHERE NOT EXISTS (SELECT 1 FROM exercises WHERE name = ?)",
                arrayOf<Any>(
                    e.name, e.primaryMuscles.joinToString(",") { it.name }, e.secondaryMuscles.joinToString(",") { it.name },
                    e.sets, e.reps, e.restSec, e.durationSec, System.currentTimeMillis(), e.name,
                ),
            )
        }
    }
}

val ALL_MIGRATIONS = arrayOf(MIGRATION_1_2, MIGRATION_2_3)
