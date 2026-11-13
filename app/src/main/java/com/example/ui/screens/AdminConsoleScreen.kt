package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.data.model.AccessRequest
import com.example.data.model.AdminSettings
import com.example.data.model.BroadcastMessage
import com.example.data.model.ModerationAlert
import com.example.data.model.PricingConfig
import com.example.data.model.UserProfile
import com.example.ui.components.ImageCodecHelper
import com.example.ui.components.PermanentQrAndShareCard
import com.example.ui.components.PhotoMarkingDialog

private val EmeraldColor = Color(0xFF10B981)
private val AmberColor = Color(0xFFF59E0B)
private val CrimsonColor = Color(0xFFEF4444)
private val ElectricBlueColor = Color(0xFF3B82F6)

@Composable
fun AdminConsoleScreen(
    allUsers: List<UserProfile>,
    allRequests: List<AccessRequest>,
    moderationAlerts: List<ModerationAlert>,
    pricingConfig: PricingConfig,
    adminSettings: AdminSettings,
    appleWebAppUrl: String,
    androidApkShareUrl: String,
    onUpdateShareUrls: (appleUrl: String, androidUrl: String) -> Unit,
    editingMessageInit: BroadcastMessage?,
    replyingToMessageInit: BroadcastMessage?,
    onClearEditingMessage: () -> Unit,
    onClearReplyingToMessage: () -> Unit,
    onPublishMessage: (
        title: String,
        content: String,
        category: String,
        riskLevel: String,
        markedPhotoBase64: String,
        linkUrl: String,
        linkTitle: String,
        isPinned: Boolean,
        replyToMessageId: String,
        replyToTitle: String,
        replyToSnippet: String,
        replyToRiskLevel: String
    ) -> Unit,
    onEditMessage: (
        messageId: String,
        title: String,
        content: String,
        category: String,
        riskLevel: String,
        markedPhotoBase64: String,
        linkUrl: String,
        linkTitle: String,
        isPinned: Boolean,
        replyToMessageId: String,
        replyToTitle: String,
        replyToSnippet: String,
        replyToRiskLevel: String
    ) -> Unit,
    onApproveUserAccount: (userId: String, grantSevenDayTrial: Boolean) -> Unit,
    onReviewProfileEdit: (user: UserProfile, approve: Boolean) -> Unit,
    onReviewAccessRequest: (request: AccessRequest, approve: Boolean) -> Unit,
    onResolveAlert: (alertId: String, status: String) -> Unit,
    onBanUserFromAlert: (ModerationAlert) -> Unit,
    onSetUserCommodityMuted: (user: UserProfile, muted: Boolean) -> Unit,
    onResetUserDailyQuota: (UserProfile) -> Unit,
    onResetUserPin: (user: UserProfile, newPin: String) -> Unit,
    onUpdateUserSubscriptionDays: (userId: String, planName: String, daysLeft: Int) -> Unit,
    onSetDeviceBanStatus: (user: UserProfile, ban: Boolean) -> Unit,
    onSavePricingAndQrConfig: (
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
    ) -> Unit,
    onAddCoAdminEmail: (String) -> Unit,
    onRemoveCoAdminEmail: (String) -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) }

    LaunchedEffect(editingMessageInit, replyingToMessageInit) {
        if (editingMessageInit != null || replyingToMessageInit != null) {
            selectedTab = 0
        }
    }

    val pendingRegistrations = remember(allUsers) {
        allUsers.filter { it.accountStatus == "PENDING_APPROVAL" }
    }
    val pendingProfileEdits = remember(allUsers) {
        allUsers.filter { it.hasPendingProfileEdit }
    }
    val pendingRequests = remember(allRequests) {
        allRequests.filter { it.status == "PENDING" }
    }
    val unresolvedAlerts = remember(moderationAlerts) {
        moderationAlerts.filter { it.status == "OPEN" }
    }
    val totalApprovalsBadge = pendingRegistrations.size + pendingProfileEdits.size + pendingRequests.size

    Column(modifier = Modifier.fillMaxSize()) {
        ScrollableTabRow(
            selectedTabIndex = selectedTab,
            edgePadding = 12.dp,
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = { Text("Publish Message") },
                icon = { Icon(Icons.Default.Campaign, contentDescription = null) },
                modifier = Modifier.testTag("admin_tab_publish")
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = {
                    Text(
                        if (totalApprovalsBadge > 0) "Approvals ($totalApprovalsBadge)" else "Approvals"
                    )
                },
                icon = { Icon(Icons.Default.FactCheck, contentDescription = null) },
                modifier = Modifier.testTag("admin_tab_approvals")
            )
            Tab(
                selected = selectedTab == 2,
                onClick = { selectedTab = 2 },
                text = {
                    Text(
                        if (unresolvedAlerts.isNotEmpty()) "Alerts (${unresolvedAlerts.size})" else "Alerts"
                    )
                },
                icon = { Icon(Icons.Default.Shield, contentDescription = null) },
                modifier = Modifier.testTag("admin_tab_alerts")
            )
            Tab(
                selected = selectedTab == 3,
                onClick = { selectedTab = 3 },
                text = { Text("Users (${allUsers.size})") },
                icon = { Icon(Icons.Default.People, contentDescription = null) },
                modifier = Modifier.testTag("admin_tab_users")
            )
            Tab(
                selected = selectedTab == 4,
                onClick = { selectedTab = 4 },
                text = { Text("Plans & Controls") },
                icon = { Icon(Icons.Default.Tune, contentDescription = null) },
                modifier = Modifier.testTag("admin_tab_plans")
            )
        }

        when (selectedTab) {
            0 -> AdminPublishMessageTab(
                editingMessageInit = editingMessageInit,
                replyingToMessageInit = replyingToMessageInit,
                onClearEditingMessage = onClearEditingMessage,
                onClearReplyingToMessage = onClearReplyingToMessage,
                onPublishMessage = onPublishMessage,
                onEditMessage = onEditMessage
            )
            1 -> AdminApprovalsTab(
                pendingRegistrations = pendingRegistrations,
                pendingProfileEdits = pendingProfileEdits,
                pendingRequests = pendingRequests,
                onApproveUserAccount = onApproveUserAccount,
                onReviewProfileEdit = onReviewProfileEdit,
                onReviewAccessRequest = onReviewAccessRequest
            )
            2 -> AdminModerationAlertsTab(
                alerts = moderationAlerts,
                allUsers = allUsers,
                onResolveAlert = onResolveAlert,
                onMuteUser = onSetUserCommodityMuted,
                onBanUserFromAlert = onBanUserFromAlert
            )
            3 -> AdminUsersInspectorTab(
                allUsers = allUsers,
                onSetUserCommodityMuted = onSetUserCommodityMuted,
                onResetUserDailyQuota = onResetUserDailyQuota,
                onResetUserPin = onResetUserPin,
                onUpdateUserSubscriptionDays = onUpdateUserSubscriptionDays,
                onSetDeviceBanStatus = onSetDeviceBanStatus
            )
            4 -> AdminPlansAndConfigTab(
                pricingConfig = pricingConfig,
                adminSettings = adminSettings,
                appleWebAppUrl = appleWebAppUrl,
                androidApkShareUrl = androidApkShareUrl,
                onUpdateShareUrls = onUpdateShareUrls,
                onSavePricingAndQrConfig = onSavePricingAndQrConfig,
                onAddCoAdminEmail = onAddCoAdminEmail,
                onRemoveCoAdminEmail = onRemoveCoAdminEmail
            )
        }
    }
}

