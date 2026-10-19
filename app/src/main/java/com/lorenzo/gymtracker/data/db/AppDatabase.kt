package com.lorenzo.gymtracker.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.lorenzo.gymtracker.data.db.dao.BackupDao
import com.lorenzo.gymtracker.data.db.dao.ExerciseDao
import com.lorenzo.gymtracker.data.db.dao.RoutineDao
import com.lorenzo.gymtracker.data.db.dao.SessionDao
import com.lorenzo.gymtracker.data.db.entity.Exercise
import com.lorenzo.gymtracker.data.db.entity.Routine
import com.lorenzo.gymtracker.data.db.entity.RoutineExercise
import com.lorenzo.gymtracker.data.db.entity.Session
import com.lorenzo.gymtracker.data.db.entity.SessionExercise
import com.lorenzo.gymtracker.data.db.entity.SessionSet

@Database(
    entities = [
        Exercise::class,
        Routine::class,
        RoutineExercise::class,
        Session::class,
        SessionExercise::class,
        SessionSet::class,
    ],
    version = AppDatabase.VERSION,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun exerciseDao(): ExerciseDao
    abstract fun routineDao(): RoutineDao
    abstract fun sessionDao(): SessionDao
    abstract fun backupDao(): BackupDao

    companion object {
        const val VERSION = 3

        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "gymtracker.db")
                .addMigrations(*ALL_MIGRATIONS)
                .build()
    }
}
