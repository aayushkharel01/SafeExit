package com.safeexit.shared

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ValidationTest {
    @Test fun invalidOrganizationCodeRejected() { assertTrue(Validation.organizationCode("WRONG").isFailure) }
    @Test fun emptyEmergencyFieldsRejected() {
        assertTrue(Validation.emergency("", "message").isFailure)
        assertTrue(Validation.emergency("Room 101", "").isFailure)
    }
    @Test fun filtersWork() {
        val users = listOf(
            UserProfile("1", "A", "a", UserRole.STUDENT, DEMO_ORGANIZATION_ID, safetyStatus = SafetyStatus.SAFE),
            UserProfile("2", "B", "b", UserRole.STUDENT, DEMO_ORGANIZATION_ID, safetyStatus = SafetyStatus.WAITING),
            UserProfile("3", "C", "c", UserRole.STUDENT, DEMO_ORGANIZATION_ID, safetyStatus = SafetyStatus.NEEDS_HELP)
        )
        assertEquals(1, users.filterFor(StudentFilter.SAFE).size)
        assertEquals(1, users.filterFor(StudentFilter.WAITING).size)
        assertEquals(1, users.filterFor(StudentFilter.NEEDS_HELP).size)
        assertEquals(1, users.filterFor(StudentFilter.NOT_ACKNOWLEDGED).size)
    }
}
