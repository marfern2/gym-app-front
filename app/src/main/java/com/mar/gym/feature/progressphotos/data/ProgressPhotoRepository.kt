package com.mar.gym.feature.progressphotos.data

import android.net.Uri
import com.mar.gym.core.network.ImagePartSource
import com.mar.gym.core.network.NetworkFailure
import com.mar.gym.core.network.NetworkResponse
import com.mar.gym.core.network.SelectedImageResult
import com.mar.gym.core.network.executeNetworkRequest
import com.mar.gym.core.network.executeNetworkUnitRequest
import com.mar.gym.core.network.mediaUrl
import com.mar.gym.core.units.EditableWeightState
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MultipartBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody

data class ProgressPhotoMedia(val id: String, val url: String, val width: Int, val height: Int)
data class ProgressPhoto(
    val id: String, val takenAt: Instant, val weightKg: BigDecimal?,
    val note: String?, val createdAt: Instant, val media: ProgressPhotoMedia,
)
data class ProgressPhotoPage(val content: List<ProgressPhoto>, val page: Int, val last: Boolean)
data class ProgressPhotoDraft(
    val uri: Uri, val takenAt: Instant, val weightInput: String,
    val weightState: EditableWeightState, val note: String,
) {
    fun canonicalWeight(): BigDecimal? = canonicalProgressPhotoWeight(weightInput, weightState)
    fun validationError(now: Instant): String? = validateProgressPhotoInput(
        takenAt, weightInput, weightState, note, now,
    )
}

internal fun validateProgressPhotoInput(
    takenAt: Instant, weightInput: String, weightState: EditableWeightState,
    note: String, now: Instant,
): String? {
    val input = weightInput.trim().toBigDecimalOrNull()
    val canonical = canonicalProgressPhotoWeight(weightInput, weightState)
    return when {
        takenAt > now -> "La fecha no puede estar en el futuro"
        note.trim().length > 1000 -> "La nota supera 1000 caracteres"
        weightInput.isNotBlank() && (input == null || input <= BigDecimal.ZERO ||
            input.scale().coerceAtLeast(0) > 3 || canonical == null ||
            canonical <= BigDecimal.ZERO || canonical > BigDecimal("500")) -> "Introduce un peso válido"
        else -> null
    }
}

/** The backend accepts at most three decimal places in canonical kilograms. */
internal fun canonicalProgressPhotoWeight(input: String, state: EditableWeightState): BigDecimal? =
    state.canonicalOrNull(input.trim())?.let { kilograms ->
        if (state.dirty) kilograms.setScale(3, RoundingMode.HALF_UP).stripTrailingZeros()
        else kilograms
    }

internal fun normalizedProgressPhotoNote(note: String): String? = note.trim().takeIf(String::isNotEmpty)

sealed interface ProgressPhotoResult<out T> {
    data class Success<T>(val value: T) : ProgressPhotoResult<T>
    data class Error(val message: String) : ProgressPhotoResult<Nothing>
}

interface ProgressPhotoRepository {
    suspend fun list(page: Int, size: Int = 20): ProgressPhotoResult<ProgressPhotoPage>
    suspend fun detail(id: String): ProgressPhotoResult<ProgressPhoto>
    suspend fun upload(draft: ProgressPhotoDraft, now: Instant): ProgressPhotoResult<ProgressPhoto>
    suspend fun delete(id: String): ProgressPhotoResult<Unit>
}

