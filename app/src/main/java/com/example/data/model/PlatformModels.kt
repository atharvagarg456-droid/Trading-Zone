package com.example.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.ceil

const val PRIMARY_ADMIN_EMAIL = "atharvagarg456@gmail.com"
const val MAX_ADMINS_LIMIT = 3

const val COMMODITY_IDENTITY_WARNING =
    "WARNING: Do not disclose your identity, phone number, email, or personal ID to anybody. " +
        "That may lead to immediate suspension of your account with NO REFUND."

const val DEFAULT_TERMS_AND_CONDITIONS = """
1. PLATFORM OPERATOR & ACADEMIC SUPERVISION
Trading Zone is operated by Atharva Garg under the academic guidance of Mr. Vikash Chandra Garg.

2. EDUCATIONAL & INFORMATIONAL PURPOSE ONLY (NON-LIABILITY)
All broadcast messages, marked charts, risk levels, and Commodity discussions shared inside Trading Zone are strictly for educational and informational study. Neither Atharva Garg nor Mr. Vikash Chandra Garg is a SEBI-registered investment advisor, and neither shall be held liable for any financial profit or loss incurred from market decisions.

3. DEVICE BINDING & SINGLE-TRIAL POLICY
Each account is bound to your hardware Device ID and network telemetry. The 7-Day Free Trial can be claimed only once per physical device. Attempting to bypass device limits or share credentials will result in a permanent hardware ban.

4. STRICT NO-IDENTITY DISCLOSURE & ANTI-ABUSE POLICY (NO REFUND)
In the Commodity section, you must NEVER disclose your personal identity, phone number, email address, UPI ID, or social/personal handles to anybody. Attempting to send phone numbers or personal IDs is automatically blocked and reported to the Admin. Any violation or abusive behavior will result in immediate account suspension and permanent device ban with NO REFUND.
"""

enum class AppThemePreference {
    SYSTEM,
    LIGHT,
    DARK
}

@IgnoreExtraProperties
data class UserProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val email: String = "",
    val fullName: String = "",
    val phoneNumber: String = "",
    val city: String = "",
    val photoBase64: String = "",
    val deviceId: String = "",
    val deviceModel: String = "",
    val ipAddress: String = "",
    val accountStatus: String = "PENDING_APPROVAL", // PENDING_APPROVAL, APPROVED, BANNED
    val entitlementStatus: String = "NONE", // NONE, TRIAL_PENDING, TRIAL_ACTIVE, PAID_ACTIVE, EXPIRED
    val trialUsed: Boolean = false,
    val validUntilMillis: Long = 0L,
    val activePlanName: String = "",
    val appLockPin: String = "",
    val securityQuestion: String = "",
    val securityAnswer: String = "",
    val termsAccepted: Boolean = false,
    val isCommodityMuted: Boolean = false,
    val hasPendingProfileEdit: Boolean = false,
    val pendingFullName: String = "",
    val pendingPhoneNumber: String = "",
    val pendingCity: String = "",
    val pendingPhotoBase64: String = "",
    val dailyReviewCount: Long = 0L,
    val lastReviewDate: String = "",
    val adminEmails: List<String> = listOf(PRIMARY_ADMIN_EMAIL),
    @ServerTimestamp val lastActiveAt: Timestamp? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun isEntitledNow(nowMillis: Long = System.currentTimeMillis()): Boolean {
        if (accountStatus != "APPROVED") return false
        if (entitlementStatus != "TRIAL_ACTIVE" && entitlementStatus != "PAID_ACTIVE") return false
        return validUntilMillis > nowMillis
    }

    fun daysRemaining(nowMillis: Long = System.currentTimeMillis()): Int {
        if (validUntilMillis <= nowMillis) return 0
        val diff = validUntilMillis - nowMillis
        return ceil(diff.toDouble() / 86_400_000.0).toInt().coerceAtLeast(0)
    }

    fun usedReviewsToday(): Int {
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        return if (lastReviewDate == today) dailyReviewCount.toInt().coerceAtLeast(0) else 0
    }

    fun remainingReviewsToday(dailyLimit: Int): Int {
        return (dailyLimit - usedReviewsToday()).coerceAtLeast(0)
    }
}

