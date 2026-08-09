package com.mar.gym.feature.measurements.data

import com.mar.gym.core.network.NetworkFailure
import com.mar.gym.feature.measurements.model.BodyMeasurement
import com.mar.gym.feature.measurements.model.BodyMeasurementDocument
import com.mar.gym.feature.measurements.model.BodyMeasurementDraft
import com.mar.gym.feature.measurements.model.BodyMeasurementPage
import com.mar.gym.feature.measurements.model.BodyMeasurementType
import java.time.Instant

sealed interface MeasurementResult<out T> {
    data class Success<T>(val value: T) : MeasurementResult<T>
    data class Failure(val error: NetworkFailure) : MeasurementResult<Nothing>
}

interface MeasurementRepository {
    suspend fun create(draft: BodyMeasurementDraft, now: Instant): MeasurementResult<BodyMeasurementDocument>
    suspend fun list(type: BodyMeasurementType?, page: Int, size: Int = 20): MeasurementResult<BodyMeasurementPage>
    suspend fun latest(): MeasurementResult<List<BodyMeasurement>>
    suspend fun detail(id: String): MeasurementResult<BodyMeasurementDocument>
    suspend fun update(
        current: BodyMeasurementDocument,
        draft: BodyMeasurementDraft,
        now: Instant,
    ): MeasurementResult<BodyMeasurementDocument>
    suspend fun delete(id: String): MeasurementResult<Unit>
}
