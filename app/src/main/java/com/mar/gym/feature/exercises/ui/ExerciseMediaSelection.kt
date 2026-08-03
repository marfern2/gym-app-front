package com.mar.gym.feature.exercises.ui

import com.mar.gym.feature.exercises.model.ExerciseMedia
import com.mar.gym.feature.exercises.model.ExerciseMediaRole
import com.mar.gym.feature.exercises.model.ExerciseMediaType

fun List<ExerciseMedia>.selectDemonstrationMedia(): ExerciseMedia? = withIndex()
    .filter { it.value.role == ExerciseMediaRole.Demonstration }
    .filter { it.value.type in SUPPORTED_DEMONSTRATION_TYPES }
    .minWithOrNull(
        compareBy<IndexedValue<ExerciseMedia>>(
            { SUPPORTED_DEMONSTRATION_TYPES.indexOf(it.value.type) },
            IndexedValue<ExerciseMedia>::index,
        )
    )
    ?.value

private val SUPPORTED_DEMONSTRATION_TYPES = listOf(
    ExerciseMediaType.AnimatedGif,
    ExerciseMediaType.Image,
)
