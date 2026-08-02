package com.mar.gym.feature.exercises.model

data class ExerciseTemplateSummary(
    val id: String,
    val slug: String,
    val name: String,
    val primaryMuscleGroup: MuscleGroup,
    val equipment: Equipment,
    val exerciseType: ExerciseType,
    val movementPattern: MovementPattern,
)

data class ExerciseTemplateDetail(
    val id: String,
    val slug: String,
    val name: String,
    val description: String?,
    val primaryMuscleGroup: MuscleGroup,
    val secondaryMuscleGroups: List<MuscleGroup>,
    val equipment: Equipment,
    val exerciseType: ExerciseType,
    val movementPattern: MovementPattern,
    val instructions: List<ExerciseInstruction>,
)

data class ExerciseInstruction(
    val position: Int,
    val text: String,
)

data class ExerciseTemplatePage(
    val content: List<ExerciseTemplateSummary>,
    val page: Int,
    val size: Int,
    val totalElements: Long,
    val totalPages: Int,
    val first: Boolean,
    val last: Boolean,
)

data class ExerciseFilters(
    val primaryMuscleGroup: MuscleGroup? = null,
    val equipment: Equipment? = null,
    val exerciseType: ExerciseType? = null,
    val movementPattern: MovementPattern? = null,
) {
    val activeCount: Int
        get() = listOfNotNull(
            primaryMuscleGroup,
            equipment,
            exerciseType,
            movementPattern,
        ).size
}

enum class ExerciseSort(val apiValue: String) {
    NameAscending("name,asc"),
    NameDescending("name,desc"),
    PrimaryMuscleGroupAscending("primaryMuscleGroup,asc"),
    EquipmentAscending("equipment,asc"),
    ExerciseTypeAscending("exerciseType,asc"),
}

enum class MuscleGroup(val apiValue: String) {
    Chest("CHEST"),
    Back("BACK"),
    Shoulders("SHOULDERS"),
    Biceps("BICEPS"),
    Triceps("TRICEPS"),
    Forearms("FOREARMS"),
    Quadriceps("QUADRICEPS"),
    Hamstrings("HAMSTRINGS"),
    Glutes("GLUTES"),
    Calves("CALVES"),
    Core("CORE"),
    FullBody("FULL_BODY"),
    Cardio("CARDIO"),
    Other("OTHER");

    companion object {
        fun fromApiValue(value: String): MuscleGroup? = entries.find { it.apiValue == value }
    }
}

enum class Equipment(val apiValue: String) {
    None("NONE"),
    Barbell("BARBELL"),
    Dumbbell("DUMBBELL"),
    Kettlebell("KETTLEBELL"),
    Machine("MACHINE"),
    Cable("CABLE"),
    ResistanceBand("RESISTANCE_BAND"),
    Bodyweight("BODYWEIGHT"),
    PullUpBar("PULL_UP_BAR"),
    Bench("BENCH"),
    CardioMachine("CARDIO_MACHINE"),
    Other("OTHER");

    companion object {
        fun fromApiValue(value: String): Equipment? = entries.find { it.apiValue == value }
    }
}

enum class ExerciseType(val apiValue: String) {
    WeightReps("WEIGHT_REPS"),
    BodyweightReps("BODYWEIGHT_REPS"),
    WeightedBodyweight("WEIGHTED_BODYWEIGHT"),
    AssistedBodyweight("ASSISTED_BODYWEIGHT"),
    Duration("DURATION"),
    DistanceDuration("DISTANCE_DURATION"),
    WeightDistance("WEIGHT_DISTANCE");

    companion object {
        fun fromApiValue(value: String): ExerciseType? = entries.find { it.apiValue == value }
    }
}

enum class MovementPattern(val apiValue: String) {
    HorizontalPush("HORIZONTAL_PUSH"),
    HorizontalPull("HORIZONTAL_PULL"),
    VerticalPush("VERTICAL_PUSH"),
    VerticalPull("VERTICAL_PULL"),
    Squat("SQUAT"),
    Hinge("HINGE"),
    Lunge("LUNGE"),
    Carry("CARRY"),
    Rotation("ROTATION"),
    AntiRotation("ANTI_ROTATION"),
    AntiExtension("ANTI_EXTENSION"),
    Flexion("FLEXION"),
    Extension("EXTENSION"),
    Locomotion("LOCOMOTION"),
    Isolation("ISOLATION"),
    Other("OTHER");

    companion object {
        fun fromApiValue(value: String): MovementPattern? = entries.find { it.apiValue == value }
    }
}

enum class ExerciseSelectionMode {
    Single,
    Multiple,
}

data class ExercisePickerConfig(
    val selectionMode: ExerciseSelectionMode,
    val initiallySelectedIds: Set<String> = emptySet(),
)

data class ExercisePickerResult(
    val selectedExerciseTemplateIds: Set<String>,
)

sealed interface ExercisePickerOutcome {
    data class Confirmed(val result: ExercisePickerResult) : ExercisePickerOutcome

    data object Cancelled : ExercisePickerOutcome
}
