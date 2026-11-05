package com.example.pleosproj.ui.permissions

import org.junit.Assert.*
import org.junit.Test

class AppAccessTest {
    @Test fun firstRunRequestsAllThreeAccesses() {
        val statuses = AppAccess.entries.map { AccessStatus(it, false, false, false) }
        assertEquals(listOf(AppAccess.SPEED, AppAccess.LOCATION, AppAccess.MICROPHONE), initialAccessRequests(statuses))
        assertTrue(statuses.all { it.action == AccessAction.REQUEST })
    }

    @Test fun updateOnlyPromptsNewPermissionsAndPreservesEarlierSpeedDenial() {
        val statuses = listOf(
            AccessStatus(AppAccess.SPEED, false, true, false),
            AccessStatus(AppAccess.LOCATION, false, false, false),
            AccessStatus(AppAccess.MICROPHONE, true, false, false),
        )
        assertEquals(listOf(AppAccess.LOCATION), initialAccessRequests(statuses))
        assertEquals(AccessAction.SETTINGS, statuses.first().action)
        assertEquals(AccessAction.NONE, statuses.last().action)
    }

    @Test fun denialAllowsManualRetryButDoesNotPromptAgainOnRestart() {
        val status = AccessStatus(AppAccess.MICROPHONE, false, true, true)
        assertEquals(AccessAction.REQUEST, status.action)
        assertTrue(initialAccessRequests(listOf(status)).isEmpty())
    }

    @Test fun unavailableSystemPromptOffersSettingsUntilPermissionIsGranted() {
        val status = AccessStatus(AppAccess.MICROPHONE, false, true, false)
        assertEquals(AccessAction.SETTINGS, status.action)
        assertEquals(AccessAction.NONE, status.copy(granted = true).action)
        assertEquals("허용됨", status.copy(granted = true).description)
    }

    @Test fun approximateLocationIsVisibleAndDoesNotPretendPreciseAccessWasGranted() {
        val status = AccessStatus(AppAccess.LOCATION, false, true, true, approximateLocation = true)
        assertFalse(status.granted)
        assertEquals("대략적인 위치만 허용됨", status.description)
        assertEquals(AccessAction.REQUEST, status.action)
        assertEquals(listOf("android.permission.ACCESS_FINE_LOCATION", "android.permission.ACCESS_COARSE_LOCATION"),
            status.access.permissions)
    }
}
