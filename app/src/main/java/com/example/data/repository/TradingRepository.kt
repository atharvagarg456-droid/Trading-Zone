package com.example.data.repository

import android.util.Log
import com.example.data.model.AbusiveWordModerator
import com.example.data.model.AccessRequest
import com.example.data.model.AdminSettings
import com.example.data.model.BroadcastMessage
import com.example.data.model.CommunityReview
import com.example.data.model.DeviceRecord
import com.example.data.model.MAX_ADMINS_LIMIT
import com.example.data.model.MessageReadReceipt
import com.example.data.model.ModerationAlert
import com.example.data.model.PRIMARY_ADMIN_EMAIL
import com.example.data.model.PresenceRecord
import com.example.data.model.PricingConfig
import com.example.data.model.UserProfile
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class TradingRepository(private val db: FirebaseFirestore) {

    private val auth = Firebase.auth

    enum class OperationType {
        CREATE, UPDATE, DELETE, GET, LIST, WRITE
    }

    data class CommunityPostOutcome(
        val reviewId: String,
        val hadAbusiveWordsBlurred: Boolean,
        val detectedWords: List<String>
    )

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
    }

    private fun handleFirestoreError(
        error: Exception,
        operationType: OperationType,
        path: String
    ) {
        val uid = auth.currentUser?.uid ?: "unauthenticated"
        val email = auth.currentUser?.email ?: "none"
        Log.e(
            "FirestoreError",
            """{"operation":"${operationType.name}","path":"$path","uid":"$uid","email":"$email","error":"${error.message}"}""",
            error
        )
    }

    private suspend fun getActiveAdminEmailList(): List<String> {
        return try {
            val snap = db.collection("app_config").document("admin_settings").get().await()
            val settings = snap.toObject(AdminSettings::class.java)
            val emails = settings?.adminEmails?.filter { it.isNotBlank() }.orEmpty()
            if (emails.contains(PRIMARY_ADMIN_EMAIL)) {
                emails.distinct().take(MAX_ADMINS_LIMIT)
            } else {
                (listOf(PRIMARY_ADMIN_EMAIL) + emails).distinct().take(MAX_ADMINS_LIMIT)
            }
        } catch (_: Exception) {
            listOf(PRIMARY_ADMIN_EMAIL)
        }
    }

    fun observeUserProfile(userId: String): Flow<UserProfile?> = flow {
        val path = "users/$userId"
        emitAll(
            db.collection("users").document(userId)
                .snapshots()
                .map { snap -> snap.toObject(UserProfile::class.java) }
                .catch { error ->
                    val fsErr = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (fsErr == null && error is CancellationException) throw error
                    val ex = fsErr ?: (error as? Exception)
                    if (ex != null && auth.currentUser?.uid == userId) {
                        handleFirestoreError(ex, OperationType.GET, path)
                    }
                    throw error
                }
        )
    }

    fun observeDeviceRecord(deviceId: String): Flow<DeviceRecord?> = flow {
        val uid = requireUserId()
        val path = "device_registry/$deviceId"
        emitAll(
            db.collection("device_registry").document(deviceId)
                .snapshots()
                .map { snap -> snap.toObject(DeviceRecord::class.java) }
                .catch { error ->
                    val fsErr = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (fsErr == null && error is CancellationException) throw error
                    val ex = fsErr ?: (error as? Exception)
                    if (ex != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(ex, OperationType.GET, path)
                    }
                    throw error
                }
        )
    }

    suspend fun getDeviceRecord(deviceId: String): Result<DeviceRecord?> = runCatching {
        requireUserId()
        val ref = db.collection("device_registry").document(deviceId)
        try {
            ref.get().await().toObject(DeviceRecord::class.java)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, ref.path)
            throw e
        }
    }

    suspend fun submitUserRegistration(
        email: String,
        fullName: String,
        phoneNumber: String,
        city: String,
        photoBase64: String,
        deviceId: String,
        deviceModel: String,
        ipAddress: String,
        securityQuestion: String,
        securityAnswer: String,
        termsAccepted: Boolean,
        isCallerAdmin: Boolean,
        appLockPin: String = ""
    ): Result<Unit> = runCatching {
        val uid = requireUserId()
        val adminEmails = getActiveAdminEmailList()
        val deviceRef = db.collection("device_registry").document(deviceId)
        val userRef = db.collection("users").document(uid)

        val existingDevice = try {
            deviceRef.get().await().toObject(DeviceRecord::class.java)
        } catch (_: Exception) {
            null
        }

        if (existingDevice?.isBanned == true && !isCallerAdmin) {
            throw IllegalStateException("This hardware device ($deviceId) is permanently banned.")
        }

        try {
            if (existingDevice == null) {
                deviceRef.set(
                    mapOf(
                        "deviceId" to deviceId,
                        "ipAddress" to ipAddress,
                        "deviceModel" to deviceModel,
                        "boundUserId" to uid,
                        "trialClaimed" to false,
                        "isBanned" to false,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } else {
                deviceRef.update(
                    mapOf(
                        "ipAddress" to ipAddress,
                        "deviceModel" to deviceModel,
                        "boundUserId" to uid,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, deviceRef.path)
            throw e
        }

        val existingUserSnap = userRef.get().await()
        try {
            if (!existingUserSnap.exists()) {
                val payload = mutableMapOf<String, Any>(
                    "userId" to uid,
                    "email" to email.trim(),
                    "fullName" to fullName.trim().take(120),
                    "phoneNumber" to phoneNumber.trim().take(40),
                    "city" to city.trim().take(100),
                    "photoBase64" to photoBase64,
                    "deviceId" to deviceId,
                    "deviceModel" to deviceModel.take(120),
                    "ipAddress" to ipAddress.take(100),
                    "accountStatus" to if (isCallerAdmin) "APPROVED" else "PENDING_APPROVAL",
                    "entitlementStatus" to if (isCallerAdmin) "PAID_ACTIVE" else "NONE",
                    "trialUsed" to false,
                    "validUntilMillis" to if (isCallerAdmin) (System.currentTimeMillis() + 86_400_000L * 3650L) else 0L,
                    "activePlanName" to if (isCallerAdmin) "Primary Admin Lifetime" else "",
                    "securityQuestion" to securityQuestion.trim().take(300),
                    "securityAnswer" to securityAnswer.trim().take(200),
                    "termsAccepted" to termsAccepted,
                    "isCommodityMuted" to false,
                    "hasPendingProfileEdit" to false,
                    "pendingFullName" to "",
                    "pendingPhoneNumber" to "",
                    "pendingCity" to "",
                    "pendingPhotoBase64" to "",
                    "dailyReviewCount" to 0L,
                    "lastReviewDate" to "",
                    "adminEmails" to adminEmails,
                    "lastActiveAt" to FieldValue.serverTimestamp(),
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                if (appLockPin.isNotBlank()) {
                    payload["appLockPin"] = appLockPin.trim().take(20)
                }
                userRef.set(payload).await()
            } else {
                val updatePayload = mutableMapOf<String, Any>(
                    "fullName" to fullName.trim().take(120),
                    "phoneNumber" to phoneNumber.trim().take(40),
                    "city" to city.trim().take(100),
                    "photoBase64" to photoBase64,
                    "securityQuestion" to securityQuestion.trim().take(300),
                    "securityAnswer" to securityAnswer.trim().take(200),
                    "termsAccepted" to termsAccepted,
                    "ipAddress" to ipAddress.take(100),
                    "deviceModel" to deviceModel.take(120),
                    "lastActiveAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                if (appLockPin.isNotBlank()) {
                    updatePayload["appLockPin"] = appLockPin.trim().take(20)
                }
                userRef.update(updatePayload).await()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, userRef.path)
            throw e
        }

        touchPublicPresenceHeartbeat(uid)
    }

    suspend fun syncAppLockPinInProfile(pin: String): Result<Unit> = runCatching {
        val uid = requireUserId()
        val cleanPin = pin.trim().take(20)
        if (cleanPin.isBlank()) return@runCatching
        val userRef = db.collection("users").document(uid)
        try {
            userRef.update(
                mapOf(
                    "appLockPin" to cleanPin,
                    "lastActiveAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } catch (_: Exception) {
        }
    }

    suspend fun updateSecurityQuestionInProfile(
        securityQuestion: String,
        securityAnswer: String
    ): Result<Unit> = runCatching {
        val uid = requireUserId()
        val userRef = db.collection("users").document(uid)
        try {
            userRef.update(
                mapOf(
                    "securityQuestion" to securityQuestion.trim().take(300),
                    "securityAnswer" to securityAnswer.trim().take(200),
                    "lastActiveAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, userRef.path)
            throw e
        }
    }

    suspend fun requestProfileEdit(
        newFullName: String,
        newPhoneNumber: String,
        newCity: String,
        newPhotoBase64: String,
        isCallerAdmin: Boolean
    ): Result<Unit> = runCatching {
        val uid = requireUserId()
        val userRef = db.collection("users").document(uid)
        try {
            if (isCallerAdmin) {
                userRef.update(
                    mapOf(
                        "fullName" to newFullName.trim().take(120),
                        "phoneNumber" to newPhoneNumber.trim().take(40),
                        "city" to newCity.trim().take(100),
                        "photoBase64" to newPhotoBase64,
                        "hasPendingProfileEdit" to false,
                        "pendingFullName" to "",
                        "pendingPhoneNumber" to "",
                        "pendingCity" to "",
                        "pendingPhotoBase64" to "",
                        "lastActiveAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } else {
                userRef.update(
                    mapOf(
                        "hasPendingProfileEdit" to true,
                        "pendingFullName" to newFullName.trim().take(120),
                        "pendingPhoneNumber" to newPhoneNumber.trim().take(40),
                        "pendingCity" to newCity.trim().take(100),
                        "pendingPhotoBase64" to newPhotoBase64,
                        "lastActiveAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, userRef.path)
            throw e
        }
    }

    suspend fun reviewProfileEditRequest(
        user: UserProfile,
        approve: Boolean
    ): Result<Unit> = runCatching {
        requireUserId()
        val userRef = db.collection("users").document(user.userId)
        val updates = if (approve) {
            mapOf(
                "fullName" to user.pendingFullName.ifBlank { user.fullName }.take(120),
                "phoneNumber" to user.pendingPhoneNumber.ifBlank { user.phoneNumber }.take(40),
                "city" to user.pendingCity.ifBlank { user.city }.take(100),
                "photoBase64" to user.pendingPhotoBase64.ifBlank { user.photoBase64 },
                "hasPendingProfileEdit" to false,
                "pendingFullName" to "",
                "pendingPhoneNumber" to "",
                "pendingCity" to "",
                "pendingPhotoBase64" to "",
                "updatedAt" to FieldValue.serverTimestamp()
            )
        } else {
            mapOf(
                "hasPendingProfileEdit" to false,
                "pendingFullName" to "",
                "pendingPhoneNumber" to "",
                "pendingCity" to "",
                "pendingPhotoBase64" to "",
                "updatedAt" to FieldValue.serverTimestamp()
            )
        }
        try {
            userRef.update(updates).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, userRef.path)
            throw e
        }
    }

    suspend fun touchUserPresence(
        userId: String,
        ipAddress: String,
        deviceModel: String
    ): Result<Unit> = runCatching {
        val currentUid = requireUserId()
        if (currentUid != userId) return@runCatching
        touchPublicPresenceHeartbeat(userId)
        try {
            db.collection("users").document(userId).update(
                mapOf(
                    "lastActiveAt" to FieldValue.serverTimestamp(),
                    "ipAddress" to ipAddress.take(100),
                    "deviceModel" to deviceModel.take(120),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } catch (_: Exception) {
        }
    }

    private suspend fun touchPublicPresenceHeartbeat(userId: String) {
        try {
            db.collection("presence").document(userId).set(
                mapOf(
                    "userId" to userId,
                    "visibility" to "PUBLIC_PRESENCE",
                    "lastActiveAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } catch (_: Exception) {
        }
    }

    fun observeOnlinePeopleCount(): Flow<Int> = flow {
        val uid = requireUserId()
        val path = "presence"
        emitAll(
            db.collection("presence")
                .whereEqualTo("visibility", "PUBLIC_PRESENCE")
                .snapshots()
                .map { snapshot ->
                    val cutoff = System.currentTimeMillis() - 10 * 60 * 1000L
                    val activeCount = snapshot.toObjects(PresenceRecord::class.java).count { rec ->
                        val ts = rec.lastActiveAt?.toDate()?.time ?: 0L
                        ts >= cutoff
                    }
                    activeCount.coerceAtLeast(1)
                }
                .catch { error ->
                    val fsErr = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (fsErr == null && error is CancellationException) throw error
                    val ex = fsErr ?: (error as? Exception)
                    if (ex != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(ex, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    fun observeBroadcastMessages(): Flow<List<BroadcastMessage>> = flow {
        val uid = requireUserId()
        val path = "broadcast_messages"
        emitAll(
            db.collection("broadcast_messages")
                .whereEqualTo("visibility", "VERIFIED_MEMBERS")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(BroadcastMessage::class.java)
                        .sortedWith(
                            compareByDescending<BroadcastMessage> { it.isPinned }
                                .thenByDescending { it.createdAt ?: Timestamp(0, 0) }
                        )
                }
                .catch { error ->
                    val fsErr = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (fsErr == null && error is CancellationException) throw error
                    val ex = fsErr ?: (error as? Exception)
                    if (ex != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(ex, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    suspend fun recordMessageReadReceipt(
        messageId: String,
        profile: UserProfile
    ): Result<Unit> = runCatching {
        val uid = requireUserId()
        if (messageId.isBlank()) return@runCatching
        val adminEmails = getActiveAdminEmailList()
        val viewId = "${messageId}_$uid".take(150)
        val ref = db.collection("message_views").document(viewId)
        try {
            ref.set(
                mapOf(
                    "messageId" to messageId.take(128),
                    "userId" to uid,
                    "userName" to profile.fullName.ifBlank { profile.email }.take(120),
                    "userEmail" to profile.email.take(200),
                    "userPhotoBase64" to profile.photoBase64.take(450000),
                    "adminEmails" to adminEmails,
                    "viewedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, ref.path)
            throw e
        }
    }

    fun observeMyMessageReadReceipts(userId: String): Flow<List<MessageReadReceipt>> = flow {
        val path = "message_views"
        emitAll(
            db.collection("message_views")
                .whereEqualTo("userId", userId)
                .snapshots()
                .map { snap -> snap.toObjects(MessageReadReceipt::class.java) }
                .catch { error ->
                    val fsErr = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (fsErr == null && error is CancellationException) throw error
                    val ex = fsErr ?: (error as? Exception)
                    if (ex != null && auth.currentUser?.uid == userId) {
                        handleFirestoreError(ex, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    fun observeAllMessageReadReceiptsForAdmin(adminEmail: String): Flow<List<MessageReadReceipt>> = flow {
        val uid = requireUserId()
        val path = "message_views"
        emitAll(
            db.collection("message_views")
                .whereArrayContains("adminEmails", adminEmail.lowercase(Locale.ROOT))
                .snapshots()
                .map { snap ->
                    snap.toObjects(MessageReadReceipt::class.java)
                        .sortedByDescending { it.viewedAt ?: Timestamp(0, 0) }
                }
                .catch { error ->
                    val fsErr = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (fsErr == null && error is CancellationException) throw error
                    val ex = fsErr ?: (error as? Exception)
                    if (ex != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(ex, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    fun observeCommunityReviews(): Flow<List<CommunityReview>> = flow {
        val uid = requireUserId()
        val path = "community_reviews"
        emitAll(
            db.collection("community_reviews")
                .whereEqualTo("visibility", "VERIFIED_MEMBERS")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(CommunityReview::class.java)
                        .sortedWith(
                            compareByDescending<CommunityReview> { it.isPinned }
                                .thenByDescending { it.createdAt ?: Timestamp(0, 0) }
                        )
                }
                .catch { error ->
                    val fsErr = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (fsErr == null && error is CancellationException) throw error
                    val ex = fsErr ?: (error as? Exception)
                    if (ex != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(ex, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    suspend fun submitCommunityReview(
        profile: UserProfile,
        tradeMessageId: String,
        tradeMessageTitle: String,
        tradeRiskLevel: String,
        topicTag: String,
        rawOpinionText: String,
        rating: Int = 5,
        dailyLimitPerUser: Int,
        isCallerAdmin: Boolean
    ): Result<CommunityPostOutcome> = runCatching {
        val uid = requireUserId()
        if (!isCallerAdmin && profile.isCommodityMuted) {
            throw IllegalStateException("Your account is muted from posting in Commodity by the Admin.")
        }

        val usedToday = profile.usedReviewsToday()
        if (!isCallerAdmin && usedToday >= dailyLimitPerUser) {
            throw IllegalStateException("Daily Commodity message limit reached ($dailyLimitPerUser/day).")
        }

        val adminEmails = getActiveAdminEmailList()

        // 1. Check for prohibited Phone Number, Email, UPI ID, or Personal Handle
        val contactCheck = AbusiveWordModerator.checkProhibitedContactOrId(rawOpinionText)
        if (contactCheck.isBlocked) {
            val alertRef = db.collection("moderation_alerts").document()
            try {
                alertRef.set(
                    mapOf(
                        "userId" to uid,
                        "userEmail" to profile.email.take(200),
                        "userName" to profile.fullName.ifBlank { profile.email }.take(120),
                        "userPhotoBase64" to profile.photoBase64.take(450000),
                        "deviceId" to profile.deviceId.ifBlank { "UNKNOWN_DEV" }.take(128),
                        "ipAddress" to profile.ipAddress.ifBlank { "0.0.0.0" }.take(100),
                        "tradeMessageTitle" to "Commodity [BLOCKED PHONE / ID]".take(200),
                        "originalUneditedText" to rawOpinionText.trim().take(2000),
                        "blurredText" to "[BLOCKED: Attempted to share Phone Number / Personal ID]",
                        "detectedWords" to contactCheck.reasonSummary.take(500),
                        "status" to "OPEN",
                        "adminEmails" to adminEmails,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } catch (_: Exception) {
            }
            throw IllegalStateException(
                "Message Blocked: Sharing phone numbers, email addresses, or personal IDs is strictly prohibited in Commodity. Admin has been alerted."
            )
        }

        // 2. Check and blur any abusive words
        val moderation = AbusiveWordModerator.moderateText(rawOpinionText.trim())
        val reviewRef = db.collection("community_reviews").document()
        val normalizedRisk = when (tradeRiskLevel.uppercase(Locale.ROOT)) {
            "LOW", "MEDIUM", "HIGH" -> tradeRiskLevel.uppercase(Locale.ROOT)
            else -> "MEDIUM"
        }

        try {
            reviewRef.set(
                mapOf(
                    "userId" to uid,
                    "userName" to profile.fullName.ifBlank { "Verified Trader" }.take(120),
                    "userPhotoBase64" to profile.photoBase64.take(450000),
                    "tradeMessageId" to tradeMessageId.ifBlank { "commodity_general" }.take(128),
                    "tradeMessageTitle" to tradeMessageTitle.ifBlank { "Commodity Discussion" }.take(200),
                    "tradeRiskLevel" to normalizedRisk,
                    "topicTag" to topicTag.ifBlank { "Commodity" }.take(80),
                    "publicContent" to moderation.publicText.take(2000),
                    "rating" to rating.coerceIn(1, 5),
                    "hasBlurredWords" to moderation.hasBlurredWords,
                    "isPinned" to false,
                    "visibility" to "VERIFIED_MEMBERS",
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, reviewRef.path)
            throw e
        }

        if (moderation.hasBlurredWords) {
            val alertRef = db.collection("moderation_alerts").document()
            try {
                alertRef.set(
                    mapOf(
                        "userId" to uid,
                        "userEmail" to profile.email.take(200),
                        "userName" to profile.fullName.ifBlank { profile.email }.take(120),
                        "userPhotoBase64" to profile.photoBase64.take(450000),
                        "deviceId" to profile.deviceId.ifBlank { "UNKNOWN_DEV" }.take(128),
                        "ipAddress" to profile.ipAddress.ifBlank { "0.0.0.0" }.take(100),
                        "tradeMessageTitle" to tradeMessageTitle.ifBlank { "Commodity" }.take(200),
                        "originalUneditedText" to rawOpinionText.trim().take(2000),
                        "blurredText" to moderation.publicText.take(2000),
                        "detectedWords" to moderation.detectedWords.joinToString(", ").take(500),
                        "status" to "OPEN",
                        "adminEmails" to adminEmails,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } catch (_: Exception) {
            }
        }

        val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        val nextCount = if (profile.lastReviewDate == today) profile.dailyReviewCount + 1L else 1L
        try {
            db.collection("users").document(uid).update(
                mapOf(
                    "dailyReviewCount" to nextCount,
                    "lastReviewDate" to today,
                    "lastActiveAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } catch (_: Exception) {
        }

        CommunityPostOutcome(
            reviewId = reviewRef.id,
            hadAbusiveWordsBlurred = moderation.hasBlurredWords,
            detectedWords = moderation.detectedWords
        )
    }

    suspend fun setCommodityMessagePinned(
        reviewId: String,
        pinned: Boolean
    ): Result<Unit> = runCatching {
        requireUserId()
        val ref = db.collection("community_reviews").document(reviewId)
        try {
            ref.update(
                mapOf(
                    "isPinned" to pinned,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, ref.path)
            throw e
        }
    }

    suspend fun deleteCommunityReview(reviewId: String): Result<Unit> = runCatching {
        requireUserId()
        val ref = db.collection("community_reviews").document(reviewId)
        try {
            ref.delete().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, ref.path)
            throw e
        }
    }

    fun observePricingConfig(): Flow<PricingConfig> = flow {
        val uid = requireUserId()
        val path = "app_config/pricing"
        emitAll(
            db.collection("app_config").document("pricing")
                .snapshots()
                .map { snap -> snap.toObject(PricingConfig::class.java) ?: PricingConfig() }
                .catch { error ->
                    val fsErr = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (fsErr == null && error is CancellationException) throw error
                    val ex = fsErr ?: (error as? Exception)
                    if (ex != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(ex, OperationType.GET, path)
                    }
                    throw error
                }
        )
    }

    fun observeAdminSettings(): Flow<AdminSettings> = flow {
        val uid = requireUserId()
        val path = "app_config/admin_settings"
        emitAll(
            db.collection("app_config").document("admin_settings")
                .snapshots()
                .map { snap -> snap.toObject(AdminSettings::class.java) ?: AdminSettings() }
                .catch { error ->
                    val fsErr = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (fsErr == null && error is CancellationException) throw error
                    val ex = fsErr ?: (error as? Exception)
                    if (ex != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(ex, OperationType.GET, path)
                    }
                    throw error
                }
        )
    }

    fun observeMyAccessRequests(userId: String): Flow<List<AccessRequest>> = flow {
        val path = "access_requests"
        emitAll(
            db.collection("access_requests")
                .whereEqualTo("userId", userId)
                .snapshots()
                .map { snap ->
                    snap.toObjects(AccessRequest::class.java)
                        .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
                }
                .catch { error ->
                    val fsErr = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (fsErr == null && error is CancellationException) throw error
                    val ex = fsErr ?: (error as? Exception)
                    if (ex != null && auth.currentUser?.uid == userId) {
                        handleFirestoreError(ex, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    suspend fun requestSevenDayFreeTrial(
        profile: UserProfile,
        deviceRecord: DeviceRecord?
    ): Result<String> = runCatching {
        val uid = requireUserId()
        if (profile.trialUsed || deviceRecord?.trialClaimed == true) {
            throw IllegalStateException("The 1-time 7-Day Free Trial has already been claimed on this account or hardware device.")
        }
        val adminEmails = getActiveAdminEmailList()
        val reqRef = db.collection("access_requests").document()

        try {
            db.collection("device_registry").document(profile.deviceId).update(
                mapOf(
                    "trialClaimed" to true,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } catch (_: Exception) {
        }

        try {
            db.collection("users").document(uid).update(
                mapOf(
                    "trialUsed" to true,
                    "entitlementStatus" to "TRIAL_PENDING",
                    "lastActiveAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()

            reqRef.set(
                mapOf(
                    "userId" to uid,
                    "userEmail" to profile.email.take(200),
                    "userName" to profile.fullName.ifBlank { profile.email }.take(120),
                    "userPhotoBase64" to profile.photoBase64.take(500000),
                    "deviceId" to profile.deviceId.take(128),
                    "ipAddress" to profile.ipAddress.take(100),
                    "requestType" to "FREE_TRIAL_7D",
                    "planName" to "7-Day Free Trial",
                    "amountInr" to 0.0,
                    "validityDays" to 7L,
                    "utrReference" to "FREE_TRIAL_REQUEST",
                    "paymentScreenshotBase64" to "",
                    "status" to "PENDING",
                    "adminEmails" to adminEmails,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            reqRef.id
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, reqRef.path)
            throw e
        }
    }

    suspend fun submitUpiPaymentRequest(
        profile: UserProfile,
        amountInr: Double,
        validityDays: Long,
        utrReference: String,
        planName: String,
        paymentScreenshotBase64: String
    ): Result<String> = runCatching {
        val uid = requireUserId()
        val adminEmails = getActiveAdminEmailList()
        val reqRef = db.collection("access_requests").document()
        try {
            reqRef.set(
                mapOf(
                    "userId" to uid,
                    "userEmail" to profile.email.take(200),
                    "userName" to profile.fullName.ifBlank { profile.email }.take(120),
                    "userPhotoBase64" to profile.photoBase64.take(500000),
                    "deviceId" to profile.deviceId.ifBlank { "DEV_DEFAULT" }.take(128),
                    "ipAddress" to profile.ipAddress.ifBlank { "0.0.0.0" }.take(100),
                    "requestType" to "PAID_UPI",
                    "planName" to planName.ifBlank { "Monthly Pro Plan" }.take(120),
                    "amountInr" to amountInr.coerceAtLeast(0.0),
                    "validityDays" to validityDays.coerceAtLeast(1L),
                    "utrReference" to utrReference.trim().take(120),
                    "paymentScreenshotBase64" to paymentScreenshotBase64,
                    "status" to "PENDING",
                    "adminEmails" to adminEmails,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            reqRef.id
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, reqRef.path)
            throw e
        }
    }

    // Admin Operations
    fun observeAllUsersForAdmin(adminEmail: String): Flow<List<UserProfile>> = flow {
        val uid = requireUserId()
        val path = "users"
        emitAll(
            db.collection("users")
                .whereArrayContains("adminEmails", adminEmail.lowercase(Locale.ROOT))
                .snapshots()
                .map { snap ->
                    snap.toObjects(UserProfile::class.java)
                        .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
                }
                .catch { error ->
                    val fsErr = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (fsErr == null && error is CancellationException) throw error
                    val ex = fsErr ?: (error as? Exception)
                    if (ex != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(ex, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    fun observeAllAccessRequestsForAdmin(adminEmail: String): Flow<List<AccessRequest>> = flow {
        val uid = requireUserId()
        val path = "access_requests"
        emitAll(
            db.collection("access_requests")
                .whereArrayContains("adminEmails", adminEmail.lowercase(Locale.ROOT))
                .snapshots()
                .map { snap ->
                    snap.toObjects(AccessRequest::class.java)
                        .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
                }
                .catch { error ->
                    val fsErr = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (fsErr == null && error is CancellationException) throw error
                    val ex = fsErr ?: (error as? Exception)
                    if (ex != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(ex, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    fun observeModerationAlertsForAdmin(adminEmail: String): Flow<List<ModerationAlert>> = flow {
        val uid = requireUserId()
        val path = "moderation_alerts"
        emitAll(
            db.collection("moderation_alerts")
                .whereArrayContains("adminEmails", adminEmail.lowercase(Locale.ROOT))
                .snapshots()
                .map { snap ->
                    snap.toObjects(ModerationAlert::class.java)
                        .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
                }
                .catch { error ->
                    val fsErr = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (fsErr == null && error is CancellationException) throw error
                    val ex = fsErr ?: (error as? Exception)
                    if (ex != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(ex, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    suspend fun publishBroadcastMessage(
        title: String,
        content: String,
        category: String,
        riskLevel: String,
        markedPhotoBase64: String,
        linkUrl: String,
        linkTitle: String,
        isPinned: Boolean,
        authorName: String,
        replyToMessageId: String = "",
        replyToTitle: String = "",
        replyToSnippet: String = "",
        replyToRiskLevel: String = ""
    ): Result<String> = runCatching {
        val uid = requireUserId()
        val ref = db.collection("broadcast_messages").document()
        val normalizedRisk = when (riskLevel.uppercase(Locale.ROOT)) {
            "LOW", "MEDIUM", "HIGH" -> riskLevel.uppercase(Locale.ROOT)
            else -> "MEDIUM"
        }
        try {
            ref.set(
                mapOf(
                    "title" to title.trim().take(200),
                    "content" to content.trim().take(8000),
                    "category" to category.trim().ifBlank { "Commodity" }.take(60),
                    "riskLevel" to normalizedRisk,
                    "markedPhotoBase64" to markedPhotoBase64,
                    "linkUrl" to linkUrl.trim().take(1000),
                    "linkTitle" to linkTitle.trim().take(200),
                    "replyToMessageId" to replyToMessageId.trim().take(128),
                    "replyToTitle" to replyToTitle.trim().take(200),
                    "replyToSnippet" to replyToSnippet.trim().take(500),
                    "replyToRiskLevel" to replyToRiskLevel.trim().take(20),
                    "isPinned" to isPinned,
                    "isEdited" to false,
                    "authorId" to uid,
                    "authorName" to authorName.ifBlank { "Atharva Garg" }.take(120),
                    "visibility" to "VERIFIED_MEMBERS",
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            ref.id
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, ref.path)
            throw e
        }
    }

    suspend fun editBroadcastMessage(
        messageId: String,
        title: String,
        content: String,
        category: String,
        riskLevel: String,
        markedPhotoBase64: String,
        linkUrl: String,
        linkTitle: String,
        isPinned: Boolean,
        authorName: String,
        replyToMessageId: String = "",
        replyToTitle: String = "",
        replyToSnippet: String = "",
        replyToRiskLevel: String = ""
    ): Result<Unit> = runCatching {
        val uid = requireUserId()
        val ref = db.collection("broadcast_messages").document(messageId)
        val normalizedRisk = when (riskLevel.uppercase(Locale.ROOT)) {
            "LOW", "MEDIUM", "HIGH" -> riskLevel.uppercase(Locale.ROOT)
            else -> "MEDIUM"
        }
        try {
            ref.update(
                mapOf(
                    "title" to title.trim().take(200),
                    "content" to content.trim().take(8000),
                    "category" to category.trim().ifBlank { "Commodity" }.take(60),
                    "riskLevel" to normalizedRisk,
                    "markedPhotoBase64" to markedPhotoBase64,
                    "linkUrl" to linkUrl.trim().take(1000),
                    "linkTitle" to linkTitle.trim().take(200),
                    "replyToMessageId" to replyToMessageId.trim().take(128),
                    "replyToTitle" to replyToTitle.trim().take(200),
                    "replyToSnippet" to replyToSnippet.trim().take(500),
                    "replyToRiskLevel" to replyToRiskLevel.trim().take(20),
                    "isPinned" to isPinned,
                    "isEdited" to true,
                    "authorId" to uid,
                    "authorName" to authorName.ifBlank { "Atharva Garg" }.take(120),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, ref.path)
            throw e
        }
    }

    suspend fun deleteBroadcastMessage(messageId: String): Result<Unit> = runCatching {
        requireUserId()
        val ref = db.collection("broadcast_messages").document(messageId)
        try {
            ref.delete().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, ref.path)
            throw e
        }
    }

    suspend fun approveUserAccount(
        userId: String,
        grantSevenDayTrial: Boolean
    ): Result<Unit> = runCatching {
        requireUserId()
        val ref = db.collection("users").document(userId)
        val updates = mutableMapOf<String, Any>(
            "accountStatus" to "APPROVED",
            "updatedAt" to FieldValue.serverTimestamp()
        )
        if (grantSevenDayTrial) {
            updates["entitlementStatus"] = "TRIAL_ACTIVE"
            updates["trialUsed"] = true
            updates["activePlanName"] = "7-Day Free Trial"
            updates["validUntilMillis"] = System.currentTimeMillis() + 7L * 86_400_000L
        }
        try {
            ref.update(updates).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, ref.path)
            throw e
        }
    }

    suspend fun updateUserSubscriptionDays(
        userId: String,
        planName: String,
        daysLeft: Int
    ): Result<Unit> = runCatching {
        requireUserId()
        val ref = db.collection("users").document(userId)
        val clampedDays = daysLeft.coerceIn(0, 3650)
        val updates = if (clampedDays > 0) {
            mapOf(
                "accountStatus" to "APPROVED",
                "entitlementStatus" to "PAID_ACTIVE",
                "activePlanName" to planName.ifBlank { "Admin Custom Plan" }.take(120),
                "validUntilMillis" to (System.currentTimeMillis() + clampedDays.toLong() * 86_400_000L),
                "updatedAt" to FieldValue.serverTimestamp()
            )
        } else {
            mapOf(
                "entitlementStatus" to "EXPIRED",
                "validUntilMillis" to 0L,
                "updatedAt" to FieldValue.serverTimestamp()
            )
        }
        try {
            ref.update(updates).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, ref.path)
            throw e
        }
    }

    suspend fun adminResetUserPin(
        userId: String,
        newPin: String
    ): Result<Unit> = runCatching {
        requireUserId()
        val ref = db.collection("users").document(userId)
        try {
            ref.update(
                mapOf(
                    "appLockPin" to newPin.trim().take(20),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, ref.path)
            throw e
        }
    }

    suspend fun setUserCommodityMuted(
        userId: String,
        muted: Boolean
    ): Result<Unit> = runCatching {
        requireUserId()
        val ref = db.collection("users").document(userId)
        try {
            ref.update(
                mapOf(
                    "isCommodityMuted" to muted,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, ref.path)
            throw e
        }
    }

    suspend fun adminResetUserDailyCommodityQuota(userId: String): Result<Unit> = runCatching {
        requireUserId()
        val ref = db.collection("users").document(userId)
        try {
            ref.update(
                mapOf(
                    "dailyReviewCount" to 0L,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, ref.path)
            throw e
        }
    }

    suspend fun reviewAccessRequest(
        request: AccessRequest,
        approve: Boolean
    ): Result<Unit> = runCatching {
        requireUserId()
        val reqRef = db.collection("access_requests").document(request.id)
        val userRef = db.collection("users").document(request.userId)
        try {
            reqRef.update(
                mapOf(
                    "status" to if (approve) "APPROVED" else "REJECTED",
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()

            if (approve) {
                val days = request.validityDays.coerceAtLeast(1L)
                val nextValidUntil = System.currentTimeMillis() + days * 86_400_000L
                val isTrial = request.requestType == "FREE_TRIAL_7D"
                userRef.update(
                    mapOf(
                        "accountStatus" to "APPROVED",
                        "entitlementStatus" to if (isTrial) "TRIAL_ACTIVE" else "PAID_ACTIVE",
                        "trialUsed" to true,
                        "activePlanName" to request.planName.ifBlank {
                            if (isTrial) "7-Day Free Trial" else "Paid Membership"
                        }.take(120),
                        "validUntilMillis" to nextValidUntil,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, reqRef.path)
            throw e
        }
    }

    suspend fun resolveModerationAlert(
        alertId: String,
        status: String
    ): Result<Unit> = runCatching {
        requireUserId()
        val normalized = if (status == "BANNED") "BANNED" else "RESOLVED"
        val ref = db.collection("moderation_alerts").document(alertId)
        try {
            ref.update(
                mapOf(
                    "status" to normalized,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, ref.path)
            throw e
        }
    }

    suspend fun banUserFromModerationAlert(alert: ModerationAlert): Result<Unit> = runCatching {
        requireUserId()
        try {
            db.collection("users").document(alert.userId).update(
                mapOf(
                    "accountStatus" to "BANNED",
                    "entitlementStatus" to "EXPIRED",
                    "validUntilMillis" to 0L,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } catch (_: Exception) {
        }
        if (alert.deviceId.isNotBlank()) {
            try {
                db.collection("device_registry").document(alert.deviceId).update(
                    mapOf(
                        "isBanned" to true,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } catch (_: Exception) {
            }
        }
        resolveModerationAlert(alert.id, "BANNED").getOrThrow()
    }

    suspend fun setDeviceBanStatus(
        user: UserProfile,
        ban: Boolean
    ): Result<Unit> = runCatching {
        requireUserId()
        val userRef = db.collection("users").document(user.userId)
        try {
            userRef.update(
                mapOf(
                    "accountStatus" to if (ban) "BANNED" else "APPROVED",
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            if (user.deviceId.isNotBlank()) {
                db.collection("device_registry").document(user.deviceId).update(
                    mapOf(
                        "isBanned" to ban,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, userRef.path)
            throw e
        }
    }

    suspend fun removeUserAndBanDevice(user: UserProfile): Result<Unit> = runCatching {
        requireUserId()
        if (user.deviceId.isNotBlank()) {
            try {
                db.collection("device_registry").document(user.deviceId).update(
                    mapOf(
                        "isBanned" to true,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } catch (_: Exception) {
            }
        }
        val userRef = db.collection("users").document(user.userId)
        try {
            userRef.delete().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, userRef.path)
            throw e
        }
    }

    suspend fun setCommodityChatLocked(
        currentConfig: PricingConfig,
        locked: Boolean
    ): Result<Unit> = savePricingConfig(
        planName = currentConfig.planName,
        priceInr = currentConfig.priceInr,
        validityDays = currentConfig.validityDays,
        upiId = currentConfig.upiId,
        upiMobileNumber = currentConfig.upiMobileNumber,
        qrCodeBase64 = currentConfig.qrCodeBase64,
        qrOnlyMode = currentConfig.qrOnlyMode,
        plan2Enabled = currentConfig.plan2Enabled,
        plan2Name = currentConfig.plan2Name,
        plan2PriceInr = currentConfig.plan2PriceInr,
        plan2ValidityDays = currentConfig.plan2ValidityDays,
        plan2QrCodeBase64 = currentConfig.plan2QrCodeBase64,
        plan2QrOnlyMode = currentConfig.plan2QrOnlyMode,
        plan3Enabled = currentConfig.plan3Enabled,
        plan3Name = currentConfig.plan3Name,
        plan3PriceInr = currentConfig.plan3PriceInr,
        plan3ValidityDays = currentConfig.plan3ValidityDays,
        plan3QrCodeBase64 = currentConfig.plan3QrCodeBase64,
        plan3QrOnlyMode = currentConfig.plan3QrOnlyMode,
        dailyReviewLimitPerUser = currentConfig.dailyReviewLimitPerUser,
        commodityChatLocked = locked,
        trialDays = currentConfig.trialDays,
        benefits = currentConfig.benefits
    )

    suspend fun savePricingConfig(
        planName: String,
        priceInr: Double,
        validityDays: Long,
        upiId: String,
        upiMobileNumber: String,
        qrCodeBase64: String,
        qrOnlyMode: Boolean,
        plan2Enabled: Boolean,
        plan2Name: String,
        plan2PriceInr: Double,
        plan2ValidityDays: Long,
        plan2QrCodeBase64: String,
        plan2QrOnlyMode: Boolean,
        plan3Enabled: Boolean,
        plan3Name: String,
        plan3PriceInr: Double,
        plan3ValidityDays: Long,
        plan3QrCodeBase64: String,
        plan3QrOnlyMode: Boolean,
        dailyReviewLimitPerUser: Long,
        commodityChatLocked: Boolean,
        trialDays: Long,
        benefits: List<String>
    ): Result<Unit> = runCatching {
        requireUserId()
        val ref = db.collection("app_config").document("pricing")
        val existing = ref.get().await()
        val baseFields = mapOf(
            "planName" to planName.trim().ifBlank { "Monthly Pro Plan" }.take(120),
            "priceInr" to priceInr.coerceAtLeast(0.0),
            "validityDays" to validityDays.coerceIn(1L, 3650L),
            "upiId" to upiId.trim().take(150),
            "upiMobileNumber" to upiMobileNumber.trim().take(50),
            "qrCodeBase64" to qrCodeBase64,
            "qrOnlyMode" to qrOnlyMode,
            "plan2Enabled" to plan2Enabled,
            "plan2Name" to plan2Name.trim().take(120),
            "plan2PriceInr" to plan2PriceInr.coerceAtLeast(0.0),
            "plan2ValidityDays" to plan2ValidityDays.coerceIn(1L, 3650L),
            "plan2QrCodeBase64" to plan2QrCodeBase64,
            "plan2QrOnlyMode" to plan2QrOnlyMode,
            "plan3Enabled" to plan3Enabled,
            "plan3Name" to plan3Name.trim().take(120),
            "plan3PriceInr" to plan3PriceInr.coerceAtLeast(0.0),
            "plan3ValidityDays" to plan3ValidityDays.coerceIn(1L, 3650L),
            "plan3QrCodeBase64" to plan3QrCodeBase64,
            "plan3QrOnlyMode" to plan3QrOnlyMode,
            "dailyReviewLimitPerUser" to dailyReviewLimitPerUser.coerceIn(1L, 500L),
            "commodityChatLocked" to commodityChatLocked,
            "trialDays" to trialDays.coerceIn(1L, 30L),
            "benefits" to benefits.map { it.trim().take(200) }.filter { it.isNotBlank() }.take(10),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        try {
            if (!existing.exists()) {
                ref.set(
                    baseFields + mapOf(
                        "configType" to "PRICING",
                        "createdAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } else {
                ref.update(baseFields).await()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, ref.path)
            throw e
        }
    }

    suspend fun updateCoAdminEmails(adminEmails: List<String>): Result<Unit> = runCatching {
        requireUserId()
        val normalized = (listOf(PRIMARY_ADMIN_EMAIL) + adminEmails.map { it.trim().lowercase(Locale.ROOT) })
            .filter { it.isNotBlank() }
            .distinct()
            .take(MAX_ADMINS_LIMIT)

        val ref = db.collection("app_config").document("admin_settings")
        val existing = ref.get().await()
        try {
            if (!existing.exists()) {
                ref.set(
                    mapOf(
                        "configType" to "ADMIN_SETTINGS",
                        "primaryAdminEmail" to PRIMARY_ADMIN_EMAIL,
                        "adminEmails" to normalized,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } else {
                ref.update(
                    mapOf(
                        "adminEmails" to normalized,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, ref.path)
            throw e
        }
    }
}
