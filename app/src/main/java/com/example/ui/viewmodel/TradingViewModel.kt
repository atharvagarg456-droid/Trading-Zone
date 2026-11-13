package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.SecurityPreferences
import com.example.data.model.AccessRequest
import com.example.data.model.AdminSettings
import com.example.data.model.BroadcastMessage
import com.example.data.model.CommunityReview
import com.example.data.model.DeviceRecord
import com.example.data.model.MessageReadReceipt
import com.example.data.model.ModerationAlert
import com.example.data.model.PRIMARY_ADMIN_EMAIL
import com.example.data.model.PricingConfig
import com.example.data.model.UserProfile
import com.example.data.repository.TradingRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Locale

@OptIn(ExperimentalCoroutinesApi::class)
class TradingViewModel(
    private val repository: TradingRepository,
    private val securityPreferences: SecurityPreferences,
    val currentUserId: String,
    val currentUserEmail: String,
    val currentUserDisplayName: String,
    val deviceId: String,
    val deviceModel: String,
    val ipAddress: String,
    networkConnectivityFlow: Flow<Boolean> = flowOf(true),
    private val onNewOrEditedMessageAlert: (BroadcastMessage) -> Unit = {},
    private val onCyclePaymentReminderAlert: (isExpired: Boolean, daysLeft: Int) -> Unit = { _, _ -> }
) : ViewModel() {

    val isNetworkConnected: StateFlow<Boolean> = networkConnectivityFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = true
        )

    private val _isAppLockSetupComplete = MutableStateFlow(
        securityPreferences.isPinConfigured(currentUserId)
    )
    val isAppLockSetupComplete: StateFlow<Boolean> = _isAppLockSetupComplete.asStateFlow()

    private val _isSessionUnlocked = MutableStateFlow(false)
    val isSessionUnlocked: StateFlow<Boolean> = _isSessionUnlocked.asStateFlow()

    private val _isBiometricEnabled = MutableStateFlow(
        securityPreferences.isBiometricEnabled(currentUserId)
    )
    val isBiometricEnabled: StateFlow<Boolean> = _isBiometricEnabled.asStateFlow()

    private val _notificationsEnabled = MutableStateFlow(
        securityPreferences.isNotificationsEnabled()
    )
    val notificationsEnabled: StateFlow<Boolean> = _notificationsEnabled.asStateFlow()

    private val _offlineDataReminderEnabled = MutableStateFlow(
        securityPreferences.isOfflineDataReminderEnabled()
    )
    val offlineDataReminderEnabled: StateFlow<Boolean> = _offlineDataReminderEnabled.asStateFlow()

    private val _offlineReminderIntervalMinutes = MutableStateFlow(
        securityPreferences.getOfflineReminderIntervalMinutes()
    )
    val offlineReminderIntervalMinutes: StateFlow<Int> = _offlineReminderIntervalMinutes.asStateFlow()

    private val _cycleExpiryNotificationsEnabled = MutableStateFlow(
        securityPreferences.isCycleExpiryNotificationsEnabled()
    )
    val cycleExpiryNotificationsEnabled: StateFlow<Boolean> = _cycleExpiryNotificationsEnabled.asStateFlow()

    private val _compactFeedMode = MutableStateFlow(
        securityPreferences.isCompactFeedMode()
    )
    val compactFeedMode: StateFlow<Boolean> = _compactFeedMode.asStateFlow()

    private val _largeTextMode = MutableStateFlow(
        securityPreferences.isLargeTextMode()
    )
    val largeTextMode: StateFlow<Boolean> = _largeTextMode.asStateFlow()

    private val _highRiskVibrationEnabled = MutableStateFlow(
        securityPreferences.isHighRiskVibrationEnabled()
    )
    val highRiskVibrationEnabled: StateFlow<Boolean> = _highRiskVibrationEnabled.asStateFlow()

    private val _localSecurityQuestion = MutableStateFlow(
        securityPreferences.getSecurityQuestion(currentUserId)
    )
    val localSecurityQuestion: StateFlow<String> = _localSecurityQuestion.asStateFlow()

    private val _appleWebAppUrl = MutableStateFlow(
        securityPreferences.getAppleWebAppUrl()
    )
    val appleWebAppUrl: StateFlow<String> = _appleWebAppUrl.asStateFlow()

    private val _androidApkShareUrl = MutableStateFlow(
        securityPreferences.getAndroidApkShareUrl()
    )
    val androidApkShareUrl: StateFlow<String> = _androidApkShareUrl.asStateFlow()

    private val _localOpenedMessageIds = MutableStateFlow(
        securityPreferences.getOpenedMessageIds(currentUserId)
    )

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    private val _isBusy = MutableStateFlow(false)
    val isBusy: StateFlow<Boolean> = _isBusy.asStateFlow()

    private var knownMessageSignatures = mutableMapOf<String, String>()
    private var hasInitializedMessages = false
    private var hasSentCycleAlertInSession = false

    val adminSettings: StateFlow<AdminSettings> = repository.observeAdminSettings()
        .catch { emit(AdminSettings()) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = AdminSettings()
        )

    val isUserAdmin: StateFlow<Boolean> = adminSettings
        .combine(MutableStateFlow(currentUserEmail.lowercase(Locale.ROOT))) { settings, email ->
            email == PRIMARY_ADMIN_EMAIL.lowercase(Locale.ROOT) ||
                settings.adminEmails.any { it.equals(email, ignoreCase = true) }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = currentUserEmail.equals(PRIMARY_ADMIN_EMAIL, ignoreCase = true)
        )

    val userProfile: StateFlow<UserProfile?> = repository.observeUserProfile(currentUserId)
        .onEach { profile ->
            if (profile != null && !hasSentCycleAlertInSession && !isUserAdmin.value) {
                val daysLeft = profile.daysRemaining()
                if (profile.accountStatus == "APPROVED" &&
                    (profile.entitlementStatus == "TRIAL_ACTIVE" || profile.entitlementStatus == "PAID_ACTIVE")
                ) {
                    if (daysLeft <= 0 || daysLeft <= 2) {
                        hasSentCycleAlertInSession = true
                        if (_notificationsEnabled.value && _cycleExpiryNotificationsEnabled.value) {
                            onCyclePaymentReminderAlert(daysLeft <= 0, daysLeft)
                        }
                    }
                }
            }
        }
        .catch { emit(null) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = null
        )

    val deviceRecord: StateFlow<DeviceRecord?> = repository.observeDeviceRecord(deviceId)
        .catch { emit(null) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = null
        )

    val broadcastMessages: StateFlow<List<BroadcastMessage>> = repository.observeBroadcastMessages()
        .onEach { list ->
            if (!hasInitializedMessages) {
                list.forEach { msg ->
                    knownMessageSignatures[msg.id] = "${msg.title}|${msg.content}|${msg.isEdited}"
                }
                hasInitializedMessages = true
            } else {
                list.forEach { msg ->
                    val sig = "${msg.title}|${msg.content}|${msg.isEdited}"
                    val prev = knownMessageSignatures[msg.id]
                    if (prev == null || prev != sig) {
                        knownMessageSignatures[msg.id] = sig
                        if (_notificationsEnabled.value) {
                            onNewOrEditedMessageAlert(msg)
                        }
                    }
                }
            }
        }
        .catch { emit(emptyList()) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = emptyList()
        )

    private val myMessageReadReceipts: StateFlow<List<MessageReadReceipt>> =
        repository.observeMyMessageReadReceipts(currentUserId)
            .catch { emit(emptyList()) }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000L),
                initialValue = emptyList()
            )

    val openedMessageIds: StateFlow<Set<String>> = combine(
        _localOpenedMessageIds,
        myMessageReadReceipts
    ) { localIds, remoteReceipts ->
        localIds + remoteReceipts.map { it.messageId }.filter { it.isNotBlank() }.toSet()
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000L),
        initialValue = securityPreferences.getOpenedMessageIds(currentUserId)
    )

    val allMessageReadReceiptsForAdmin: StateFlow<List<MessageReadReceipt>> = isUserAdmin
        .flatMapLatest { admin ->
            if (admin) {
                repository.observeAllMessageReadReceiptsForAdmin(currentUserEmail)
                    .catch { emit(emptyList()) }
            } else {
                flowOf(emptyList())
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = emptyList()
        )

    val communityReviews: StateFlow<List<CommunityReview>> = repository.observeCommunityReviews()
        .catch { emit(emptyList()) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = emptyList()
        )

    val onlinePeopleCount: StateFlow<Int> = repository.observeOnlinePeopleCount()
        .catch { emit(1) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = 1
        )

    val pricingConfig: StateFlow<PricingConfig> = repository.observePricingConfig()
        .catch { emit(PricingConfig()) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = PricingConfig()
        )

    val myAccessRequests: StateFlow<List<AccessRequest>> =
        repository.observeMyAccessRequests(currentUserId)
            .catch { emit(emptyList()) }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000L),
                initialValue = emptyList()
            )

    val allUsersForAdmin: StateFlow<List<UserProfile>> = isUserAdmin
        .flatMapLatest { admin ->
            if (admin) {
                repository.observeAllUsersForAdmin(currentUserEmail)
                    .catch { emit(emptyList()) }
            } else {
                flowOf(emptyList())
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = emptyList()
        )

    val allAccessRequestsForAdmin: StateFlow<List<AccessRequest>> = isUserAdmin
        .flatMapLatest { admin ->
            if (admin) {
                repository.observeAllAccessRequestsForAdmin(currentUserEmail)
                    .catch { emit(emptyList()) }
            } else {
                flowOf(emptyList())
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = emptyList()
        )

    val moderationAlertsForAdmin: StateFlow<List<ModerationAlert>> = isUserAdmin
        .flatMapLatest { admin ->
            if (admin) {
                repository.observeModerationAlertsForAdmin(currentUserEmail)
                    .catch { emit(emptyList()) }
            } else {
                flowOf(emptyList())
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = emptyList()
        )

    init {
        viewModelScope.launch {
            while (isActive) {
                try {
                    repository.touchUserPresence(
                        userId = currentUserId,
                        ipAddress = ipAddress,
                        deviceModel = deviceModel
                    )
                } catch (_: Exception) {
                }
                delay(120_000L)
            }
        }
    }

    fun clearStatusMessage() {
        _statusMessage.value = null
    }

    fun completeInitialAppLockSetup(
        pin: String,
        enableBiometric: Boolean,
        securityQuestion: String,
        securityAnswer: String
    ) {
        securityPreferences.savePin(currentUserId, pin)
        securityPreferences.setBiometricEnabled(currentUserId, enableBiometric)
        securityPreferences.saveSecurityQuestionAndAnswer(
            currentUserId,
            securityQuestion,
            securityAnswer
        )
        _localSecurityQuestion.value = securityQuestion.trim()
        _isBiometricEnabled.value = enableBiometric
        _isAppLockSetupComplete.value = true
        _isSessionUnlocked.value = true
        viewModelScope.launch {
            repository.syncAppLockPinInProfile(pin)
        }
    }

    fun verifyPinAndUnlock(candidatePin: String): Boolean {
        val remotePin = userProfile.value?.appLockPin.orEmpty()
        val isMatch = securityPreferences.verifyPin(currentUserId, candidatePin) ||
            (remotePin.isNotBlank() && remotePin == candidatePin.trim())
        if (isMatch) {
            securityPreferences.savePin(currentUserId, candidatePin.trim())
            _isSessionUnlocked.value = true
        }
        return isMatch
    }

    fun unlockViaBiometricSuccess() {
        _isSessionUnlocked.value = true
    }

    fun lockSessionNow() {
        _isSessionUnlocked.value = false
    }

    fun recoverPinWithSecurityAnswer(candidateAnswer: String, newPin: String): Boolean {
        val remoteAnswer = userProfile.value?.securityAnswer?.trim()?.lowercase().orEmpty()
        val isMatch = securityPreferences.verifySecurityAnswer(currentUserId, candidateAnswer) ||
            (remoteAnswer.isNotBlank() && remoteAnswer == candidateAnswer.trim().lowercase())
        if (isMatch && newPin.trim().length >= 4) {
            securityPreferences.savePin(currentUserId, newPin.trim())
            _isSessionUnlocked.value = true
            viewModelScope.launch {
                repository.syncAppLockPinInProfile(newPin.trim())
            }
            return true
        }
        return false
    }

    fun updateAppLockPin(currentPin: String, newPin: String): Boolean {
        if (!verifyPinAndUnlock(currentPin)) return false
        if (newPin.trim().length < 4) return false
        securityPreferences.savePin(currentUserId, newPin.trim())
        viewModelScope.launch {
            repository.syncAppLockPinInProfile(newPin.trim())
        }
        _statusMessage.value = "App Lock PIN updated."
        return true
    }

    fun updateSecurityQuestionAndAnswer(question: String, answer: String) {
        if (question.isBlank() || answer.isBlank()) {
            _statusMessage.value = "Please enter both a security question and answer."
            return
        }
        securityPreferences.saveSecurityQuestionAndAnswer(currentUserId, question, answer)
        _localSecurityQuestion.value = question.trim()
        viewModelScope.launch {
            repository.updateSecurityQuestionInProfile(question, answer)
                .onSuccess {
                    _statusMessage.value = "Security question & answer updated."
                }
                .onFailure {
                    _statusMessage.value = it.localizedMessage ?: "Failed to update security question."
                }
        }
    }

    fun setBiometricPreference(enabled: Boolean) {
        securityPreferences.setBiometricEnabled(currentUserId, enabled)
        _isBiometricEnabled.value = enabled
    }

    fun setNotificationsPreference(enabled: Boolean) {
        securityPreferences.setNotificationsEnabled(enabled)
        _notificationsEnabled.value = enabled
    }

    fun setOfflineDataReminderPreference(enabled: Boolean) {
        securityPreferences.setOfflineDataReminderEnabled(enabled)
        _offlineDataReminderEnabled.value = enabled
    }

    fun setOfflineReminderIntervalPreference(minutes: Int) {
        securityPreferences.setOfflineReminderIntervalMinutes(minutes)
        _offlineReminderIntervalMinutes.value = minutes
    }

    fun setCycleExpiryNotificationsPreference(enabled: Boolean) {
        securityPreferences.setCycleExpiryNotificationsEnabled(enabled)
        _cycleExpiryNotificationsEnabled.value = enabled
    }

    fun setCompactFeedMode(enabled: Boolean) {
        securityPreferences.setCompactFeedMode(enabled)
        _compactFeedMode.value = enabled
    }

    fun setLargeTextMode(enabled: Boolean) {
        securityPreferences.setLargeTextMode(enabled)
        _largeTextMode.value = enabled
    }

    fun setHighRiskVibrationPreference(enabled: Boolean) {
        securityPreferences.setHighRiskVibrationEnabled(enabled)
        _highRiskVibrationEnabled.value = enabled
    }

    fun updateShareUrls(appleUrl: String, androidUrl: String) {
        securityPreferences.setAppleWebAppUrl(appleUrl)
        securityPreferences.setAndroidApkShareUrl(androidUrl)
        _appleWebAppUrl.value = securityPreferences.getAppleWebAppUrl()
        _androidApkShareUrl.value = securityPreferences.getAndroidApkShareUrl()
        _statusMessage.value = "Permanent Website QR & App Share links updated!"
    }

    fun markMessageAsOpened(message: BroadcastMessage) {
        if (message.id.isBlank()) return
        securityPreferences.markMessageOpened(currentUserId, message.id)
        _localOpenedMessageIds.value = securityPreferences.getOpenedMessageIds(currentUserId)
        val profile = userProfile.value ?: return
        viewModelScope.launch {
            repository.recordMessageReadReceipt(
                messageId = message.id,
                profile = profile
            )
        }
    }

    fun submitUserRegistration(
        fullName: String,
        phoneNumber: String,
        city: String,
        photoBase64: String,
        securityQuestion: String,
        securityAnswer: String,
        termsAccepted: Boolean
    ) {
        viewModelScope.launch {
            _isBusy.value = true
            try {
                securityPreferences.saveSecurityQuestionAndAnswer(
                    currentUserId,
                    securityQuestion,
                    securityAnswer
                )
                _localSecurityQuestion.value = securityQuestion.trim()
                val pinPlain = securityPreferences.getPlainPin(currentUserId)
                repository.submitUserRegistration(
                    email = currentUserEmail,
                    fullName = fullName,
                    phoneNumber = phoneNumber,
                    city = city,
                    photoBase64 = photoBase64,
                    deviceId = deviceId,
                    deviceModel = deviceModel,
                    ipAddress = ipAddress,
                    securityQuestion = securityQuestion,
                    securityAnswer = securityAnswer,
                    termsAccepted = termsAccepted,
                    isCallerAdmin = isUserAdmin.value,
                    appLockPin = pinPlain
                ).onSuccess {
                    _statusMessage.value = if (isUserAdmin.value) {
                        "Admin profile registered and activated."
                    } else {
                        "Registration submitted! Waiting for Admin verification."
                    }
                }.onFailure { err ->
                    _statusMessage.value = err.localizedMessage ?: "Registration failed."
                }
            } catch (e: CancellationException) {
                throw e
            } finally {
                _isBusy.value = false
            }
        }
    }

    fun requestProfileEdit(
        fullName: String,
        phoneNumber: String,
        city: String,
        photoBase64: String
    ) {
        viewModelScope.launch {
            _isBusy.value = true
            try {
                repository.requestProfileEdit(
                    newFullName = fullName,
                    newPhoneNumber = phoneNumber,
                    newCity = city,
                    newPhotoBase64 = photoBase64,
                    isCallerAdmin = isUserAdmin.value
                ).onSuccess {
                    _statusMessage.value = if (isUserAdmin.value) {
                        "Profile updated."
                    } else {
                        "Profile edit request sent to Admin for approval."
                    }
                }.onFailure { err ->
                    _statusMessage.value = err.localizedMessage ?: "Failed to request profile edit."
                }
            } catch (e: CancellationException) {
                throw e
            } finally {
                _isBusy.value = false
            }
        }
    }

    fun reviewProfileEditRequest(user: UserProfile, approve: Boolean) {
        viewModelScope.launch {
            _isBusy.value = true
            try {
                repository.reviewProfileEditRequest(user, approve)
                    .onSuccess {
                        _statusMessage.value = if (approve) {
                            "Approved profile update for ${user.fullName}."
                        } else {
                            "Rejected profile edit request."
                        }
                    }
                    .onFailure { err ->
                        _statusMessage.value = err.localizedMessage ?: "Failed to review profile edit."
                    }
            } catch (e: CancellationException) {
                throw e
            } finally {
                _isBusy.value = false
            }
        }
    }

    fun requestOneWeekFreeTrial() {
        val profile = userProfile.value ?: return
        viewModelScope.launch {
            _isBusy.value = true
            try {
                repository.requestSevenDayFreeTrial(profile, deviceRecord.value)
                    .onSuccess {
                        _statusMessage.value = "7-Day Free Trial requested! Pending Admin approval."
                    }
                    .onFailure { err ->
                        _statusMessage.value = err.localizedMessage ?: "Could not request Free Trial."
                    }
            } catch (e: CancellationException) {
                throw e
            } finally {
                _isBusy.value = false
            }
        }
    }

    fun submitUpiPayment(
        planName: String,
        amountInr: Double,
        validityDays: Long,
        utrReference: String,
        paymentScreenshotBase64: String
    ) {
        val profile = userProfile.value ?: return
        viewModelScope.launch {
            _isBusy.value = true
            try {
                repository.submitUpiPaymentRequest(
                    profile = profile,
                    amountInr = amountInr,
                    validityDays = validityDays,
                    utrReference = utrReference,
                    planName = planName,
                    paymentScreenshotBase64 = paymentScreenshotBase64
                ).onSuccess {
                    _statusMessage.value = "Payment proof submitted for Admin verification."
                }.onFailure { err ->
                    _statusMessage.value = err.localizedMessage ?: "Payment submission failed."
                }
            } catch (e: CancellationException) {
                throw e
            } finally {
                _isBusy.value = false
            }
        }
    }

    fun submitCommunityReview(
        tradeMessageId: String,
        tradeMessageTitle: String,
        tradeRiskLevel: String,
        topicTag: String,
        opinionText: String,
        rating: Int = 5
    ) {
        val profile = userProfile.value ?: return
        viewModelScope.launch {
            _isBusy.value = true
            try {
                repository.submitCommunityReview(
                    profile = profile,
                    tradeMessageId = tradeMessageId,
                    tradeMessageTitle = tradeMessageTitle,
                    tradeRiskLevel = tradeRiskLevel,
                    topicTag = topicTag,
                    rawOpinionText = opinionText,
                    rating = rating,
                    dailyLimitPerUser = pricingConfig.value.dailyReviewLimitPerUser.toInt().coerceAtLeast(1),
                    isCallerAdmin = isUserAdmin.value
                ).onSuccess { outcome ->
                    _statusMessage.value = if (outcome.hadAbusiveWordsBlurred) {
                        "Commodity message posted (inappropriate words were blurred and reported to Admin)."
                    } else {
                        "Commodity message posted."
                    }
                }.onFailure { err ->
                    _statusMessage.value = err.localizedMessage ?: "Could not post Commodity message."
                }
            } catch (e: CancellationException) {
                throw e
            } finally {
                _isBusy.value = false
            }
        }
    }

    fun setCommodityMessagePinned(reviewId: String, pinned: Boolean) {
        viewModelScope.launch {
            repository.setCommodityMessagePinned(reviewId, pinned)
                .onSuccess {
                    _statusMessage.value = if (pinned) {
                        "Commodity message pinned to top."
                    } else {
                        "Commodity message unpinned."
                    }
                }
                .onFailure { err ->
                    _statusMessage.value = err.localizedMessage ?: "Failed to update pin status."
                }
        }
    }

    fun deleteCommunityReview(reviewId: String) {
        viewModelScope.launch {
            repository.deleteCommunityReview(reviewId)
                .onSuccess { _statusMessage.value = "Commodity message deleted." }
                .onFailure { _statusMessage.value = it.localizedMessage ?: "Failed to delete." }
        }
    }

    fun setCommodityChatLocked(locked: Boolean) {
        viewModelScope.launch {
            repository.setCommodityChatLocked(pricingConfig.value, locked)
                .onSuccess {
                    _statusMessage.value = if (locked) {
                        "Commodity chat paused for members."
                    } else {
                        "Commodity chat unlocked for members."
                    }
                }
                .onFailure {
                    _statusMessage.value = it.localizedMessage ?: "Failed to update Commodity lock."
                }
        }
    }

    fun publishMessage(
        title: String,
        content: String,
        category: String,
        riskLevel: String,
        markedPhotoBase64: String,
        linkUrl: String,
        linkTitle: String,
        isPinned: Boolean,
        replyToMessageId: String = "",
        replyToTitle: String = "",
        replyToSnippet: String = "",
        replyToRiskLevel: String = ""
    ) {
        val authorName = userProfile.value?.fullName?.ifBlank { "Atharva Garg" } ?: "Atharva Garg"
        viewModelScope.launch {
            _isBusy.value = true
            try {
                repository.publishBroadcastMessage(
                    title = title,
                    content = content,
                    category = category,
                    riskLevel = riskLevel,
                    markedPhotoBase64 = markedPhotoBase64,
                    linkUrl = linkUrl,
                    linkTitle = linkTitle,
                    isPinned = isPinned,
                    authorName = authorName,
                    replyToMessageId = replyToMessageId,
                    replyToTitle = replyToTitle,
                    replyToSnippet = replyToSnippet,
                    replyToRiskLevel = replyToRiskLevel
                ).onSuccess {
                    _statusMessage.value = "Broadcast message published!"
                }.onFailure { err ->
                    _statusMessage.value = err.localizedMessage ?: "Failed to publish message."
                }
            } catch (e: CancellationException) {
                throw e
            } finally {
                _isBusy.value = false
            }
        }
    }

    fun editMessage(
        messageId: String,
        title: String,
        content: String,
        category: String,
        riskLevel: String,
        markedPhotoBase64: String,
        linkUrl: String,
        linkTitle: String,
        isPinned: Boolean,
        replyToMessageId: String = "",
        replyToTitle: String = "",
        replyToSnippet: String = "",
        replyToRiskLevel: String = ""
    ) {
        val authorName = userProfile.value?.fullName?.ifBlank { "Atharva Garg" } ?: "Atharva Garg"
        viewModelScope.launch {
            _isBusy.value = true
            try {
                repository.editBroadcastMessage(
                    messageId = messageId,
                    title = title,
                    content = content,
                    category = category,
                    riskLevel = riskLevel,
                    markedPhotoBase64 = markedPhotoBase64,
                    linkUrl = linkUrl,
                    linkTitle = linkTitle,
                    isPinned = isPinned,
                    authorName = authorName,
                    replyToMessageId = replyToMessageId,
                    replyToTitle = replyToTitle,
                    replyToSnippet = replyToSnippet,
                    replyToRiskLevel = replyToRiskLevel
                ).onSuccess {
                    _statusMessage.value = "Broadcast message updated!"
                }.onFailure { err ->
                    _statusMessage.value = err.localizedMessage ?: "Failed to update message."
                }
            } catch (e: CancellationException) {
                throw e
            } finally {
                _isBusy.value = false
            }
        }
    }

    fun deleteMessage(messageId: String) {
        viewModelScope.launch {
            repository.deleteBroadcastMessage(messageId)
                .onSuccess { _statusMessage.value = "Broadcast message deleted." }
                .onFailure { _statusMessage.value = it.localizedMessage ?: "Failed to delete message." }
        }
    }

    fun approveUserAccount(userId: String, grantSevenDayTrial: Boolean) {
        viewModelScope.launch {
            repository.approveUserAccount(userId, grantSevenDayTrial)
                .onSuccess {
                    _statusMessage.value = if (grantSevenDayTrial) {
                        "Member approved with 7-Day Free Trial."
                    } else {
                        "Member account approved."
                    }
                }
                .onFailure { _statusMessage.value = it.localizedMessage ?: "Approval failed." }
        }
    }

    fun updateUserSubscriptionDays(userId: String, planName: String, daysLeft: Int) {
        viewModelScope.launch {
            repository.updateUserSubscriptionDays(userId, planName, daysLeft)
                .onSuccess { _statusMessage.value = "Updated member validity to $daysLeft days." }
                .onFailure { _statusMessage.value = it.localizedMessage ?: "Failed to update validity." }
        }
    }

    fun adminResetUserPin(user: UserProfile, newPin: String) {
        viewModelScope.launch {
            repository.adminResetUserPin(user.userId, newPin)
                .onSuccess { _statusMessage.value = "Updated App Lock PIN for ${user.fullName}." }
                .onFailure { _statusMessage.value = it.localizedMessage ?: "Failed to reset PIN." }
        }
    }

    fun setUserCommodityMuted(user: UserProfile, muted: Boolean) {
        viewModelScope.launch {
            repository.setUserCommodityMuted(user.userId, muted)
                .onSuccess {
                    _statusMessage.value = if (muted) {
                        "${user.fullName} muted from Commodity messaging."
                    } else {
                        "${user.fullName} unmuted in Commodity."
                    }
                }
                .onFailure { _statusMessage.value = it.localizedMessage ?: "Failed to update mute status." }
        }
    }

    fun adminResetUserDailyCommodityQuota(user: UserProfile) {
        viewModelScope.launch {
            repository.adminResetUserDailyCommodityQuota(user.userId)
                .onSuccess { _statusMessage.value = "Reset daily Commodity quota for ${user.fullName}." }
                .onFailure { _statusMessage.value = it.localizedMessage ?: "Failed to reset quota." }
        }
    }

    fun reviewAccessRequest(request: AccessRequest, approve: Boolean) {
        viewModelScope.launch {
            repository.reviewAccessRequest(request, approve)
                .onSuccess {
                    _statusMessage.value = if (approve) {
                        "Approved ${request.planName} for ${request.userName}."
                    } else {
                        "Rejected request from ${request.userName}."
                    }
                }
                .onFailure { _statusMessage.value = it.localizedMessage ?: "Review action failed." }
        }
    }

    fun resolveModerationAlert(alertId: String, status: String) {
        viewModelScope.launch {
            repository.resolveModerationAlert(alertId, status)
                .onSuccess { _statusMessage.value = "Moderation alert updated." }
                .onFailure { _statusMessage.value = it.localizedMessage ?: "Failed to update alert." }
        }
    }

    fun banUserFromModerationAlert(alert: ModerationAlert) {
        viewModelScope.launch {
            repository.banUserFromModerationAlert(alert)
                .onSuccess { _statusMessage.value = "Banned ${alert.userName} and hardware device." }
                .onFailure { _statusMessage.value = it.localizedMessage ?: "Ban failed." }
        }
    }

    fun setDeviceBanStatus(user: UserProfile, ban: Boolean) {
        viewModelScope.launch {
            repository.setDeviceBanStatus(user, ban)
                .onSuccess {
                    _statusMessage.value = if (ban) {
                        "Banned ${user.fullName} and device ${user.deviceId}."
                    } else {
                        "Unbanned ${user.fullName}."
                    }
                }
                .onFailure { _statusMessage.value = it.localizedMessage ?: "Failed to update ban." }
        }
    }

    fun removeUserAndBanDevice(user: UserProfile) {
        viewModelScope.launch {
            repository.removeUserAndBanDevice(user)
                .onSuccess { _statusMessage.value = "Removed ${user.fullName} and banned device." }
                .onFailure { _statusMessage.value = it.localizedMessage ?: "Failed to remove user." }
        }
    }

    fun savePricingAndQrConfig(
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
    ) {
        viewModelScope.launch {
            _isBusy.value = true
            try {
                repository.savePricingConfig(
                    planName = planName,
                    priceInr = priceInr,
                    validityDays = validityDays,
                    upiId = upiId,
                    upiMobileNumber = upiMobileNumber,
                    qrCodeBase64 = qrCodeBase64,
                    qrOnlyMode = qrOnlyMode,
                    plan2Enabled = plan2Enabled,
                    plan2Name = plan2Name,
                    plan2PriceInr = plan2PriceInr,
                    plan2ValidityDays = plan2ValidityDays,
                    plan2QrCodeBase64 = plan2QrCodeBase64,
                    plan2QrOnlyMode = plan2QrOnlyMode,
                    plan3Enabled = plan3Enabled,
                    plan3Name = plan3Name,
                    plan3PriceInr = plan3PriceInr,
                    plan3ValidityDays = plan3ValidityDays,
                    plan3QrCodeBase64 = plan3QrCodeBase64,
                    plan3QrOnlyMode = plan3QrOnlyMode,
                    dailyReviewLimitPerUser = dailyReviewLimitPerUser,
                    commodityChatLocked = commodityChatLocked,
                    trialDays = trialDays,
                    benefits = benefits
                ).onSuccess {
                    _statusMessage.value = "Pricing & QR plans saved!"
                }.onFailure { err ->
                    _statusMessage.value = err.localizedMessage ?: "Failed to save pricing config."
                }
            } catch (e: CancellationException) {
                throw e
            } finally {
                _isBusy.value = false
            }
        }
    }

    fun addCoAdminEmail(email: String) {
        val clean = email.trim().lowercase(Locale.ROOT)
        if (clean.isBlank() || !clean.contains("@")) {
            _statusMessage.value = "Enter a valid Google email address."
            return
        }
        val updated = (adminSettings.value.adminEmails + clean).distinct()
        viewModelScope.launch {
            repository.updateCoAdminEmails(updated)
                .onSuccess { _statusMessage.value = "Added Co-Admin $clean." }
                .onFailure { _statusMessage.value = it.localizedMessage ?: "Failed to add Co-Admin." }
        }
    }

    fun removeCoAdminEmail(email: String) {
        val clean = email.trim().lowercase(Locale.ROOT)
        if (clean.equals(PRIMARY_ADMIN_EMAIL, ignoreCase = true)) {
            _statusMessage.value = "Primary Admin cannot be removed."
            return
        }
        val updated = adminSettings.value.adminEmails.filterNot { it.equals(clean, ignoreCase = true) }
        viewModelScope.launch {
            repository.updateCoAdminEmails(updated)
                .onSuccess { _statusMessage.value = "Removed Co-Admin $clean." }
                .onFailure { _statusMessage.value = it.localizedMessage ?: "Failed to remove Co-Admin." }
        }
    }
}
