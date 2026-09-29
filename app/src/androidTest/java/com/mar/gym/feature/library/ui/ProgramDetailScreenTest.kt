package com.mar.gym.feature.library.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.mar.gym.feature.library.model.ProgramDay
import com.mar.gym.feature.library.model.ProgramDetail
import com.mar.gym.feature.routines.model.RoutineEtag
import com.mar.gym.ui.theme.GYmAppTheme
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class ProgramDetailScreenTest {
    @get:Rule val composeRule = createComposeRule()

    @Test fun startingDayPassesExistingRoutineId() {
        var started: String? = null
        val now = Instant.parse("2026-09-29T10:00:00Z")
        val routineId = "00000000-0000-0000-0000-000000000002"
        val detail = ProgramDetail("00000000-0000-0000-0000-000000000001", "Plan", null,
            RoutineEtag.fromVersion(0)!!, now, now,
            listOf(ProgramDay("00000000-0000-0000-0000-000000000003", 1, "Lunes", routineId, "Fuerza", false)))
        composeRule.setContent { GYmAppTheme { ProgramDetailScreen(detail, false, { started = it }, {}, {}) } }
        composeRule.onNodeWithText("Lunes").assertExists()
        composeRule.onNodeWithText("Fuerza").assertExists()
        composeRule.onNodeWithText("Iniciar").performClick()
        composeRule.runOnIdle { assertEquals(routineId, started) }
    }
}
