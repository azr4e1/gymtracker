package com.lorenzo.gymtracker.data.db

import androidx.room.TypeConverter
import com.lorenzo.gymtracker.data.db.entity.Muscle

/** Muscle lists are stored as comma-separated names ("CHEST,TRICEPS"), readable in exports. */
class Converters {
    @TypeConverter
    fun musclesToString(muscles: List<Muscle>?): String? = muscles?.joinToString(",") { it.name }

    @TypeConverter
    fun stringToMuscles(value: String?): List<Muscle>? =
        value?.split(',')?.mapNotNull { name -> Muscle.entries.firstOrNull { it.name == name } }
}
