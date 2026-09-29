package com.mar.gym.feature.library.model

import com.mar.gym.feature.routines.model.RoutineEtag
import java.time.Instant

data class RoutineFolder(val id: String, val name: String, val position: Int, val routineCount: Long, val createdAt: Instant, val updatedAt: Instant)
data class LibraryRoutine(val id: String, val folderId: String?, val position: Int, val name: String, val description: String?, val archived: Boolean, val exerciseCount: Int, val etag: RoutineEtag, val updatedAt: Instant)
data class ProgramSummary(val id: String, val name: String, val description: String?, val daysCount: Long, val updatedAt: Instant, val version: Long)
data class ProgramDay(val id: String, val position: Int, val label: String?, val routineId: String, val routineName: String, val routineArchived: Boolean)
data class ProgramDetail(val id: String, val name: String, val description: String?, val etag: RoutineEtag, val createdAt: Instant, val updatedAt: Instant, val days: List<ProgramDay>)
data class LibraryResponse(val folders: List<RoutineFolder>, val routines: List<LibraryRoutine>, val programs: List<ProgramSummary>)
data class ProgramDayInput(val routineId: String, val label: String? = null)
data class ProgramInput(val name: String, val description: String?, val days: List<ProgramDayInput>)
