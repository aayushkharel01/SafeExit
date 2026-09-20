package com.safeexit.shared

fun List<UserProfile>.filterFor(filter: StudentFilter): List<UserProfile> = filter { user ->
    when (filter) {
        StudentFilter.ALL -> true
        StudentFilter.WAITING -> user.safetyStatus == SafetyStatus.WAITING
        StudentFilter.SAFE -> user.safetyStatus == SafetyStatus.SAFE
        StudentFilter.NEEDS_HELP -> user.safetyStatus == SafetyStatus.NEEDS_HELP
        StudentFilter.NOT_ACKNOWLEDGED -> user.safetyStatus == SafetyStatus.UNKNOWN || user.safetyStatus == SafetyStatus.WAITING
    }
}
enum class StudentFilter { ALL, WAITING, SAFE, NEEDS_HELP, NOT_ACKNOWLEDGED }
fun UserProfile.isRecentlyActive(now: Long = currentTimeMillis()): Boolean = lastSeenAt?.let { now - it <= 120_000 } == true
expect fun currentTimeMillis(): Long
