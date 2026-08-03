package com.mar.gym.feature.exercises.model

import java.net.URI

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
    val media: List<ExerciseMedia> = emptyList(),
)

data class ExerciseInstruction(
    val position: Int,
    val text: String,
)

data class ExerciseMedia(
    val type: ExerciseMediaType,
    val role: ExerciseMediaRole,
    val url: HttpsUrl,
    val width: Int?,
    val height: Int?,
    val attribution: ExerciseMediaAttribution?,
)

data class ExerciseMediaAttribution(
    val text: String,
    val url: HttpsUrl?,
)

class HttpsUrl private constructor(val value: String) {
    override fun equals(other: Any?): Boolean = other is HttpsUrl && value == other.value

    override fun hashCode(): Int = value.hashCode()

    override fun toString(): String = "HttpsUrl(redacted)"

    companion object {
        fun parse(value: String): HttpsUrl? {
            if (value != value.trim() || value.any(Char::isWhitespace)) return null
            val uri = runCatching { URI(value) }.getOrNull() ?: return null
            if (!uri.scheme.equals("https", ignoreCase = true) || uri.host.isNullOrBlank()) {
                return null
            }
            return HttpsUrl(value)
        }
    }
}

enum class ExerciseMediaType(val apiValue: String) {
    Image("IMAGE"),
    AnimatedGif("ANIMATED_GIF"),
    Video("VIDEO");

    companion object {
        fun fromApiValue(value: String): ExerciseMediaType? = entries.find { it.apiValue == value }
    }
}

enum class ExerciseMediaRole(val apiValue: String) {
    Thumbnail("THUMBNAIL"),
    Demonstration("DEMONSTRATION"),
    StartPosition("START_POSITION"),
    EndPosition("END_POSITION");

    companion object {
        fun fromApiValue(value: String): ExerciseMediaRole? = entries.find { it.apiValue == value }
    }
}

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