class DefaultProgressPhotoRepository(
    private val api: ProgressPhotoApi, private val parts: ImagePartSource,
) : ProgressPhotoRepository {
    override suspend fun list(page: Int, size: Int): ProgressPhotoResult<ProgressPhotoPage> {
        if (page < 0 || size !in 1..100) return invalid()
        return when (val result = executeNetworkRequest { api.list(page, size) }) {
            is NetworkResponse.Failure -> result.error.safeError()
            is NetworkResponse.Success -> {
                val dto = result.value
                if (dto.page != page || dto.size != size || dto.totalElements < 0 || dto.totalPages < 0) invalid()
                else {
                    val mapped = dto.content.map { it.toDomain() ?: return invalid() }
                    ProgressPhotoResult.Success(ProgressPhotoPage(mapped, page, dto.last))
                }
            }
        }
    }

    override suspend fun detail(id: String): ProgressPhotoResult<ProgressPhoto> {
        if (!id.isUuid()) return invalid()
        return when (val result = executeNetworkRequest { api.detail(id) }) {
            is NetworkResponse.Failure -> result.error.safeError()
            is NetworkResponse.Success -> result.value.toDomain()?.takeIf { it.id == id }
                ?.let { ProgressPhotoResult.Success(it) } ?: invalid()
        }
    }

    override suspend fun upload(draft: ProgressPhotoDraft, now: Instant): ProgressPhotoResult<ProgressPhoto> {
        draft.validationError(now)?.let { return ProgressPhotoResult.Error(it) }
        val part = withContext(Dispatchers.IO) { parts.from(draft.uri, 10_000_000) }
        if (part !is SelectedImageResult.Ready) return ProgressPhotoResult.Error(when (part) {
            SelectedImageResult.TooLarge -> "Imagen demasiado grande"
            SelectedImageResult.Unsupported -> "Formato no soportado"
            else -> "Imagen inválida"
        })
        return uploadPrepared(part.part, draft.takenAt, draft.canonicalWeight(),
            normalizedProgressPhotoNote(draft.note))
    }

    internal suspend fun uploadPrepared(
        file: MultipartBody.Part, takenAt: Instant, weightKg: BigDecimal?, note: String?,
    ): ProgressPhotoResult<ProgressPhoto> {
        val text = "text/plain".toMediaType()
        return when (val result = executeNetworkRequest {
            api.upload(
                file, takenAt.toString().toRequestBody(text),
                weightKg?.stripTrailingZeros()?.toPlainString()?.toRequestBody(text),
                note?.toRequestBody(text),
            )
        }) {
            is NetworkResponse.Failure -> result.error.safeError()
            is NetworkResponse.Success -> result.value.toDomain()?.let { ProgressPhotoResult.Success(it) } ?: invalid()
        }
    }

    override suspend fun delete(id: String): ProgressPhotoResult<Unit> {
        if (!id.isUuid()) return invalid()
        return when (val result = executeNetworkUnitRequest { api.delete(id) }) {
            is NetworkResponse.Failure -> result.error.safeError()
            is NetworkResponse.Success -> ProgressPhotoResult.Success(Unit)
        }
    }
}

private fun ProgressPhotoDto.toDomain(): ProgressPhoto? {
    if (!id.isUuid() || !media.id.isUuid() || media.width <= 0 || media.height <= 0 ||
        note?.length?.let { it > 1000 } == true ||
        weightKg?.let { !it.isFinite() || it <= 0 || it > 500 } == true) return null
    val taken = runCatching { Instant.parse(takenAt) }.getOrNull() ?: return null
    val created = runCatching { Instant.parse(createdAt) }.getOrNull() ?: return null
    val url = mediaUrl(media.url ?: "/api/v1/media/${media.id}") ?: return null
    return ProgressPhoto(id, taken, weightKg?.let(BigDecimal::valueOf), note, created,
        ProgressPhotoMedia(media.id, url, media.width, media.height))
}

private fun String.isUuid() = runCatching { UUID.fromString(this) }.isSuccess
private fun <T> invalid(): ProgressPhotoResult<T> = ProgressPhotoResult.Error("Datos de foto no válidos")
private fun NetworkFailure.safeError(): ProgressPhotoResult.Error = ProgressPhotoResult.Error(when (this) {
    is NetworkFailure.Network, is NetworkFailure.Timeout -> "Error de red. Inténtalo de nuevo"
    is NetworkFailure.HttpProblem -> when (statusCode) {
        404 -> "Foto no disponible"
        413 -> "Imagen demasiado grande"
        415 -> "Formato no soportado"
        else -> "No se pudo completar la operación"
    }
    is NetworkFailure.HttpUnknown -> if (statusCode == 404) "Foto no disponible" else "No se pudo completar la operación"
    else -> "No se pudo completar la operación"
})
