package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.data.model.AppThemePreference
import com.example.data.model.DEFAULT_TERMS_AND_CONDITIONS
import com.example.data.model.UserProfile
import com.example.ui.components.ImageCodecHelper
import com.example.ui.components.PermanentQrAndShareCard

@Composable
fun SettingsScreen(
    userProfile: UserProfile?,
    isAdmin: Boolean,
    appleWebAppUrl: String,
    androidApkShareUrl: String,
    onUpdateShareUrls: (appleUrl: String, androidUrl: String) -> Unit,
    themePreference: AppThemePreference,
    isBiometricEnabled: Boolean,
    notificationsEnabled: Boolean,
    offlineDataReminderEnabled: Boolean,
    offlineReminderIntervalMinutes: Int,
    cycleExpiryNotificationsEnabled: Boolean,
    highRiskVibrationEnabled: Boolean,
    compactFeedMode: Boolean,
    largeTextMode: Boolean,
    securityQuestion: String,
    onChangeTheme: (AppThemePreference) -> Unit,
    onToggleBiometric: (Boolean) -> Unit,
    onToggleNotifications: (Boolean) -> Unit,
    onToggleOfflineDataReminder: (Boolean) -> Unit,
    onChangeOfflineReminderInterval: (Int) -> Unit,
    onToggleCycleExpiryNotifications: (Boolean) -> Unit,
    onToggleHighRiskVibration: (Boolean) -> Unit,
    onToggleCompactFeedMode: (Boolean) -> Unit,
    onToggleLargeTextMode: (Boolean) -> Unit,
    onUpdatePin: (currentPin: String, newPin: String) -> Boolean,
    onUpdateSecurityQuestion: (question: String, answer: String) -> Unit,
    onRequestProfileEdit: (fullName: String, phone: String, city: String, photoBase64: String) -> Unit,
    onLockNow: () -> Unit,
    onSignOut: () -> Unit
) {
    val context = LocalContext.current

    // Profile Edit Request State
    var editName by remember(userProfile?.fullName) { mutableStateOf(userProfile?.fullName ?: "") }
    var editPhone by remember(userProfile?.phoneNumber) { mutableStateOf(userProfile?.phoneNumber ?: "") }
    var editCity by remember(userProfile?.city) { mutableStateOf(userProfile?.city ?: "") }
    var editPhotoBase64 by remember(userProfile?.photoBase64) {
        mutableStateOf(userProfile?.photoBase64 ?: "")
    }
    var showProfileEditor by remember { mutableStateOf(false) }

    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            val encoded = ImageCodecHelper.encodeUriToBase64Jpeg(
                context = context,
                uri = uri,
                maxDimension = 360,
                quality = 72
            )
            if (encoded != null) {
                editPhotoBase64 = encoded
            }
        }
    }

    val avatarBitmap = remember(userProfile?.photoBase64) {
        ImageCodecHelper.decodeBase64ToBitmap(userProfile?.photoBase64)
    }
    val editAvatarBitmap = remember(editPhotoBase64) {
        ImageCodecHelper.decodeBase64ToBitmap(editPhotoBase64)
    }

    var showPinDialog by remember { mutableStateOf(false) }
    var showTermsDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Member Profile & Admin-Approved Edit Request Card
        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (avatarBitmap != null) {
                        Image(
                            bitmap = avatarBitmap.asImageBitmap(),
                            contentDescription = "Profile Photo",
                            modifier = Modifier
                                .size(58.dp)
                                .clip(CircleShape),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
                            modifier = Modifier.size(58.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Person,
                                    contentDescription = "Avatar",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = userProfile?.fullName?.ifBlank { userProfile.email } ?: "Member",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = userProfile?.email ?: "",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (userProfile?.phoneNumber?.isNotBlank() == true) {
                            Text(
                                text = "${userProfile.phoneNumber} • ${userProfile.city}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    OutlinedButton(
                        onClick = { showProfileEditor = !showProfileEditor },
                        modifier = Modifier.testTag("toggle_profile_edit_button")
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = "Request Edit", modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Edit")
                    }
                }

                if (userProfile?.hasPendingProfileEdit == true) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xFFF59E0B).copy(alpha = 0.15f),
                        border = BorderStroke(1.dp, Color(0xFFF59E0B).copy(alpha = 0.4f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.HourglassTop,
                                contentDescription = "Pending Approval",
                                tint = Color(0xFFF59E0B),
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Profile change request (${userProfile.pendingFullName}) is awaiting Admin approval.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }

                if (showProfileEditor) {
                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Request Profile & Photo Update (Requires Admin Approval)",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (editAvatarBitmap != null) {
                            Image(
                                bitmap = editAvatarBitmap.asImageBitmap(),
                                contentDescription = "New Profile Photo",
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape),
                                contentScale = ContentScale.Crop
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                        }
                        OutlinedButton(
                            onClick = {
                                photoPicker.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            }
                        ) {
                            Icon(Icons.Default.PhotoCamera, contentDescription = "Pick Photo", modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Choose New Photo")
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = editName,
                        onValueChange = { editName = it },
                        label = { Text("Full Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = editPhone,
                        onValueChange = { editPhone = it },
                        label = { Text("Mobile Number") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = editCity,
                        onValueChange = { editCity = it },
                        label = { Text("City / State") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Button(
                        onClick = {
                            onRequestProfileEdit(editName, editPhone, editCity, editPhotoBase64)
                            showProfileEditor = false
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("submit_profile_edit_request_button")
                    ) {
                        Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Submit Profile Update for Admin Approval")
                    }
                }
            }
        }

        // 1B. Permanent Website QR Code & Apple iPhone / Android Share Hub
        PermanentQrAndShareCard(
            appleWebAppUrl = appleWebAppUrl,
            androidApkUrl = androidApkShareUrl,
            isAdmin = isAdmin,
            onUpdateUrls = onUpdateShareUrls
        )

        // 2. Customizable Notification Center (Including 1-Hour Offline Mobile Data Reminder)
        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.NotificationsActive,
                        contentDescription = "Notifications",
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Customizable Notification Settings",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Control background push alerts, privacy masking ('Trading Zone • 1 new message'), and offline mobile data reminders.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Toggle 1: Instant Broadcast Notifications
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Instant Message Push Notifications",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Push 'Trading Zone • 1 new message' even when app is closed",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = notificationsEnabled,
                        onCheckedChange = onToggleNotifications,
                        modifier = Modifier.testTag("switch_instant_notifications")
                    )
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                // Toggle 2: Offline Mobile Data Reminder Notification (Default 1 Hour)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Offline Mobile Data Reminder",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Alert me if mobile data / Wi-Fi is disconnected so I don't miss instant messages",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = offlineDataReminderEnabled,
                        onCheckedChange = onToggleOfflineDataReminder,
                        modifier = Modifier.testTag("switch_offline_data_reminder")
                    )
                }

                if (offlineDataReminderEnabled) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Offline Reminder Interval:",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(
                            30 to "30 Min",
                            60 to "1 Hour (Default)",
                            120 to "2 Hours"
                        ).forEach { (minutes, label) ->
                            FilterChip(
                                selected = offlineReminderIntervalMinutes == minutes,
                                onClick = { onChangeOfflineReminderInterval(minutes) },
                                label = { Text(label) },
                                modifier = Modifier.testTag("chip_offline_interval_$minutes")
                            )
                        }
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                // Toggle 3: Priority Vibration
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Priority Vibration on New Alerts",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Vibrate device when a new Trading Zone broadcast arrives",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = highRiskVibrationEnabled,
                        onCheckedChange = onToggleHighRiskVibration,
                        modifier = Modifier.testTag("switch_vibration")
                    )
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                // Toggle 4: Cycle Expiry Reminder
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Membership Cycle Expiry Reminders",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Notify when 2 days or fewer remain in your subscription cycle",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = cycleExpiryNotificationsEnabled,
                        onCheckedChange = onToggleCycleExpiryNotifications,
                        modifier = Modifier.testTag("switch_cycle_expiry")
                    )
                }
            }
        }

        // 3. Appearance & Smooth Display Preferences
        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Appearance & Smooth UI Preferences",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(10.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        AppThemePreference.DARK to "Dark OLED",
                        AppThemePreference.LIGHT to "Daylight",
                        AppThemePreference.SYSTEM to "Auto"
                    ).forEach { (mode, label) ->
                        FilterChip(
                            selected = themePreference == mode,
                            onClick = { onChangeTheme(mode) },
                            label = { Text(label) },
                            modifier = Modifier.testTag("theme_chip_${mode.name}")
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Compact Message Cards", style = MaterialTheme.typography.bodyMedium)
                    Switch(checked = compactFeedMode, onCheckedChange = onToggleCompactFeedMode)
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Large Readable Typography", style = MaterialTheme.typography.bodyMedium)
                    Switch(checked = largeTextMode, onCheckedChange = onToggleLargeTextMode)
                }
            }
        }

        // 4. App Lock PIN & Security Question
        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "App Lock PIN & Recovery Question",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Current Security Question: ${securityQuestion.ifBlank { userProfile?.securityQuestion ?: "Not configured" }}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Biometric Fingerprint Unlock", style = MaterialTheme.typography.bodyMedium)
                    Switch(checked = isBiometricEnabled, onCheckedChange = onToggleBiometric)
                }
                Spacer(modifier = Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(
                        onClick = { showPinDialog = true },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("change_pin_button")
                    ) {
                        Icon(Icons.Default.Password, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Change PIN")
                    }
                    Button(
                        onClick = onLockNow,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("lock_app_now_button")
                    ) {
                        Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Lock App Now")
                    }
                }
            }
        }

        // 5. Legal Terms, Platform Credits & Sign Out
        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Trading Zone Compliance & Platform Info",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Platform Operator: Atharva Garg\nAcademic Guide: Mr. Vikash Chandra Garg\nStrict Policy: Disclosing personal identity, phone numbers, or IDs in Commodity leads to immediate account suspension with NO REFUND.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(
                        onClick = { showTermsDialog = true },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Gavel, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Terms & Policy")
                    }
                    Button(
                        onClick = onSignOut,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("sign_out_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Sign Out")
                    }
                }
            }
        }
    }

    if (showPinDialog) {
        var currentPin by remember { mutableStateOf("") }
        var newPin by remember { mutableStateOf("") }
        var newQuestion by remember { mutableStateOf(securityQuestion) }
        var newAnswer by remember { mutableStateOf("") }
        var errorMsg by remember { mutableStateOf<String?>(null) }

        AlertDialog(
            onDismissRequest = { showPinDialog = false },
            title = { Text("Update App PIN & Security Question") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = currentPin,
                        onValueChange = { if (it.length <= 8 && it.all(Char::isDigit)) currentPin = it },
                        label = { Text("Current App PIN") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = newPin,
                        onValueChange = { if (it.length <= 8 && it.all(Char::isDigit)) newPin = it },
                        label = { Text("New 4–8 Digit PIN") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = newQuestion,
                        onValueChange = { newQuestion = it },
                        label = { Text("Custom Recovery Security Question") },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = newAnswer,
                        onValueChange = { newAnswer = it },
                        label = { Text("Recovery Answer") },
                        singleLine = true
                    )
                    errorMsg?.let {
                        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (currentPin.isNotBlank() && newPin.length >= 4) {
                            if (!onUpdatePin(currentPin, newPin)) {
                                errorMsg = "Current PIN is incorrect."
                                return@Button
                            }
                        }
                        if (newQuestion.isNotBlank() && newAnswer.isNotBlank()) {
                            onUpdateSecurityQuestion(newQuestion.trim(), newAnswer.trim())
                        }
                        showPinDialog = false
                    }
                ) {
                    Text("Save Changes")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPinDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showTermsDialog) {
        AlertDialog(
            onDismissRequest = { showTermsDialog = false },
            title = { Text("Terms, Conditions & No-Refund Policy") },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text(
                        text = DEFAULT_TERMS_AND_CONDITIONS.trim(),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            },
            confirmButton = {
                Button(onClick = { showTermsDialog = false }) {
                    Text("Close")
                }
            }
        )
    }
}
