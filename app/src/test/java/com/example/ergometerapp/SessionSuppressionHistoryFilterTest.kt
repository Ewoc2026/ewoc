package com.example.ergometerapp

import com.example.ergometerapp.ai.AiPhase
import com.example.ergometerapp.ai.AiPresentedMessageRecord
import com.example.ergometerapp.ai.AiRecentEmission
import com.example.ergometerapp.ai.AiRecommendationType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionSuppressionHistoryFilterTest {
    @Test
    fun filterSessionSuppressionHistoryByType_removesOnlySessionCadenceEntries() {
        val emissions = listOf(
            AiRecentEmission(type = AiRecommendationType.CADENCE, emittedAtMillis = 1000L),
            AiRecentEmission(type = AiRecommendationType.PACING, emittedAtMillis = 1200L),
            AiRecentEmission(type = AiRecommendationType.SAFETY, emittedAtMillis = 1400L),
        )
        val presented = listOf(
            AiPresentedMessageRecord(
                phase = AiPhase.SESSION,
                type = AiRecommendationType.CADENCE,
                presentedAtMillis = 1000L,
            ),
            AiPresentedMessageRecord(
                phase = AiPhase.SESSION,
                type = AiRecommendationType.PACING,
                presentedAtMillis = 1200L,
            ),
            AiPresentedMessageRecord(
                phase = AiPhase.MENU,
                type = AiRecommendationType.CADENCE,
                presentedAtMillis = 1300L,
            ),
        )

        val filtered = filterSessionSuppressionHistoryByType(
            recentEmissions = emissions,
            recentPresented = presented,
            type = AiRecommendationType.CADENCE,
        )

        assertEquals(2, filtered.recentEmissions.size)
        assertTrue(filtered.recentEmissions.none { it.type == AiRecommendationType.CADENCE })
        assertEquals(2, filtered.recentPresented.size)
        assertTrue(
            filtered.recentPresented.none {
                it.phase == AiPhase.SESSION && it.type == AiRecommendationType.CADENCE
            },
        )
        assertTrue(
            filtered.recentPresented.any {
                it.phase == AiPhase.MENU && it.type == AiRecommendationType.CADENCE
            },
        )
    }
}