@IgnoreExtraProperties
data class DeviceRecord(
    @DocumentId val id: String = "",
    val deviceId: String = "",
    val ipAddress: String = "",
    val deviceModel: String = "",
    val boundUserId: String = "",
    val trialClaimed: Boolean = false,
    val isBanned: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class BroadcastMessage(
    @DocumentId val id: String = "",
    val title: String = "",
    val content: String = "",
    val category: String = "Commodity",
    val riskLevel: String = "MEDIUM", // LOW, MEDIUM, HIGH
    val markedPhotoBase64: String = "",
    val linkUrl: String = "",
    val linkTitle: String = "",
    val replyToMessageId: String = "",
    val replyToTitle: String = "",
    val replyToSnippet: String = "",
    val replyToRiskLevel: String = "",
    val isPinned: Boolean = false,
    val isEdited: Boolean = false,
    val authorId: String = "",
    val authorName: String = "Atharva Garg",
    val visibility: String = "VERIFIED_MEMBERS",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class MessageReadReceipt(
    @DocumentId val id: String = "",
    val messageId: String = "",
    val userId: String = "",
    val userName: String = "",
    val userEmail: String = "",
    val userPhotoBase64: String = "",
    val adminEmails: List<String> = listOf(PRIMARY_ADMIN_EMAIL),
    @ServerTimestamp val viewedAt: Timestamp? = null
)

data class SubscriptionPlanTier(
    val tierIndex: Int,
    val planName: String,
    val priceInr: Double,
    val validityDays: Long,
    val qrCodeBase64: String,
    val qrOnlyMode: Boolean
)

@IgnoreExtraProperties
data class PricingConfig(
    @DocumentId val id: String = "",
    val configType: String = "PRICING",
    val planName: String = "Monthly Pro Plan",
    val priceInr: Double = 2499.0,
    val validityDays: Long = 30L,
    val upiId: String = "atharvagarg@upi",
    val upiMobileNumber: String = "+91 9876543210",
    val qrCodeBase64: String = "",
    val qrOnlyMode: Boolean = false,
    val plan2Enabled: Boolean = true,
    val plan2Name: String = "Quarterly Elite Plan",
    val plan2PriceInr: Double = 5999.0,
    val plan2ValidityDays: Long = 90L,
    val plan2QrCodeBase64: String = "",
    val plan2QrOnlyMode: Boolean = false,
    val plan3Enabled: Boolean = true,
    val plan3Name: String = "Half-Yearly Institutional Plan",
    val plan3PriceInr: Double = 9999.0,
    val plan3ValidityDays: Long = 180L,
    val plan3QrCodeBase64: String = "",
    val plan3QrOnlyMode: Boolean = false,
    val dailyReviewLimitPerUser: Long = 10L,
    val commodityChatLocked: Boolean = false,
    val trialDays: Long = 7L,
    val benefits: List<String> = listOf(
        "Real-time verified trade & commodity broadcasts",
        "Marked chart setups with support & resistance zones",
        "Access to the moderated Commodity discussion feed",
        "Direct WhatsApp-style threaded trade updates"
    ),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun availablePlans(): List<SubscriptionPlanTier> {
        val list = mutableListOf(
            SubscriptionPlanTier(
                tierIndex = 1,
                planName = planName.ifBlank { "Monthly Pro Plan" },
                priceInr = priceInr,
                validityDays = validityDays.coerceAtLeast(1L),
                qrCodeBase64 = qrCodeBase64,
                qrOnlyMode = qrOnlyMode
            )
        )
        if (plan2Enabled && plan2Name.isNotBlank()) {
            list.add(
                SubscriptionPlanTier(
                    tierIndex = 2,
                    planName = plan2Name,
                    priceInr = plan2PriceInr,
                    validityDays = plan2ValidityDays.coerceAtLeast(1L),
                    qrCodeBase64 = plan2QrCodeBase64.ifBlank { qrCodeBase64 },
                    qrOnlyMode = plan2QrOnlyMode
                )
            )
        }
        if (plan3Enabled && plan3Name.isNotBlank()) {
            list.add(
                SubscriptionPlanTier(
                    tierIndex = 3,
                    planName = plan3Name,
                    priceInr = plan3PriceInr,
                    validityDays = plan3ValidityDays.coerceAtLeast(1L),
                    qrCodeBase64 = plan3QrCodeBase64.ifBlank { qrCodeBase64 },
                    qrOnlyMode = plan3QrOnlyMode
                )
            )
        }
        return list
    }
}

@IgnoreExtraProperties
data class AdminSettings(
    @DocumentId val id: String = "",
    val configType: String = "ADMIN_SETTINGS",
    val primaryAdminEmail: String = PRIMARY_ADMIN_EMAIL,
    val adminEmails: List<String> = listOf(PRIMARY_ADMIN_EMAIL),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class AccessRequest(
    @DocumentId val id: String = "",
    val userId: String = "",
    val userEmail: String = "",
    val userName: String = "",
    val userPhotoBase64: String = "",
    val deviceId: String = "",
    val ipAddress: String = "",
    val requestType: String = "FREE_TRIAL_7D", // FREE_TRIAL_7D, PAID_UPI
    val planName: String = "7-Day Free Trial",
    val amountInr: Double = 0.0,
    val validityDays: Long = 7L,
    val utrReference: String = "",
    val paymentScreenshotBase64: String = "",
    val status: String = "PENDING", // PENDING, APPROVED, REJECTED
    val adminEmails: List<String> = listOf(PRIMARY_ADMIN_EMAIL),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class CommunityReview(
    @DocumentId val id: String = "",
    val userId: String = "",
    val userName: String = "",
    val userPhotoBase64: String = "",
    val tradeMessageId: String = "",
    val tradeMessageTitle: String = "",
    val tradeRiskLevel: String = "MEDIUM",
    val topicTag: String = "Commodity",
    val publicContent: String = "",
    val rating: Int = 5,
    val hasBlurredWords: Boolean = false,
    val isPinned: Boolean = false,
    val visibility: String = "VERIFIED_MEMBERS",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class ModerationAlert(
    @DocumentId val id: String = "",
    val userId: String = "",
    val userEmail: String = "",
    val userName: String = "",
    val userPhotoBase64: String = "",
    val deviceId: String = "",
    val ipAddress: String = "",
    val tradeMessageTitle: String = "",
    val originalUneditedText: String = "",
    val blurredText: String = "",
    val detectedWords: String = "",
    val status: String = "OPEN", // OPEN, RESOLVED, BANNED
    val adminEmails: List<String> = listOf(PRIMARY_ADMIN_EMAIL),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class PresenceRecord(
    @DocumentId val id: String = "",
    val userId: String = "",
    val visibility: String = "PUBLIC_PRESENCE",
    @ServerTimestamp val lastActiveAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

object AbusiveWordModerator {
    private val prohibitedWords = setOf(
        "idiot", "stupid", "scam", "scammer", "fraud", "cheat", "cheater",
        "loser", "dumb", "bastard", "fool", "moron", "trash", "garbage",
        "fake", "liar", "chutiya", "madarchod", "bhenchod", "gandu",
        "harami", "sala", "kutte", "kamina", "wtf", "fuck", "shit", "bitch", "asshole"
    )

    private val phoneRegex = Regex("""(?:\+?\d[\d\-\s().]{6,14}\d)""")
    private val emailOrUpiRegex = Regex("""[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+""")
    private val handleOrIdKeywordRegex = Regex(
        """(?i)\b(?:whatsapp|telegram|insta|instagram|contact\s*me|call\s*me|dm\s*me|my\s*id|user\s*id|upi|phone\s*no|mobile\s*no)\b"""
    )
    private val spacedDigitsRegex = Regex("""(?:\d\s*){8,13}""")

    data class ModerationResult(
        val publicText: String,
        val hasBlurredWords: Boolean,
        val detectedWords: List<String>
    )

    data class ProhibitedContactCheckResult(
        val isBlocked: Boolean,
        val reasonSummary: String
    )

    fun checkProhibitedContactOrId(rawText: String): ProhibitedContactCheckResult {
        val detected = mutableListOf<String>()
        phoneRegex.find(rawText)?.value?.trim()?.let {
            detected.add("Phone Number ($it)")
        }
        if (detected.isEmpty()) {
            spacedDigitsRegex.find(rawText)?.value?.trim()?.let {
                detected.add("Digits/Phone ($it)")
            }
        }
        emailOrUpiRegex.find(rawText)?.value?.trim()?.let {
            detected.add("Email/UPI ID ($it)")
        }
        handleOrIdKeywordRegex.find(rawText)?.value?.trim()?.let {
            detected.add("Personal ID/Contact Keyword ($it)")
        }
        return if (detected.isNotEmpty()) {
            ProhibitedContactCheckResult(
                isBlocked = true,
                reasonSummary = detected.joinToString(", ")
            )
        } else {
            ProhibitedContactCheckResult(isBlocked = false, reasonSummary = "")
        }
    }

    fun moderateText(rawText: String): ModerationResult {
        val detected = mutableSetOf<String>()
        val tokenRegex = Regex("""\b[A-Za-z]+\b""")
        val moderated = tokenRegex.replace(rawText) { match ->
            val word = match.value
            if (prohibitedWords.contains(word.lowercase(Locale.ROOT))) {
                detected.add(word.lowercase(Locale.ROOT))
                "▓".repeat(word.length.coerceAtLeast(4))
            } else {
                word
            }
        }
        return ModerationResult(
            publicText = moderated,
            hasBlurredWords = detected.isNotEmpty(),
            detectedWords = detected.toList()
        )
    }
}