@Composable
private fun AdminPublishMessageTab(
    editingMessageInit: BroadcastMessage?,
    replyingToMessageInit: BroadcastMessage?,
    onClearEditingMessage: () -> Unit,
    onClearReplyingToMessage: () -> Unit,
    onPublishMessage: (
        title: String,
        content: String,
        category: String,
        riskLevel: String,
        markedPhotoBase64: String,
        linkUrl: String,
        linkTitle: String,
        isPinned: Boolean,
        replyToMessageId: String,
        replyToTitle: String,
        replyToSnippet: String,
        replyToRiskLevel: String
    ) -> Unit,
    onEditMessage: (
        messageId: String,
        title: String,
        content: String,
        category: String,
        riskLevel: String,
        markedPhotoBase64: String,
        linkUrl: String,
        linkTitle: String,
        isPinned: Boolean,
        replyToMessageId: String,
        replyToTitle: String,
        replyToSnippet: String,
        replyToRiskLevel: String
    ) -> Unit
) {
    val context = LocalContext.current
    var title by remember(editingMessageInit) { mutableStateOf(editingMessageInit?.title ?: "") }
    var content by remember(editingMessageInit) { mutableStateOf(editingMessageInit?.content ?: "") }
    var category by remember(editingMessageInit) { mutableStateOf(editingMessageInit?.category ?: "INTRADAY") }
    var riskLevel by remember(editingMessageInit) { mutableStateOf(editingMessageInit?.riskLevel ?: "MEDIUM") }
    var markedPhotoBase64 by remember(editingMessageInit) {
        mutableStateOf(editingMessageInit?.markedPhotoBase64 ?: "")
    }
    var linkUrl by remember(editingMessageInit) { mutableStateOf(editingMessageInit?.linkUrl ?: "") }
    var linkTitle by remember(editingMessageInit) { mutableStateOf(editingMessageInit?.linkTitle ?: "") }
    var isPinned by remember(editingMessageInit) { mutableStateOf(editingMessageInit?.isPinned ?: false) }
    var showMarkupDialog by remember { mutableStateOf(false) }

    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            val encoded = ImageCodecHelper.encodeUriToBase64Jpeg(
                context = context,
                uri = uri,
                maxDimension = 900,
                quality = 75
            )
            if (encoded != null) {
                markedPhotoBase64 = encoded
            }
        }
    }

    val chartBitmap = remember(markedPhotoBase64) {
        ImageCodecHelper.decodeBase64ToBitmap(markedPhotoBase64)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (editingMessageInit != null) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = ElectricBlueColor.copy(alpha = 0.14f),
                border = BorderStroke(1.dp, ElectricBlueColor.copy(alpha = 0.45f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Editing Message: ${editingMessageInit.title}",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                    TextButton(onClick = onClearEditingMessage) {
                        Text("Cancel Edit")
                    }
                }
            }
        }

        // WhatsApp-style Quoted Reply Banner
        if (replyingToMessageInit != null) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = EmeraldColor.copy(alpha = 0.14f),
                border = BorderStroke(1.dp, EmeraldColor.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Reply,
                        contentDescription = "Replying To",
                        tint = EmeraldColor
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Replying to: ${replyingToMessageInit.title}",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = EmeraldColor
                        )
                        Text(
                            text = replyingToMessageInit.content.take(90),
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 2
                        )
                    }
                    IconButton(onClick = onClearReplyingToMessage) {
                        Icon(Icons.Default.Close, contentDescription = "Cancel Reply")
                    }
                }
            }
        }

        OutlinedTextField(
            value = title,
            onValueChange = { title = it },
            label = { Text("Broadcast Headline (e.g., GOLD MCX / NIFTY UPDATE)") },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("admin_message_title_input")
        )

        OutlinedTextField(
            value = content,
            onValueChange = { content = it },
            label = { Text("Full Message Content (Hidden until member clicks to open)") },
            minLines = 4,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("admin_message_body_input")
        )

        Text("Category", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("INTRADAY", "SWING", "OPTIONS", "MCX").forEach { cat ->
                FilterChip(
                    selected = category == cat,
                    onClick = { category = cat },
                    label = { Text(cat) }
                )
            }
        }

        Text("Broadcast Risk Level", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("LOW", "MEDIUM", "HIGH").forEach { risk ->
                FilterChip(
                    selected = riskLevel == risk,
                    onClick = { riskLevel = risk },
                    label = { Text(risk) }
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Pin Message to Top of Stream", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            Switch(checked = isPinned, onCheckedChange = { isPinned = it })
        }

        if (chartBitmap != null) {
            Image(
                bitmap = chartBitmap.asImageBitmap(),
                contentDescription = "Attached Chart",
                modifier = Modifier
                    .fillMaxWidth()
                    .height(190.dp)
                    .clip(RoundedCornerShape(12.dp)),
                contentScale = ContentScale.Crop
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { showMarkupDialog = true },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Brush, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Annotate / Draw on Chart")
                }
                OutlinedButton(
                    onClick = { markedPhotoBase64 = "" },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Remove Photo")
                }
            }
        } else {
            OutlinedButton(
                onClick = {
                    imagePicker.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.AddPhotoAlternate, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Attach Chart / Market Photo")
            }
        }

        Button(
            onClick = {
                if (title.isBlank() || content.isBlank()) return@Button
                if (editingMessageInit != null) {
                    onEditMessage(
                        editingMessageInit.id,
                        title.trim(),
                        content.trim(),
                        category,
                        riskLevel,
                        markedPhotoBase64,
                        linkUrl.trim(),
                        linkTitle.trim(),
                        isPinned,
                        editingMessageInit.replyToMessageId,
                        editingMessageInit.replyToTitle,
                        editingMessageInit.replyToSnippet,
                        editingMessageInit.replyToRiskLevel
                    )
                } else {
                    onPublishMessage(
                        title.trim(),
                        content.trim(),
                        category,
                        riskLevel,
                        markedPhotoBase64,
                        linkUrl.trim(),
                        linkTitle.trim(),
                        isPinned,
                        replyingToMessageInit?.id ?: "",
                        replyingToMessageInit?.title ?: "",
                        replyingToMessageInit?.content?.take(100) ?: "",
                        replyingToMessageInit?.riskLevel ?: ""
                    )
                }
                title = ""
                content = ""
                markedPhotoBase64 = ""
                linkUrl = ""
                linkTitle = ""
                isPinned = false
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .testTag("admin_publish_submit_button")
        ) {
            Icon(Icons.Default.Send, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = if (editingMessageInit != null) "Save Updated Broadcast" else "Publish Masked Broadcast Alert",
                fontWeight = FontWeight.Bold
            )
        }
    }

    if (showMarkupDialog && chartBitmap != null) {
        PhotoMarkingDialog(
            sourceBitmap = chartBitmap,
            onDismiss = { showMarkupDialog = false },
            onSaveMarkedPhoto = { annotated ->
                markedPhotoBase64 = annotated
                showMarkupDialog = false
            }
        )
    }
}

@Composable
private fun AdminApprovalsTab(
    pendingRegistrations: List<UserProfile>,
    pendingProfileEdits: List<UserProfile>,
    pendingRequests: List<AccessRequest>,
    onApproveUserAccount: (userId: String, grantSevenDayTrial: Boolean) -> Unit,
    onReviewProfileEdit: (user: UserProfile, approve: Boolean) -> Unit,
    onReviewAccessRequest: (request: AccessRequest, approve: Boolean) -> Unit
) {
    var inspectedScreenshot by remember { mutableStateOf<String?>(null) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Section A: New Member Registrations
        item {
            Text(
                text = "New Member Registration Queue (${pendingRegistrations.size})",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }
        if (pendingRegistrations.isEmpty()) {
            item {
                Text(
                    text = "No new member registrations awaiting approval.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            items(pendingRegistrations, key = { "reg_${it.userId}" }) { user ->
                val userPhotoBmp = remember(user.photoBase64) {
                    ImageCodecHelper.decodeBase64ToBitmap(user.photoBase64)
                }
                Card(
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (userPhotoBmp != null) {
                                Image(
                                    bitmap = userPhotoBmp.asImageBitmap(),
                                    contentDescription = "User Photo",
                                    modifier = Modifier
                                        .size(46.dp)
                                        .clip(CircleShape),
                                    contentScale = ContentScale.Crop
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(user.fullName.ifBlank { user.email }, fontWeight = FontWeight.Bold)
                                Text("${user.email} • ${user.phoneNumber}", style = MaterialTheme.typography.bodySmall)
                                Text("City: ${user.city} • IP: ${user.ipAddress}", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = { onApproveUserAccount(user.userId, false) },
                                colors = ButtonDefaults.buttonColors(containerColor = EmeraldColor, contentColor = Color.Black),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Approve", fontWeight = FontWeight.Bold)
                            }
                            OutlinedButton(
                                onClick = { onApproveUserAccount(user.userId, true) },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Approve + 7d Trial")
                            }
                        }
                    }
                }
            }
        }

        // Section B: Profile Edit Requests
        item {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Profile Name & Photo Edit Requests (${pendingProfileEdits.size})",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }
        if (pendingProfileEdits.isEmpty()) {
            item {
                Text(
                    text = "No pending profile or photo change requests.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            items(pendingProfileEdits, key = { "edit_${it.userId}" }) { user ->
                val pendingBmp = remember(user.pendingPhotoBase64) {
                    ImageCodecHelper.decodeBase64ToBitmap(user.pendingPhotoBase64)
                }
                Card(
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = "Current: ${user.fullName} (${user.phoneNumber})",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Requested: ${user.pendingFullName} • ${user.pendingPhoneNumber} • ${user.pendingCity}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold
                        )
                        if (pendingBmp != null) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Image(
                                bitmap = pendingBmp.asImageBitmap(),
                                contentDescription = "Requested Photo",
                                modifier = Modifier
                                    .size(64.dp)
                                    .clip(CircleShape),
                                contentScale = ContentScale.Crop
                            )
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = { onReviewProfileEdit(user, true) },
                                colors = ButtonDefaults.buttonColors(containerColor = EmeraldColor, contentColor = Color.Black),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Approve Edit", fontWeight = FontWeight.Bold)
                            }
                            OutlinedButton(
                                onClick = { onReviewProfileEdit(user, false) },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Reject Edit")
                            }
                        }
                    }
                }
            }
        }

        // Section C: Trial & UPI QR Payment Requests
        item {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Subscription & 7-Day Trial Requests (${pendingRequests.size})",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }
        if (pendingRequests.isEmpty()) {
            item {
                Text(
                    text = "No pending subscription or trial requests.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            items(pendingRequests, key = { "sub_${it.id}" }) { req ->
                val proofBmp = remember(req.paymentScreenshotBase64) {
                    ImageCodecHelper.decodeBase64ToBitmap(req.paymentScreenshotBase64)
                }
                Card(
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = "${req.userName} (${req.userEmail})",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Plan: ${req.planName} • ₹${req.amountInr.toInt()} • ${req.validityDays} Days",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "UTR / Ref: ${req.utrReference}",
                            style = MaterialTheme.typography.bodySmall
                        )
                        if (proofBmp != null) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Image(
                                bitmap = proofBmp.asImageBitmap(),
                                contentDescription = "Payment Proof",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(150.dp)
                                    .clip(RoundedCornerShape(10.dp)),
                                contentScale = ContentScale.Crop
                            )
                            TextButton(onClick = { inspectedScreenshot = req.paymentScreenshotBase64 }) {
                                Icon(Icons.Default.ZoomIn, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Inspect Full Payment Screenshot")
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = { onReviewAccessRequest(req, true) },
                                colors = ButtonDefaults.buttonColors(containerColor = EmeraldColor, contentColor = Color.Black),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Approve (${req.validityDays}d)", fontWeight = FontWeight.Bold)
                            }
                            OutlinedButton(
                                onClick = { onReviewAccessRequest(req, false) },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Reject")
                            }
                        }
                    }
                }
            }
        }
    }

    inspectedScreenshot?.let { screenshot ->
        val fullBmp = remember(screenshot) { ImageCodecHelper.decodeBase64ToBitmap(screenshot) }
        AlertDialog(
            onDismissRequest = { inspectedScreenshot = null },
            title = { Text("UPI Payment Proof Inspector") },
            text = {
                if (fullBmp != null) {
                    Image(
                        bitmap = fullBmp.asImageBitmap(),
                        contentDescription = "Full Payment Screenshot",
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(380.dp)
                            .clip(RoundedCornerShape(12.dp)),
                        contentScale = ContentScale.Fit
                    )
                }
            },
            confirmButton = {
                Button(onClick = { inspectedScreenshot = null }) {
                    Text("Close")
                }
            }
        )
    }
}

@Composable
private fun AdminModerationAlertsTab(
    alerts: List<ModerationAlert>,
    allUsers: List<UserProfile>,
    onResolveAlert: (alertId: String, status: String) -> Unit,
    onMuteUser: (user: UserProfile, muted: Boolean) -> Unit,
    onBanUserFromAlert: (ModerationAlert) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                text = "Blocked Phone/ID & Moderation Alerts (${alerts.size})",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Includes automatic blocks when a user tries to share a phone number, email, UPI ID, or social handle in Commodity.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        items(alerts, key = { it.id }) { alert ->
            val isResolved = alert.status != "OPEN"
            val matchedUser = allUsers.find { it.userId == alert.userId }
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isResolved) {
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    } else {
                        CrimsonColor.copy(alpha = 0.12f)
                    }
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "${alert.userName} (${alert.userEmail})",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Attempted Content: \"${alert.originalUneditedText}\"",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "Triggered Pattern: ${alert.detectedWords} • Device: ${alert.deviceId}",
                        style = MaterialTheme.typography.labelSmall,
                        color = CrimsonColor
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (matchedUser != null) {
                            OutlinedButton(
                                onClick = { onMuteUser(matchedUser, true) },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Block Msg")
                            }
                        }
                        if (!isResolved) {
                            Button(
                                onClick = { onResolveAlert(alert.id, "RESOLVED") },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Resolve")
                            }
                        }
                        Button(
                            onClick = { onBanUserFromAlert(alert) },
                            colors = ButtonDefaults.buttonColors(containerColor = CrimsonColor),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Ban Device")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AdminUsersInspectorTab(
    allUsers: List<UserProfile>,
    onSetUserCommodityMuted: (user: UserProfile, muted: Boolean) -> Unit,
    onResetUserDailyQuota: (UserProfile) -> Unit,
    onResetUserPin: (user: UserProfile, newPin: String) -> Unit,
    onUpdateUserSubscriptionDays: (userId: String, planName: String, daysLeft: Int) -> Unit,
    onSetDeviceBanStatus: (user: UserProfile, ban: Boolean) -> Unit
) {
    var inspectedUser by remember { mutableStateOf<UserProfile?>(null) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Text(
                text = "Member Directory & Security Credentials (${allUsers.size})",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Tap any member to inspect their App Lock PIN, Security Question & Answer, Device ID, IP, or manage their Commodity permissions.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        items(allUsers, key = { it.userId.ifBlank { it.id } }) { user ->
            val avatarBmp = remember(user.photoBase64) {
                ImageCodecHelper.decodeBase64ToBitmap(user.photoBase64)
            }
            Card(
                onClick = { inspectedUser = user },
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("admin_user_row_${user.userId}")
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (avatarBmp != null) {
                        Image(
                            bitmap = avatarBmp.asImageBitmap(),
                            contentDescription = "User Avatar",
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
                            modifier = Modifier.size(48.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.Person, contentDescription = null)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = user.fullName.ifBlank { user.email },
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${user.email} • PIN: ${user.appLockPin.ifBlank { "Not Set" }}",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            text = "Q: ${user.securityQuestion.ifBlank { "N/A" }} | A: ${user.securityAnswer.ifBlank { "N/A" }}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Icon(Icons.Default.ChevronRight, contentDescription = "Inspect")
                }
            }
        }
    }

    inspectedUser?.let { user ->
        var daysInput by remember(user) { mutableStateOf(user.daysRemaining().toString()) }
        var pinInput by remember(user) { mutableStateOf(user.appLockPin.ifBlank { "1234" }) }
        val isBanned = user.accountStatus == "BANNED"

        AlertDialog(
            onDismissRequest = { inspectedUser = null },
            title = { Text(user.fullName.ifBlank { user.email }) },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("Email: ${user.email}", style = MaterialTheme.typography.bodySmall)
                    Text("Phone: ${user.phoneNumber} • City: ${user.city}", style = MaterialTheme.typography.bodySmall)
                    Text("Device ID: ${user.deviceId} (${user.deviceModel})", style = MaterialTheme.typography.labelSmall)
                    Text("IP Address: ${user.ipAddress}", style = MaterialTheme.typography.labelSmall)

                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    Text("Security Credentials (Admin View)", fontWeight = FontWeight.Bold)
                    Text(
                        text = "App Lock PIN / Password: ${user.appLockPin.ifBlank { "Not Set" }}",
                        color = EmeraldColor,
                        fontWeight = FontWeight.Bold
                    )
                    Text("Security Question: ${user.securityQuestion.ifBlank { "Not Set" }}")
                    Text(
                        text = "Security Answer: ${user.securityAnswer.ifBlank { "Not Set" }}",
                        color = AmberColor,
                        fontWeight = FontWeight.Bold
                    )

                    OutlinedTextField(
                        value = pinInput,
                        onValueChange = { if (it.length <= 8 && it.all(Char::isDigit)) pinInput = it },
                        label = { Text("Reset User App PIN") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                    Button(
                        onClick = { onResetUserPin(user, pinInput) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Force Update User PIN")
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    Text("Commodity & Subscription Controls", fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { onSetUserCommodityMuted(user, !user.isCommodityMuted) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(if (user.isCommodityMuted) "Unmute Chat" else "Block Msg")
                        }
                        OutlinedButton(
                            onClick = { onResetUserDailyQuota(user) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Reset Quota")
                        }
                    }

                    OutlinedTextField(
                        value = daysInput,
                        onValueChange = { daysInput = it.filter(Char::isDigit) },
                        label = { Text("Subscription Days Remaining") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                    Button(
                        onClick = {
                            val days = daysInput.toIntOrNull() ?: 0
                            onUpdateUserSubscriptionDays(
                                user.userId,
                                user.activePlanName.ifBlank { "Admin Custom Plan" },
                                days
                            )
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Update Days Remaining")
                    }

                    if (isBanned) {
                        Button(
                            onClick = {
                                onSetDeviceBanStatus(user, false)
                                inspectedUser = null
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = EmeraldColor, contentColor = Color.Black),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Unban Device & Account")
                        }
                    } else {
                        Button(
                            onClick = {
                                onSetDeviceBanStatus(user, true)
                                inspectedUser = null
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = CrimsonColor),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Suspend Account & Ban Device")
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { inspectedUser = null }) {
                    Text("Close")
                }
            }
        )
    }
}

@Composable
private fun AdminPlansAndConfigTab(
    pricingConfig: PricingConfig,
    adminSettings: AdminSettings,
    appleWebAppUrl: String,
    androidApkShareUrl: String,
    onUpdateShareUrls: (appleUrl: String, androidUrl: String) -> Unit,
    onSavePricingAndQrConfig: (
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
    ) -> Unit,
    onAddCoAdminEmail: (String) -> Unit,
    onRemoveCoAdminEmail: (String) -> Unit
) {
    val context = LocalContext.current

    var plan1Name by remember(pricingConfig) { mutableStateOf(pricingConfig.planName) }
    var plan1Price by remember(pricingConfig) { mutableStateOf(pricingConfig.priceInr.toInt().toString()) }
    var plan1Days by remember(pricingConfig) { mutableStateOf(pricingConfig.validityDays.toString()) }
    var plan1Qr by remember(pricingConfig) { mutableStateOf(pricingConfig.qrCodeBase64) }

    var plan2Name by remember(pricingConfig) { mutableStateOf(pricingConfig.plan2Name) }
    var plan2Price by remember(pricingConfig) { mutableStateOf(pricingConfig.plan2PriceInr.toInt().toString()) }
    var plan2Days by remember(pricingConfig) { mutableStateOf(pricingConfig.plan2ValidityDays.toString()) }
    var plan2Qr by remember(pricingConfig) { mutableStateOf(pricingConfig.plan2QrCodeBase64) }

    var plan3Name by remember(pricingConfig) { mutableStateOf(pricingConfig.plan3Name) }
    var plan3Price by remember(pricingConfig) { mutableStateOf(pricingConfig.plan3PriceInr.toInt().toString()) }
    var plan3Days by remember(pricingConfig) { mutableStateOf(pricingConfig.plan3ValidityDays.toString()) }
    var plan3Qr by remember(pricingConfig) { mutableStateOf(pricingConfig.plan3QrCodeBase64) }

    var upiId by remember(pricingConfig) { mutableStateOf(pricingConfig.upiId) }
    var upiMobile by remember(pricingConfig) { mutableStateOf(pricingConfig.upiMobileNumber) }
    var dailyLimit by remember(pricingConfig) { mutableStateOf(pricingConfig.dailyReviewLimitPerUser.toString()) }
    var isChatLocked by remember(pricingConfig) { mutableStateOf(pricingConfig.commodityChatLocked) }
    var newCoAdminEmail by remember { mutableStateOf("") }
    var activeQrSlot by remember { mutableIntStateOf(1) }

    val qrPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            val encoded = ImageCodecHelper.encodeUriToBase64Jpeg(
                context = context,
                uri = uri,
                maxDimension = 700,
                quality = 75
            )
            if (encoded != null) {
                when (activeQrSlot) {
                    1 -> plan1Qr = encoded
                    2 -> plan2Qr = encoded
                    3 -> plan3Qr = encoded
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Admin Permanent Website QR Code & Apple / Android Share Hub
        PermanentQrAndShareCard(
            appleWebAppUrl = appleWebAppUrl,
            androidApkUrl = androidApkShareUrl,
            isAdmin = true,
            onUpdateUrls = onUpdateShareUrls
        )

        Text(
            text = "Commodity Controls & Multi-Plan QR Settings",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Pause All Member Posts in Commodity", fontWeight = FontWeight.SemiBold)
            Switch(checked = isChatLocked, onCheckedChange = { isChatLocked = it })
        }

        OutlinedTextField(
            value = dailyLimit,
            onValueChange = { dailyLimit = it.filter(Char::isDigit) },
            label = { Text("Daily Commodity Message Limit per Member") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = upiId,
            onValueChange = { upiId = it },
            label = { Text("Official UPI ID") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        // Plan 1
        Card(shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Plan #1 (Monthly)", fontWeight = FontWeight.Bold)
                OutlinedTextField(value = plan1Name, onValueChange = { plan1Name = it }, label = { Text("Plan Title") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = plan1Price, onValueChange = { plan1Price = it.filter(Char::isDigit) }, label = { Text("Price (INR)") }, singleLine = true, modifier = Modifier.weight(1f))
                    OutlinedTextField(value = plan1Days, onValueChange = { plan1Days = it.filter(Char::isDigit) }, label = { Text("Validity (Days)") }, singleLine = true, modifier = Modifier.weight(1f))
                }
                OutlinedButton(
                    onClick = {
                        activeQrSlot = 1
                        qrPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (plan1Qr.isBlank()) "Upload Plan #1 QR Image" else "Replace Plan #1 QR Image")
                }
            }
        }

        // Plan 2
        Card(shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Plan #2 (Quarterly)", fontWeight = FontWeight.Bold)
                OutlinedTextField(value = plan2Name, onValueChange = { plan2Name = it }, label = { Text("Plan Title") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = plan2Price, onValueChange = { plan2Price = it.filter(Char::isDigit) }, label = { Text("Price (INR)") }, singleLine = true, modifier = Modifier.weight(1f))
                    OutlinedTextField(value = plan2Days, onValueChange = { plan2Days = it.filter(Char::isDigit) }, label = { Text("Validity (Days)") }, singleLine = true, modifier = Modifier.weight(1f))
                }
                OutlinedButton(
                    onClick = {
                        activeQrSlot = 2
                        qrPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (plan2Qr.isBlank()) "Upload Plan #2 QR Image" else "Replace Plan #2 QR Image")
                }
            }
        }

        // Plan 3
        Card(shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Plan #3 (Half-Yearly)", fontWeight = FontWeight.Bold)
                OutlinedTextField(value = plan3Name, onValueChange = { plan3Name = it }, label = { Text("Plan Title") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = plan3Price, onValueChange = { plan3Price = it.filter(Char::isDigit) }, label = { Text("Price (INR)") }, singleLine = true, modifier = Modifier.weight(1f))
                    OutlinedTextField(value = plan3Days, onValueChange = { plan3Days = it.filter(Char::isDigit) }, label = { Text("Validity (Days)") }, singleLine = true, modifier = Modifier.weight(1f))
                }
                OutlinedButton(
                    onClick = {
                        activeQrSlot = 3
                        qrPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (plan3Qr.isBlank()) "Upload Plan #3 QR Image" else "Replace Plan #3 QR Image")
                }
            }
        }

        // Co-Admins Management
        Card(shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Authorized Admin Emails (Max 3)", fontWeight = FontWeight.Bold)
                adminSettings.adminEmails.forEach { email ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(email, style = MaterialTheme.typography.bodySmall)
                        TextButton(onClick = { onRemoveCoAdminEmail(email) }) {
                            Text("Remove")
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = newCoAdminEmail,
                        onValueChange = { newCoAdminEmail = it },
                        label = { Text("Add Co-Admin Google Email") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    Button(
                        onClick = {
                            if (newCoAdminEmail.isNotBlank()) {
                                onAddCoAdminEmail(newCoAdminEmail.trim())
                                newCoAdminEmail = ""
                            }
                        }
                    ) {
                        Text("Add")
                    }
                }
            }
        }

        Button(
            onClick = {
                onSavePricingAndQrConfig(
                    plan1Name,
                    plan1Price.toDoubleOrNull() ?: 2499.0,
                    plan1Days.toLongOrNull() ?: 30L,
                    upiId,
                    upiMobile,
                    plan1Qr,
                    pricingConfig.qrOnlyMode,
                    true,
                    plan2Name,
                    plan2Price.toDoubleOrNull() ?: 5999.0,
                    plan2Days.toLongOrNull() ?: 90L,
                    plan2Qr,
                    pricingConfig.plan2QrOnlyMode,
                    true,
                    plan3Name,
                    plan3Price.toDoubleOrNull() ?: 9999.0,
                    plan3Days.toLongOrNull() ?: 180L,
                    plan3Qr,
                    pricingConfig.plan3QrOnlyMode,
                    dailyLimit.toLongOrNull() ?: 10L,
                    isChatLocked,
                    pricingConfig.trialDays,
                    pricingConfig.benefits
                )
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .testTag("save_pricing_config_button")
        ) {
            Icon(Icons.Default.Save, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Save All Plans & Platform Settings", fontWeight = FontWeight.Bold)
        }
    }
}
