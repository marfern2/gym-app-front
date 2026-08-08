package com.mar.gym.feature.exercises.ui

import androidx.annotation.StringRes
import com.mar.gym.R
import com.mar.gym.feature.exercises.model.Equipment
import com.mar.gym.feature.exercises.model.ExerciseSort
import com.mar.gym.feature.exercises.model.ExerciseTemplateSource
import com.mar.gym.feature.exercises.model.ExerciseType
import com.mar.gym.feature.exercises.model.MovementPattern
import com.mar.gym.feature.exercises.model.MuscleGroup

@StringRes
fun MuscleGroup.labelResource(): Int = when (this) {
    MuscleGroup.Chest -> R.string.exercise_muscle_chest
    MuscleGroup.Back -> R.string.exercise_muscle_back
    MuscleGroup.Shoulders -> R.string.exercise_muscle_shoulders
    MuscleGroup.Biceps -> R.string.exercise_muscle_biceps
    MuscleGroup.Triceps -> R.string.exercise_muscle_triceps
    MuscleGroup.Forearms -> R.string.exercise_muscle_forearms
    MuscleGroup.Quadriceps -> R.string.exercise_muscle_quadriceps
    MuscleGroup.Hamstrings -> R.string.exercise_muscle_hamstrings
    MuscleGroup.Glutes -> R.string.exercise_muscle_glutes
    MuscleGroup.Calves -> R.string.exercise_muscle_calves
    MuscleGroup.Core -> R.string.exercise_muscle_core
    MuscleGroup.FullBody -> R.string.exercise_muscle_full_body
    MuscleGroup.Cardio -> R.string.exercise_muscle_cardio
    MuscleGroup.Other -> R.string.exercise_label_other
}

@StringRes
fun Equipment.labelResource(): Int = when (this) {
    Equipment.None -> R.string.exercise_equipment_none
    Equipment.Barbell -> R.string.exercise_equipment_barbell
    Equipment.Dumbbell -> R.string.exercise_equipment_dumbbell
    Equipment.Kettlebell -> R.string.exercise_equipment_kettlebell
    Equipment.Machine -> R.string.exercise_equipment_machine
    Equipment.Cable -> R.string.exercise_equipment_cable
    Equipment.ResistanceBand -> R.string.exercise_equipment_resistance_band
    Equipment.Bodyweight -> R.string.exercise_equipment_bodyweight
    Equipment.PullUpBar -> R.string.exercise_equipment_pull_up_bar
    Equipment.Bench -> R.string.exercise_equipment_bench
    Equipment.CardioMachine -> R.string.exercise_equipment_cardio_machine
    Equipment.Other -> R.string.exercise_label_other
}

@StringRes
fun ExerciseType.labelResource(): Int = when (this) {
    ExerciseType.WeightReps -> R.string.exercise_type_weight_reps
    ExerciseType.BodyweightReps -> R.string.exercise_type_bodyweight_reps
    ExerciseType.WeightedBodyweight -> R.string.exercise_type_weighted_bodyweight
    ExerciseType.AssistedBodyweight -> R.string.exercise_type_assisted_bodyweight
    ExerciseType.Duration -> R.string.exercise_type_duration
    ExerciseType.DistanceDuration -> R.string.exercise_type_distance_duration
    ExerciseType.WeightDistance -> R.string.exercise_type_weight_distance
}

@StringRes
fun MovementPattern.labelResource(): Int = when (this) {
    MovementPattern.HorizontalPush -> R.string.exercise_pattern_horizontal_push
    MovementPattern.HorizontalPull -> R.string.exercise_pattern_horizontal_pull
    MovementPattern.VerticalPush -> R.string.exercise_pattern_vertical_push
    MovementPattern.VerticalPull -> R.string.exercise_pattern_vertical_pull
    MovementPattern.Squat -> R.string.exercise_pattern_squat
    MovementPattern.Hinge -> R.string.exercise_pattern_hinge
    MovementPattern.Lunge -> R.string.exercise_pattern_lunge
    MovementPattern.Carry -> R.string.exercise_pattern_carry
    MovementPattern.Rotation -> R.string.exercise_pattern_rotation
    MovementPattern.AntiRotation -> R.string.exercise_pattern_anti_rotation
    MovementPattern.AntiExtension -> R.string.exercise_pattern_anti_extension
    MovementPattern.Flexion -> R.string.exercise_pattern_flexion
    MovementPattern.Extension -> R.string.exercise_pattern_extension
    MovementPattern.Locomotion -> R.string.exercise_pattern_locomotion
    MovementPattern.Isolation -> R.string.exercise_pattern_isolation
    MovementPattern.Other -> R.string.exercise_label_other
}

@StringRes
fun ExerciseSort.labelResource(): Int = when (this) {
    ExerciseSort.NameAscending -> R.string.exercise_sort_name_ascending
    ExerciseSort.NameDescending -> R.string.exercise_sort_name_descending
    ExerciseSort.PrimaryMuscleGroupAscending -> R.string.exercise_sort_muscle_ascending
    ExerciseSort.EquipmentAscending -> R.string.exercise_sort_equipment_ascending
    ExerciseSort.ExerciseTypeAscending -> R.string.exercise_sort_type_ascending
}

@StringRes
fun ExerciseTemplateSource.labelResource(): Int = when (this) {
    ExerciseTemplateSource.Global -> R.string.exercise_source_global
    ExerciseTemplateSource.Custom -> R.string.exercise_source_custom
}

@StringRes
fun ExerciseUiErrorKind.messageResource(): Int = when (this) {
    ExerciseUiErrorKind.Network -> R.string.exercise_error_network
    ExerciseUiErrorKind.Timeout -> R.string.exercise_error_timeout
    ExerciseUiErrorKind.Unauthorized -> R.string.exercise_error_unauthorized
    ExerciseUiErrorKind.NotFound -> R.string.exercise_error_not_found
    ExerciseUiErrorKind.Forbidden -> R.string.exercise_error_forbidden
    ExerciseUiErrorKind.Validation -> R.string.exercise_error_validation
    ExerciseUiErrorKind.Conflict -> R.string.exercise_error_conflict
    ExerciseUiErrorKind.NameConflict -> R.string.exercise_error_name_conflict
    ExerciseUiErrorKind.Archived -> R.string.exercise_error_archived
    ExerciseUiErrorKind.InvalidResponse -> R.string.exercise_error_invalid_response
    ExerciseUiErrorKind.Server -> R.string.exercise_error_server
    ExerciseUiErrorKind.Unknown -> R.string.exercise_error_unknown
}
