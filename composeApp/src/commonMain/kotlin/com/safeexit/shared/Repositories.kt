package com.safeexit.shared

import kotlinx.coroutines.flow.Flow

interface AuthenticationRepository {
    suspend fun signIn(email: String, password: String): Result<UserProfile>
    suspend fun registerStudent(displayName: String, email: String, password: String, organizationCode: String, studentId: String?): Result<UserProfile>
    fun signOut()
    suspend fun currentUser(): UserProfile?
}
interface EmergencyRepository {
    fun observeActiveEmergency(organizationId: String): Flow<Emergency?>
    suspend fun createEmergency(organizationId: String, type: EmergencyType, locationText: String, message: String): Result<Emergency>
    suspend fun endEmergency(emergencyId: String): Result<Unit>
    suspend fun acknowledgeEmergency(emergencyId: String, status: SafetyStatus, currentArea: String): Result<Unit>
}
interface UserRepository {
    fun observeStudents(organizationId: String): Flow<List<UserProfile>>
    suspend fun updateCurrentArea(currentArea: String): Result<Unit>
    suspend fun updateSafetyStatus(status: SafetyStatus): Result<Unit>
    suspend fun updateLastSeen(): Result<Unit>
}

data class SafeExitRepositories(val auth: AuthenticationRepository, val emergencies: EmergencyRepository, val users: UserRepository)
