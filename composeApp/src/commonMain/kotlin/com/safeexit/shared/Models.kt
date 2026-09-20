package com.safeexit.shared

import kotlinx.serialization.Serializable

@Serializable enum class UserRole { STUDENT, ADMIN }
@Serializable enum class EmergencyType { FIRE, GAS_LEAK, SECURITY, MEDICAL, OTHER }
@Serializable enum class SafetyStatus { UNKNOWN, WAITING, SAFE, NEEDS_HELP }

@Serializable data class UserProfile(
    val uid: String, val displayName: String, val email: String, val role: UserRole,
    val organizationId: String, val studentId: String? = null, val currentArea: String = "",
    val safetyStatus: SafetyStatus = SafetyStatus.UNKNOWN, val lastSeenAt: Long? = null,
    val createdAt: Long? = null
)
@Serializable data class Emergency(
    val id: String, val organizationId: String, val active: Boolean, val type: EmergencyType,
    val locationText: String, val message: String, val createdBy: String, val createdAt: Long? = null,
    val endedAt: Long? = null, val version: Long = 0, val recommendedExit: String? = null, val blockedExits: String? = null
)
@Serializable data class EmergencyAcknowledgement(
    val studentUid: String, val displayName: String, val status: SafetyStatus,
    val currentArea: String, val acknowledgedAt: Long? = null
)

const val DEMO_ORGANIZATION_ID = "demo-university"
const val DEMO_ORGANIZATION_NAME = "Demo University"
const val DEMO_ORGANIZATION_CODE = "SAFE101"
const val SAFETY_DISCLAIMER = "SafeExit is a preparedness prototype. Follow official alarms, exit signs, university instructions, and emergency personnel."
