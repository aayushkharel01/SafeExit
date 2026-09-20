package com.safeexit.app

import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.safeexit.shared.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

private fun Map<String, Any?>.profile(): UserProfile = UserProfile(
    uid = this["uid"] as? String ?: "", displayName = this["displayName"] as? String ?: "",
    email = this["email"] as? String ?: "", role = runCatching { UserRole.valueOf(this["role"] as? String ?: "STUDENT") }.getOrDefault(UserRole.STUDENT),
    organizationId = this["organizationId"] as? String ?: "", studentId = this["studentId"] as? String,
    currentArea = this["currentArea"] as? String ?: "", safetyStatus = runCatching { SafetyStatus.valueOf(this["safetyStatus"] as? String ?: "UNKNOWN") }.getOrDefault(SafetyStatus.UNKNOWN),
    lastSeenAt = (this["lastSeenAt"] as? Timestamp)?.toDate()?.time, createdAt = (this["createdAt"] as? Timestamp)?.toDate()?.time
)
private fun Map<String, Any?>.emergency(id: String) = Emergency(id, this["organizationId"] as? String ?: "", this["active"] as? Boolean ?: false,
    runCatching { EmergencyType.valueOf(this["type"] as? String ?: "OTHER") }.getOrDefault(EmergencyType.OTHER), this["locationText"] as? String ?: "", this["message"] as? String ?: "", this["createdBy"] as? String ?: "",
    (this["createdAt"] as? Timestamp)?.toDate()?.time, (this["endedAt"] as? Timestamp)?.toDate()?.time, (this["version"] as? Number)?.toLong() ?: 0, this["recommendedExit"] as? String, this["blockedExits"] as? String)

class FirebaseAuthRepository(private val auth: FirebaseAuth = FirebaseAuth.getInstance(), private val db: FirebaseFirestore = FirebaseFirestore.getInstance()) : AuthenticationRepository {
    override suspend fun signIn(email: String, password: String): Result<UserProfile> = runCatching {
        val result = auth.signInWithEmailAndPassword(email.trim(), password).await()
        db.collection("users").document(result.user!!.uid).get().await().data!!.profile()
    }
    override suspend fun registerStudent(displayName: String, email: String, password: String, organizationCode: String, studentId: String?): Result<UserProfile> = runCatching {
        Validation.organizationCode(organizationCode).getOrThrow()
        val result = auth.createUserWithEmailAndPassword(email.trim(), password).await()
        val uid = result.user!!.uid
        val profile = UserProfile(uid, displayName.trim(), email.trim(), UserRole.STUDENT, DEMO_ORGANIZATION_ID, studentId?.trim()?.ifBlank { null }, lastSeenAt = currentTimeMillis(), createdAt = currentTimeMillis())
        db.collection("users").document(uid).set(mapOf("uid" to uid, "displayName" to profile.displayName, "email" to profile.email, "role" to "STUDENT", "organizationId" to profile.organizationId, "studentId" to profile.studentId, "currentArea" to "", "safetyStatus" to "UNKNOWN", "lastSeenAt" to com.google.firebase.firestore.FieldValue.serverTimestamp(), "createdAt" to com.google.firebase.firestore.FieldValue.serverTimestamp())).await()
        profile
    }
    override fun signOut() { auth.signOut() }
    suspend fun sendPasswordReset(email: String): Result<Unit> = runCatching { auth.sendPasswordResetEmail(email.trim()).await(); Unit }
    override suspend fun currentUser(): UserProfile? = auth.currentUser?.let { db.collection("users").document(it.uid).get().await().data?.profile() }
}

class FirebaseUserRepository(private val db: FirebaseFirestore = FirebaseFirestore.getInstance(), private val auth: FirebaseAuth = FirebaseAuth.getInstance()) : UserRepository {
    override fun observeStudents(organizationId: String): Flow<List<UserProfile>> = callbackFlow {
        val registration = db.collection("users").whereEqualTo("organizationId", organizationId).whereEqualTo("role", "STUDENT").addSnapshotListener { snapshot, error ->
            if (error != null) close(error) else trySend(snapshot?.documents?.mapNotNull { it.data?.profile() } ?: emptyList())
        }; awaitClose { registration.remove() }
    }
    override suspend fun updateCurrentArea(currentArea: String) = runCatching { db.collection("users").document(auth.currentUser!!.uid).update("currentArea", currentArea, "lastSeenAt", com.google.firebase.firestore.FieldValue.serverTimestamp()).await(); Unit }
    override suspend fun updateSafetyStatus(status: SafetyStatus) = runCatching { db.collection("users").document(auth.currentUser!!.uid).update("safetyStatus", status.name, "lastSeenAt", com.google.firebase.firestore.FieldValue.serverTimestamp()).await(); Unit }
    override suspend fun updateLastSeen() = runCatching { db.collection("users").document(auth.currentUser!!.uid).update("lastSeenAt", com.google.firebase.firestore.FieldValue.serverTimestamp()).await(); Unit }
}

class FirebaseEmergencyRepository(private val db: FirebaseFirestore = FirebaseFirestore.getInstance(), private val auth: FirebaseAuth = FirebaseAuth.getInstance()) : EmergencyRepository {
    override fun observeActiveEmergency(organizationId: String): Flow<Emergency?> = callbackFlow {
        val registration = db.collection("emergencies").whereEqualTo("organizationId", organizationId).whereEqualTo("active", true).limit(1).addSnapshotListener { snapshot, error ->
            if (error != null) close(error) else { val doc = snapshot?.documents?.firstOrNull(); trySend(doc?.data?.emergency(doc.id)) }
        }; awaitClose { registration.remove() }
    }
    override suspend fun createEmergency(organizationId: String, type: EmergencyType, locationText: String, message: String): Result<Emergency> = runCatching {
        Validation.emergency(locationText, message).getOrThrow(); val ref = db.collection("emergencies").document(); val uid = auth.currentUser!!.uid
        ref.set(mapOf("organizationId" to organizationId, "active" to true, "type" to type.name, "locationText" to locationText.trim(), "message" to message.trim(), "createdBy" to uid, "createdAt" to com.google.firebase.firestore.FieldValue.serverTimestamp(), "version" to 1)).await()
        db.collection("organizations").document(organizationId).update("activeEmergencyId", ref.id).await(); Emergency(ref.id, organizationId, true, type, locationText, message, uid, version = 1)
    }
    override suspend fun endEmergency(emergencyId: String) = runCatching {
        val batch = db.batch(); batch.update(db.collection("emergencies").document(emergencyId), mapOf("active" to false, "endedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp(), "version" to com.google.firebase.firestore.FieldValue.increment(1))); val org = db.collection("organizations").document(DEMO_ORGANIZATION_ID); batch.update(org, "activeEmergencyId", null); batch.commit().await(); Unit
    }
    override suspend fun acknowledgeEmergency(emergencyId: String, status: SafetyStatus, currentArea: String) = runCatching {
        val user = db.collection("users").document(auth.currentUser!!.uid).get().await().data!!.profile(); val ack = mapOf("studentUid" to user.uid, "displayName" to user.displayName, "status" to status.name, "currentArea" to currentArea, "acknowledgedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp())
        db.collection("emergencies").document(emergencyId).collection("acknowledgements").document(user.uid).set(ack).await(); db.collection("users").document(user.uid).update("safetyStatus", status.name, "lastSeenAt", com.google.firebase.firestore.FieldValue.serverTimestamp()).await(); Unit
    }
}
