package com.mar.gym.feature.exercises.data

import android.content.Context
import com.mar.gym.core.units.WeightUnit

/** Settings belong to one signed-in user and one exercise template. */
interface ExerciseLocalSettings {
    fun note(userId: String, exerciseId: String): String?
    fun weightUnit(userId: String, exerciseId: String): WeightUnit?
    fun saveNote(userId: String, exerciseId: String, note: String?)
    fun saveWeightUnit(userId: String, exerciseId: String, unit: WeightUnit?)
}

class SharedPreferenceExerciseLocalSettings(context: Context) : ExerciseLocalSettings {
    private val preferences = context.applicationContext.getSharedPreferences(
        "exercise_local_settings", Context.MODE_PRIVATE,
    )

    private fun key(userId: String, exerciseId: String, field: String) =
        "$userId:$exerciseId:$field"

    override fun note(userId: String, exerciseId: String): String? =
        preferences.getString(key(userId, exerciseId, "note"), null)

    override fun weightUnit(userId: String, exerciseId: String): WeightUnit? =
        preferences.getString(key(userId, exerciseId, "unit"), null)
            ?.let { value -> WeightUnit.entries.find { it.apiValue == value } }

    override fun saveNote(userId: String, exerciseId: String, note: String?) {
        preferences.edit().apply {
            val key = key(userId, exerciseId, "note")
            if (note.isNullOrBlank()) remove(key) else putString(key, note.trim())
        }.apply()
    }

    override fun saveWeightUnit(userId: String, exerciseId: String, unit: WeightUnit?) {
        preferences.edit().apply {
            val key = key(userId, exerciseId, "unit")
            if (unit == null) remove(key) else putString(key, unit.apiValue)
        }.apply()
    }
}
