package com.mar.gym.feature.measurements.data

import com.mar.gym.core.network.EntityNetworkResponse
import com.mar.gym.core.network.EntityTag
import com.mar.gym.core.network.NetworkFailure
import com.mar.gym.core.network.NetworkResponse
import com.mar.gym.core.network.VersionedDocument
import com.mar.gym.core.network.executeNetworkEntityRequest
import com.mar.gym.core.network.executeNetworkRequest
import com.mar.gym.core.network.executeNetworkUnitRequest
import com.mar.gym.feature.measurements.model.BodyMeasurement
import com.mar.gym.feature.measurements.model.BodyMeasurementDocument
import com.mar.gym.feature.measurements.model.BodyMeasurementDraft
import com.mar.gym.feature.measurements.model.BodyMeasurementPage
import com.mar.gym.feature.measurements.model.BodyMeasurementType
import com.mar.gym.feature.measurements.model.BodyMeasurementUnit
import com.mar.gym.feature.measurements.model.validate
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

class DefaultMeasurementRepository(private val api: MeasurementApi) : MeasurementRepository {
    override suspend fun create(draft: BodyMeasurementDraft, now: Instant) =
        if (draft.validate(now).isNotEmpty()) invalid() else entity { api.create(draft.dto()) }

    override suspend fun list(type: BodyMeasurementType?, page: Int, size: Int): MeasurementResult<BodyMeasurementPage> {
        if (page < 0 || size !in 1..100) return invalid()
        return when (val response = executeNetworkRequest { api.list(type?.apiValue, page, size) }) {
            is NetworkResponse.Failure -> MeasurementResult.Failure(response.error)
            is NetworkResponse.Success -> response.value.toDomain()?.let { MeasurementResult.Success(it) }
                ?: invalid(response.correlationId)
        }
    }

    override suspend fun latest(): MeasurementResult<List<BodyMeasurement>> =
        when (val response = executeNetworkRequest(api::latest)) {
            is NetworkResponse.Failure -> MeasurementResult.Failure(response.error)
            is NetworkResponse.Success -> response.value.map { it.toDomain() ?: return invalid(response.correlationId) }
                .takeIf { it.map(BodyMeasurement::type).distinct().size == it.size }
                ?.let { MeasurementResult.Success(it) } ?: invalid(response.correlationId)
        }

    override suspend fun detail(id: String) = if (!id.isUuid()) invalid() else entity { api.detail(id) }

    override suspend fun update(current: BodyMeasurementDocument, draft: BodyMeasurementDraft, now: Instant) =
        if (draft.validate(now).isNotEmpty()) invalid()
        else entity { api.update(current.value.id, current.etag.headerValue, draft.dto()) }

    override suspend fun delete(id: String): MeasurementResult<Unit> {
        if (!id.isUuid()) return invalid()
        return when (val response = executeNetworkUnitRequest { api.delete(id) }) {
            is NetworkResponse.Failure -> MeasurementResult.Failure(response.error)
            is NetworkResponse.Success -> MeasurementResult.Success(Unit)
        }
    }

    private suspend fun entity(call: suspend () -> retrofit2.Response<BodyMeasurementDto>): MeasurementResult<BodyMeasurementDocument> =
        when (val response = executeNetworkEntityRequest(call)) {
            is EntityNetworkResponse.Failure -> MeasurementResult.Failure(response.error)
            is EntityNetworkResponse.Success -> {
                val measurement = response.value.toDomain() ?: return invalid(response.correlationId)
                val etag = EntityTag.parse(response.etag)?.takeIf { it.version == measurement.version }
                    ?: return invalid(response.correlationId)
                MeasurementResult.Success(VersionedDocument(measurement, etag))
            }
        }

    private fun BodyMeasurementDraft.dto() = BodyMeasurementWriteDto(
        type.apiValue, value.toDouble(), measuredAt.toString(),
    )

    private fun BodyMeasurementPageDto.toDomain(): BodyMeasurementPage? {
        if (page < 0 || size !in 1..100 || totalElements < 0 || totalPages < 0) return null
        val mapped = content.map { it.toDomain() ?: return null }
        return BodyMeasurementPage(mapped, page, size, totalElements, totalPages, first, last)
    }

    private fun BodyMeasurementDto.toDomain(): BodyMeasurement? {
        if (!id.isUuid() || !value.isFinite() || value <= 0 || version < 0) return null
        val mappedType = BodyMeasurementType.fromApiValue(type) ?: return null
        val mappedUnit = BodyMeasurementUnit.fromApiValue(unit)?.takeIf { it == mappedType.unit } ?: return null
        val measured = measuredAt.instant() ?: return null
        val created = createdAt.instant() ?: return null
        val updated = updatedAt.instant() ?: return null
        if (updated < created) return null
        return BodyMeasurement(id, mappedType, BigDecimal.valueOf(value), mappedUnit, measured, created, updated, version)
    }

    private fun String.instant() = runCatching { Instant.parse(this) }.getOrNull()
    private fun String.isUuid() = runCatching { UUID.fromString(this) }.isSuccess
    private fun <T> invalid(correlationId: String? = null): MeasurementResult<T> =
        MeasurementResult.Failure(NetworkFailure.InvalidResponse(correlationId))
}
