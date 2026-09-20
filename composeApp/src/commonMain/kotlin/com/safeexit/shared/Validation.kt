package com.safeexit.shared

object Validation {
    fun organizationCode(code: String): Result<Unit> = if (code.trim().uppercase() == DEMO_ORGANIZATION_CODE) Result.success(Unit) else Result.failure(IllegalArgumentException("Use the Demo University code SAFE101."))
    fun emergency(location: String, message: String): Result<Unit> = when {
        location.isBlank() -> Result.failure(IllegalArgumentException("Emergency location is required."))
        message.isBlank() -> Result.failure(IllegalArgumentException("Emergency message is required."))
        else -> Result.success(Unit)
    }
}
