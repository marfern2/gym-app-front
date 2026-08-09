package com.mar.gym.feature.measurements.data

import kotlinx.serialization.Serializable

@Serializable data class BodyMeasurementDto(
    val id: String, val type: String, val value: Double, val unit: String,
    val measuredAt: String, val createdAt: String, val updatedAt: String, val version: Long,
)
@Serializable data class BodyMeasurementWriteDto(val type: String, val value: Double, val measuredAt: String)
@Serializable data class BodyMeasurementPageDto(
    val content: List<BodyMeasurementDto>, val page: Int, val size: Int, val totalElements: Long,
    val totalPages: Int, val first: Boolean, val last: Boolean,
)
