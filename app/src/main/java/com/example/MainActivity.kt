package com.example

import android.Manifest
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.local.DeviceTelemetry
import com.example.data.local.NetworkMonitor
import com.example.data.local.SecurityPreferences
import com.example.data.model.AppThemePreference
import com.example.data.model.BroadcastMessage
import com.example.data.repository.TradingRepository
import com.example.service.TradingBackgroundService
import com.example.ui.components.NotificationHelper
import com.example.ui.components.PermanentQrAndShareCard
import com.example.ui.screens.*
import com.example.ui.theme.TradingTheme
import com.example.ui.viewmodel.TradingViewModel
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

enum class MainNavTab {
    STREAM,
    COMMODITY,
    MEMBERSHIP,
    SETTINGS,
    ADMIN
}

class MainActivity : FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Screenshot & Screen Recording Protection
        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE
        )

        enableEdgeToEdge()
        NotificationHelper.ensureChannels(this)

        val appContext = applicationContext
        val databaseId = getString(R.string.firestore_database_id)
        val firestore = Firebase.firestore(databaseId)
        val repository = TradingRepository(firestore)
        val securityPrefs = SecurityPreferences(appContext)
        val deviceId = DeviceTelemetry.getPermanentDeviceId(appContext)
        val deviceModel = DeviceTelemetry.getDeviceModelLabel()

        setContent {
            var themePref by remember { mutableStateOf(securityPrefs.getThemePreference()) }
            val isNetworkConnected by remember {
                NetworkMonitor.observeNetworkConnectivity(appContext)
            }.collectAsStateWithLifecycle(initialValue = NetworkMonitor.isCurrentlyConnected(appContext))

            var currentUser by remember { mutableStateOf<FirebaseUser?>(Firebase.auth.currentUser) }
            var ipAddress by remember { mutableStateOf("0.0.0.0") }

            DisposableEffect(Unit) {
                val listener = FirebaseAuth.AuthStateListener { auth ->
                    currentUser = auth.currentUser
                }
                Firebase.auth.addAuthStateListener(listener)
                onDispose {
                    Firebase.auth.removeAuthStateListener(listener)
                }
            }

            LaunchedEffect(Unit) {
                ipAddress = withContext(Dispatchers.IO) {
                    DeviceTelemetry.getDeviceIpAddress()
                }
            }

            // Track offline transitions in preferences for the 1-hour offline mobile data reminder
            LaunchedEffect(isNetworkConnected) {
                if (!isNetworkConnected) {
                    if (securityPrefs.getOfflineSinceMillis() <= 0L) {
                        securityPrefs.setOfflineSinceMillis(System.currentTimeMillis())
                    }
                } else {
                    securityPrefs.setOfflineSinceMillis(0L)
                    securityPrefs.setLastOfflineNotificationSentMillis(0L)
                }
            }

            TradingTheme(themePreference = themePref) {
                val activeUser = currentUser
                if (activeUser == null) {
                    GoogleSignInWelcomeScreen(
                        deviceId = deviceId,
                        ipAddress = ipAddress,
                        isNetworkConnected = isNetworkConnected,
                        onAuthSuccess = {
                            currentUser = Firebase.auth.currentUser
                        }
                    )
                } else {
                    val vm: TradingViewModel = viewModel(
                        key = "vm_${activeUser.uid}",
                        factory = object : ViewModelProvider.Factory {
                            @Suppress("UNCHECKED_CAST")
                            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                                return TradingViewModel(
                                    repository = repository,
                                    securityPreferences = securityPrefs,
                                    currentUserId = activeUser.uid,
                                    currentUserEmail = activeUser.email.orEmpty(),
                                    currentUserDisplayName = activeUser.displayName.orEmpty(),
                                    deviceId = deviceId,
                                    deviceModel = deviceModel,
                                    ipAddress = ipAddress,
                                    networkConnectivityFlow = NetworkMonitor.observeNetworkConnectivity(appContext),
                                    onNewOrEditedMessageAlert = { msg ->
                                        NotificationHelper.showBroadcastNotification(
                                            context = appContext,
                                            isEdited = msg.isEdited
                                        )
                                    },
                                    onCyclePaymentReminderAlert = { isExpired, daysLeft ->
                                        NotificationHelper.showCyclePaymentReminderNotification(
                                            context = appContext,
                                            isExpired = isExpired,
                                            daysLeft = daysLeft
                                        )
                                    }
                                ) as T
                            }
                        }
                    )

                    AuthenticatedTradingWorkspace(
                        viewModel = vm,
                        themePreference = themePref,
                        onChangeTheme = { newTheme ->
                            securityPrefs.setThemePreference(newTheme)
                            themePref = newTheme
                        },
                        onSignedOut = {
                            TradingBackgroundService.stop(appContext)
                            currentUser = null
                        }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuthenticatedTradingWorkspace(
    viewModel: TradingViewModel,
    themePreference: AppThemePreference,
    onChangeTheme: (AppThemePreference) -> Unit,
    onSignedOut: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember { CredentialManager.create(context) }
    val snackbarHostState = remember { SnackbarHostState() }

    val isNetworkConnected by viewModel.isNetworkConnected.collectAsStateWithLifecycle()
    val isAppLockSetupComplete by viewModel.isAppLockSetupComplete.collectAsStateWithLifecycle()
    val isSessionUnlocked by viewModel.isSessionUnlocked.collectAsStateWithLifecycle()
    val isBiometricEnabled by viewModel.isBiometricEnabled.collectAsStateWithLifecycle()
    val notificationsEnabled by viewModel.notificationsEnabled.collectAsStateWithLifecycle()
    val offlineDataReminderEnabled by viewModel.offlineDataReminderEnabled.collectAsStateWithLifecycle()
    val offlineReminderIntervalMinutes by viewModel.offlineReminderIntervalMinutes.collectAsStateWithLifecycle()
    val cycleExpiryNotificationsEnabled by viewModel.cycleExpiryNotificationsEnabled.collectAsStateWithLifecycle()
    val highRiskVibrationEnabled by viewModel.highRiskVibrationEnabled.collectAsStateWithLifecycle()
    val compactFeedMode by viewModel.compactFeedMode.collectAsStateWithLifecycle()
    val largeTextMode by viewModel.largeTextMode.collectAsStateWithLifecycle()
    val localSecurityQuestion by viewModel.localSecurityQuestion.collectAsStateWithLifecycle()
    val appleWebAppUrl by viewModel.appleWebAppUrl.collectAsStateWithLifecycle()
    val androidApkShareUrl by viewModel.androidApkShareUrl.collectAsStateWithLifecycle()

    val isAdmin by viewModel.isUserAdmin.collectAsStateWithLifecycle()
    val userProfile by viewModel.userProfile.collectAsStateWithLifecycle()
    val deviceRecord by viewModel.deviceRecord.collectAsStateWithLifecycle()
    val broadcastMessages by viewModel.broadcastMessages.collectAsStateWithLifecycle()
    val openedMessageIds by viewModel.openedMessageIds.collectAsStateWithLifecycle()
    val allMessageReadReceipts by viewModel.allMessageReadReceiptsForAdmin.collectAsStateWithLifecycle()
    val communityReviews by viewModel.communityReviews.collectAsStateWithLifecycle()
    val onlinePeopleCount by viewModel.onlinePeopleCount.collectAsStateWithLifecycle()
    val pricingConfig by viewModel.pricingConfig.collectAsStateWithLifecycle()
    val adminSettings by viewModel.adminSettings.collectAsStateWithLifecycle()
    val myAccessRequests by viewModel.myAccessRequests.collectAsStateWithLifecycle()
    val allUsersForAdmin by viewModel.allUsersForAdmin.collectAsStateWithLifecycle()
    val allAccessRequestsForAdmin by viewModel.allAccessRequestsForAdmin.collectAsStateWithLifecycle()
    val moderationAlertsForAdmin by viewModel.moderationAlertsForAdmin.collectAsStateWithLifecycle()
    val statusMessage by viewModel.statusMessage.collectAsStateWithLifecycle()
    val isBusy by viewModel.isBusy.collectAsStateWithLifecycle()

    var selectedTab by remember { mutableStateOf(MainNavTab.STREAM) }
    var editingMessage by remember { mutableStateOf<BroadcastMessage?>(null) }
    var replyingToMessage by remember { mutableStateOf<BroadcastMessage?>(null) }
    var showQuickQrShareDialog by remember { mutableStateOf(false) }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { /* handled gracefully */ }

    LaunchedEffect(viewModel.currentUserId) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        TradingBackgroundService.start(context)
    }

    LaunchedEffect(statusMessage) {
        statusMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearStatusMessage()
        }
    }

    val isDeviceBanned = deviceRecord?.isBanned == true || userProfile?.accountStatus == "BANNED"
    val hasCompletedRegistration = userProfile != null && userProfile?.fullName?.isNotBlank() == true
    val isApprovedMember = isAdmin || userProfile?.accountStatus == "APPROVED"
    val showMainTabs = isAppLockSetupComplete && isSessionUnlocked && !isDeviceBanned && hasCompletedRegistration && isApprovedMember

    val signOutAction: () -> Unit = {
        performGoogleSignOut(
            credentialManager = credentialManager,
            scope = scope,
            onSignOutComplete = onSignedOut
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.TrendingUp,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = "Trading Zone",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.ExtraBold
                                )
                                Text(
                                    text = if (isAdmin) "Admin Studio • Live" else "Advisory & Commodity Stream",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    },
                    actions = {
                        // Quick Permanent QR & Share Website/App button for Apple & Android devices
                        IconButton(
                            onClick = { showQuickQrShareDialog = true },
                            modifier = Modifier.testTag("top_bar_share_qr_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.QrCode2,
                                contentDescription = "Share Apple Website & Permanent QR Code",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }

                        // Live Offline / No Network Connected Symbol Pill in TopAppBar
                        if (!isNetworkConnected) {
                            Surface(
                                shape = RoundedCornerShape(50),
                                color = Color(0xFFEF4444).copy(alpha = 0.2f),
                                border = BorderStroke(1.dp, Color(0xFFEF4444)),
                                modifier = Modifier
                                    .padding(end = 8.dp)
                                    .testTag("top_bar_no_network_symbol")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.SignalWifiOff,
                                        contentDescription = "No Network Connected",
                                        tint = Color(0xFFEF4444),
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "No Network",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFFEF4444)
                                    )
                                }
                            }
                        } else if (showMainTabs && userProfile != null) {
                            val days = userProfile?.daysRemaining() ?: 0
                            val entitled = isAdmin || (userProfile?.isEntitledNow() == true)
                            Surface(
                                shape = RoundedCornerShape(50),
                                color = if (entitled) {
                                    Color(0xFF10B981).copy(alpha = 0.16f)
                                } else {
                                    Color(0xFFF59E0B).copy(alpha = 0.16f)
                                },
                                modifier = Modifier.padding(end = 8.dp)
                            ) {
                                Text(
                                    text = when {
                                        isAdmin -> "ADMIN"
                                        userProfile?.entitlementStatus == "TRIAL_ACTIVE" && days > 0 ->
                                            "TRIAL • ${days}d"
                                        days > 0 -> "ACTIVE • ${days}d"
                                        else -> "EXPIRED"
                                    },
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (entitled) Color(0xFF10B981) else Color(0xFFF59E0B)
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )

                // Prominent Animated Offline / No-Network Banner across the screen
                Box(modifier = Modifier.padding(horizontal = 12.dp)) {
                    NoNetworkStatusBanner(isNetworkConnected = isNetworkConnected)
                }
            }
        },
        bottomBar = {
            if (showMainTabs) {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    tonalElevation = 8.dp
                ) {
                    NavigationBarItem(
                        selected = selectedTab == MainNavTab.STREAM,
                        onClick = { selectedTab = MainNavTab.STREAM },
                        icon = { Icon(Icons.Default.Campaign, contentDescription = "Messages") },
                        label = { Text("Messages") },
                        modifier = Modifier.testTag("nav_stream")
                    )
                    NavigationBarItem(
                        selected = selectedTab == MainNavTab.COMMODITY,
                        onClick = { selectedTab = MainNavTab.COMMODITY },
                        icon = { Icon(Icons.Default.Forum, contentDescription = "Commodity") },
                        label = { Text("Commodity") },
                        modifier = Modifier.testTag("nav_reviews")
                    )
                    NavigationBarItem(
                        selected = selectedTab == MainNavTab.MEMBERSHIP,
                        onClick = { selectedTab = MainNavTab.MEMBERSHIP },
                        icon = { Icon(Icons.Default.QrCode2, contentDescription = "Plans") },
                        label = { Text("Plans") },
                        modifier = Modifier.testTag("nav_membership")
                    )
                    NavigationBarItem(
                        selected = selectedTab == MainNavTab.SETTINGS,
                        onClick = { selectedTab = MainNavTab.SETTINGS },
                        icon = { Icon(Icons.Default.Settings, contentDescription = "Settings") },
                        label = { Text("Settings") },
                        modifier = Modifier.testTag("nav_settings")
                    )
                    if (isAdmin) {
                        NavigationBarItem(
                            selected = selectedTab == MainNavTab.ADMIN,
                            onClick = { selectedTab = MainNavTab.ADMIN },
                            icon = { Icon(Icons.Default.AdminPanelSettings, contentDescription = "Admin") },
                            label = { Text("Admin") },
                            modifier = Modifier.testTag("nav_admin")
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            when {
                !isAppLockSetupComplete -> {
                    AppLockSetupScreen(
                        userEmail = viewModel.currentUserEmail,
                        onCompleteSetup = { pin, bio, question, answer ->
                            viewModel.completeInitialAppLockSetup(pin, bio, question, answer)
                        },
                        onSignOut = signOutAction
                    )
                }
                !isSessionUnlocked -> {
                    AppLockUnlockScreen(
                        userEmail = viewModel.currentUserEmail,
                        isBiometricEnabled = isBiometricEnabled,
                        securityQuestion = localSecurityQuestion.ifBlank {
                            userProfile?.securityQuestion ?: "What is my favorite commodity or stock?"
                        },
                        onVerifyPin = { viewModel.verifyPinAndUnlock(it) },
                        onRecoverPin = { answer, newPin ->
                            viewModel.recoverPinWithSecurityAnswer(answer, newPin)
                        },
                        onBiometricUnlockSuccess = { viewModel.unlockViaBiometricSuccess() },
                        onSignOut = signOutAction
                    )
                }
                !hasCompletedRegistration -> {
                    MandatoryPhotoRegistrationScreen(
                        userEmail = viewModel.currentUserEmail,
                        initialDisplayName = viewModel.currentUserDisplayName,
                        initialSecurityQuestion = localSecurityQuestion,
                        deviceId = viewModel.deviceId,
                        deviceModel = viewModel.deviceModel,
                        ipAddress = viewModel.ipAddress,
                        isBusy = isBusy,
                        statusMessage = statusMessage,
                        onSubmitRegistration = { fullName, phone, city, photo, question, answer, terms ->
                            viewModel.submitUserRegistration(
                                fullName = fullName,
                                phoneNumber = phone,
                                city = city,
                                photoBase64 = photo,
                                securityQuestion = question,
                                securityAnswer = answer,
                                termsAccepted = terms
                            )
                        },
                        onSignOut = signOutAction
                    )
                }
                isDeviceBanned || !isApprovedMember -> {
                    userProfile?.let { profile ->
                        AccountStatusGateScreen(
                            profile = profile,
                            isDeviceBanned = isDeviceBanned,
                            onSignOut = signOutAction
                        )
                    }
                }
                else -> {
                    if (selectedTab != MainNavTab.STREAM) {
                        BackHandler {
                            selectedTab = MainNavTab.STREAM
                        }
                    }

                    // Super-smooth directional slide + fade transition between bottom navigation tabs
                    AnimatedContent(
                        targetState = selectedTab,
                        transitionSpec = {
                            val forward = targetState.ordinal >= initialState.ordinal
                            val slideIn = slideInHorizontally(
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioNoBouncy,
                                    stiffness = Spring.StiffnessMediumLow
                                ),
                                initialOffsetX = { fullWidth -> if (forward) fullWidth / 4 else -fullWidth / 4 }
                            ) + fadeIn(animationSpec = tween(220))
                            val slideOut = slideOutHorizontally(
                                animationSpec = tween(180),
                                targetOffsetX = { fullWidth -> if (forward) -fullWidth / 5 else fullWidth / 5 }
                            ) + fadeOut(animationSpec = tween(160))
                            slideIn togetherWith slideOut
                        },
                        label = "MainTabTransition"
                    ) { destination ->
                        when (destination) {
                            MainNavTab.STREAM -> {
                                BroadcastStreamScreen(
                                    messages = broadcastMessages,
                                    openedMessageIds = openedMessageIds,
                                    allMessageReadReceipts = allMessageReadReceipts,
                                    userProfile = userProfile,
                                    deviceRecord = deviceRecord,
                                    pricingConfig = pricingConfig,
                                    onlinePeopleCount = onlinePeopleCount,
                                    compactFeedMode = compactFeedMode,
                                    largeTextMode = largeTextMode,
                                    isAdmin = isAdmin,
                                    onMarkMessageOpened = { msg -> viewModel.markMessageAsOpened(msg) },
                                    onRequestFreeTrial = { viewModel.requestOneWeekFreeTrial() },
                                    onNavigateToMembership = { selectedTab = MainNavTab.MEMBERSHIP },
                                    onNavigateToReviews = { selectedTab = MainNavTab.COMMODITY },
                                    onReplyToMessageRequest = { msg ->
                                        replyingToMessage = msg
                                        editingMessage = null
                                        selectedTab = MainNavTab.ADMIN
                                    },
                                    onEditMessageRequest = { msg ->
                                        editingMessage = msg
                                        replyingToMessage = null
                                        selectedTab = MainNavTab.ADMIN
                                    },
                                    onDeleteMessageRequest = { msgId ->
                                        viewModel.deleteMessage(msgId)
                                    }
                                )
                            }
                            MainNavTab.COMMODITY -> {
                                CommunityReviewsScreen(
                                    latestTradeMessage = broadcastMessages.firstOrNull(),
                                    reviews = communityReviews,
                                    userProfile = userProfile,
                                    pricingConfig = pricingConfig,
                                    onlinePeopleCount = onlinePeopleCount,
                                    isAdmin = isAdmin,
                                    isBusy = isBusy,
                                    onSubmitReview = { msgId, msgTitle, risk, topic, opinion, rating ->
                                        viewModel.submitCommunityReview(
                                            tradeMessageId = msgId,
                                            tradeMessageTitle = msgTitle,
                                            tradeRiskLevel = risk,
                                            topicTag = topic,
                                            opinionText = opinion,
                                            rating = rating
                                        )
                                    },
                                    onDeleteReview = { viewModel.deleteCommunityReview(it) },
                                    onTogglePinReview = { reviewId, pinned ->
                                        viewModel.setCommodityMessagePinned(reviewId, pinned)
                                    },
                                    onToggleCommodityChatLock = { locked ->
                                        viewModel.setCommodityChatLocked(locked)
                                    },
                                    onMuteUserInCommodity = { userId, userName ->
                                        val target = allUsersForAdmin.find { it.userId == userId }
                                            ?: com.example.data.model.UserProfile(userId = userId, fullName = userName)
                                        viewModel.setUserCommodityMuted(target, true)
                                    },
                                    onNavigateToMembership = { selectedTab = MainNavTab.MEMBERSHIP }
                                )
                            }
                            MainNavTab.MEMBERSHIP -> {
                                MembershipScreen(
                                    userProfile = userProfile,
                                    deviceRecord = deviceRecord,
                                    pricingConfig = pricingConfig,
                                    myRequests = myAccessRequests,
                                    isBusy = isBusy,
                                    onRequestFreeTrial = { viewModel.requestOneWeekFreeTrial() },
                                    onSubmitUpiPayment = { planName, amount, days, utr, screenshot ->
                                        viewModel.submitUpiPayment(
                                            planName = planName,
                                            amountInr = amount,
                                            validityDays = days,
                                            utrReference = utr,
                                            paymentScreenshotBase64 = screenshot
                                        )
                                    }
                                )
                            }
                            MainNavTab.SETTINGS -> {
                                SettingsScreen(
                                    userProfile = userProfile,
                                    isAdmin = isAdmin,
                                    appleWebAppUrl = appleWebAppUrl,
                                    androidApkShareUrl = androidApkShareUrl,
                                    onUpdateShareUrls = { appleUrl, androidUrl ->
                                        viewModel.updateShareUrls(appleUrl, androidUrl)
                                    },
                                    themePreference = themePreference,
                                    isBiometricEnabled = isBiometricEnabled,
                                    notificationsEnabled = notificationsEnabled,
                                    offlineDataReminderEnabled = offlineDataReminderEnabled,
                                    offlineReminderIntervalMinutes = offlineReminderIntervalMinutes,
                                    cycleExpiryNotificationsEnabled = cycleExpiryNotificationsEnabled,
                                    highRiskVibrationEnabled = highRiskVibrationEnabled,
                                    compactFeedMode = compactFeedMode,
                                    largeTextMode = largeTextMode,
                                    securityQuestion = localSecurityQuestion,
                                    onChangeTheme = onChangeTheme,
                                    onToggleBiometric = { viewModel.setBiometricPreference(it) },
                                    onToggleNotifications = { viewModel.setNotificationsPreference(it) },
                                    onToggleOfflineDataReminder = { viewModel.setOfflineDataReminderPreference(it) },
                                    onChangeOfflineReminderInterval = { viewModel.setOfflineReminderIntervalPreference(it) },
                                    onToggleCycleExpiryNotifications = { viewModel.setCycleExpiryNotificationsPreference(it) },
                                    onToggleHighRiskVibration = { viewModel.setHighRiskVibrationPreference(it) },
                                    onToggleCompactFeedMode = { viewModel.setCompactFeedMode(it) },
                                    onToggleLargeTextMode = { viewModel.setLargeTextMode(it) },
                                    onUpdatePin = { cur, next -> viewModel.updateAppLockPin(cur, next) },
                                    onUpdateSecurityQuestion = { q, a -> viewModel.updateSecurityQuestionAndAnswer(q, a) },
                                    onRequestProfileEdit = { name, phone, city, photo ->
                                        viewModel.requestProfileEdit(name, phone, city, photo)
                                    },
                                    onLockNow = { viewModel.lockSessionNow() },
                                    onSignOut = signOutAction
                                )
                            }
                            MainNavTab.ADMIN -> {
                                AdminConsoleScreen(
                                    allUsers = allUsersForAdmin,
                                    allRequests = allAccessRequestsForAdmin,
                                    moderationAlerts = moderationAlertsForAdmin,
                                    pricingConfig = pricingConfig,
                                    adminSettings = adminSettings,
                                    appleWebAppUrl = appleWebAppUrl,
                                    androidApkShareUrl = androidApkShareUrl,
                                    onUpdateShareUrls = { appleUrl, androidUrl ->
                                        viewModel.updateShareUrls(appleUrl, androidUrl)
                                    },
                                    editingMessageInit = editingMessage,
                                    replyingToMessageInit = replyingToMessage,
                                    onClearEditingMessage = { editingMessage = null },
                                    onClearReplyingToMessage = { replyingToMessage = null },
                                    onPublishMessage = { title, content, cat, risk, img, url, lTitle, pinned, rId, rTitle, rSnippet, rRisk ->
                                        viewModel.publishMessage(
                                            title, content, cat, risk, img, url, lTitle, pinned, rId, rTitle, rSnippet, rRisk
                                        )
                                        replyingToMessage = null
                                    },
                                    onEditMessage = { id, title, content, cat, risk, img, url, lTitle, pinned, rId, rTitle, rSnippet, rRisk ->
                                        viewModel.editMessage(
                                            id, title, content, cat, risk, img, url, lTitle, pinned, rId, rTitle, rSnippet, rRisk
                                        )
                                        editingMessage = null
                                    },
                                    onApproveUserAccount = { uid, trial -> viewModel.approveUserAccount(uid, trial) },
                                    onReviewProfileEdit = { u, approve -> viewModel.reviewProfileEditRequest(u, approve) },
                                    onReviewAccessRequest = { req, approve -> viewModel.reviewAccessRequest(req, approve) },
                                    onResolveAlert = { id, status -> viewModel.resolveModerationAlert(id, status) },
                                    onBanUserFromAlert = { alert -> viewModel.banUserFromModerationAlert(alert) },
                                    onSetUserCommodityMuted = { u, muted -> viewModel.setUserCommodityMuted(u, muted) },
                                    onResetUserDailyQuota = { u -> viewModel.adminResetUserDailyCommodityQuota(u) },
                                    onResetUserPin = { u, pin -> viewModel.adminResetUserPin(u, pin) },
                                    onUpdateUserSubscriptionDays = { uid, plan, days ->
                                        viewModel.updateUserSubscriptionDays(uid, plan, days)
                                    },
                                    onSetDeviceBanStatus = { u, ban -> viewModel.setDeviceBanStatus(u, ban) },
                                    onSavePricingAndQrConfig = { p1Name, p1Price, p1Days, upi, mobile, p1Qr, p1Only, p2En, p2Name, p2Price, p2Days, p2Qr, p2Only, p3En, p3Name, p3Price, p3Days, p3Qr, p3Only, limit, locked, trialDays, benefits ->
                                        viewModel.savePricingAndQrConfig(
                                            p1Name, p1Price, p1Days, upi, mobile, p1Qr, p1Only,
                                            p2En, p2Name, p2Price, p2Days, p2Qr, p2Only,
                                            p3En, p3Name, p3Price, p3Days, p3Qr, p3Only,
                                            limit, locked, trialDays, benefits
                                        )
                                    },
                                    onAddCoAdminEmail = { viewModel.addCoAdminEmail(it) },
                                    onRemoveCoAdminEmail = { viewModel.removeCoAdminEmail(it) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showQuickQrShareDialog) {
        AlertDialog(
            onDismissRequest = { showQuickQrShareDialog = false },
            title = { Text("Share App & Permanent Website QR") },
            text = {
                PermanentQrAndShareCard(
                    appleWebAppUrl = appleWebAppUrl,
                    androidApkUrl = androidApkShareUrl,
                    isAdmin = isAdmin,
                    onUpdateUrls = { appleUrl, androidUrl ->
                        viewModel.updateShareUrls(appleUrl, androidUrl)
                    }
                )
            },
            confirmButton = {
                Button(onClick = { showQuickQrShareDialog = false }) {
                    Text("Done")
                }
            }
        )
    }
}
