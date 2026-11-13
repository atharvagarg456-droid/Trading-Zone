package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.data.model.AccessRequest
import com.example.data.model.DeviceRecord
import com.example.data.model.PricingConfig
import com.example.data.model.UserProfile
import com.example.ui.components.ImageCodecHelper

@Composable
fun MembershipScreen(
    userProfile: UserProfile?,
    deviceRecord: DeviceRecord?,
    pricingConfig: PricingConfig,
    myRequests: List<AccessRequest>,
    isBusy: Boolean,
    onRequestFreeTrial: () -> Unit,
    onSubmitUpiPayment: (
        planName: String,
        amountInr: Double,
        validityDays: Long,
        utrReference: String,
        paymentScreenshotBase64: String
    ) -> Unit
) {
    val context = LocalContext.current
    val plans = remember(pricingConfig) { pricingConfig.availablePlans() }
    var selectedPlanIndex by remember { mutableIntStateOf(0) }
    val selectedPlan = plans.getOrElse(selectedPlanIndex) { plans.first() }

    var utrInput by remember { mutableStateOf("") }
    var screenshotBase64 by remember { mutableStateOf("") }
    var errorText by remember { mutableStateOf<String?>(null) }

    val daysRemaining = userProfile?.daysRemaining() ?: 0
    val hasActiveSub = userProfile?.isEntitledNow() == true
    val hasPendingRequest = myRequests.any { it.status == "PENDING" }
    val canRequestTrial =
        userProfile?.trialUsed == false && deviceRecord?.trialClaimed != true && !hasActiveSub && !hasPendingRequest

    val screenshotPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            val encoded = ImageCodecHelper.encodeUriToBase64Jpeg(
                context = context,
                uri = uri,
                maxDimension = 850,
                quality = 72
            )
            if (encoded != null) {
                screenshotBase64 = encoded
                errorText = null
            } else {
                errorText = "Unable to process screenshot image."
            }
        }
    }

    val qrBitmap = remember(selectedPlan.qrCodeBase64) {
        ImageCodecHelper.decodeBase64ToBitmap(selectedPlan.qrCodeBase64)
    }
    val screenshotPreviewBitmap = remember(screenshotBase64) {
        ImageCodecHelper.decodeBase64ToBitmap(screenshotBase64)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Subscription Status Card
        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (hasActiveSub) {
                    Color(0xFF10B981).copy(alpha = 0.14f)
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                }
            ),
            border = BorderStroke(
                1.dp,
                if (hasActiveSub) Color(0xFF10B981).copy(alpha = 0.5f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(18.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (hasActiveSub) Icons.Default.Verified else Icons.Default.WorkspacePremium,
                    contentDescription = "Subscription Status",
                    tint = if (hasActiveSub) Color(0xFF10B981) else Color(0xFFF59E0B),
                    modifier = Modifier.size(36.dp)
                )
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = when {
                            hasActiveSub && userProfile?.entitlementStatus == "TRIAL_ACTIVE" ->
                                "7-Day Free Trial Active"
                            hasActiveSub -> "Premium Cycle Active (${userProfile?.activePlanName})"
                            userProfile?.entitlementStatus == "TRIAL_PENDING" || hasPendingRequest ->
                                "Verification Pending"
                            else -> "Membership Required"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (hasActiveSub) {
                            "$daysRemaining day(s) remaining in your current cycle"
                        } else {
                            "Choose a membership plan below or request a 1-time 7-Day Free Trial."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // 1-Time 7-Day Free Trial Banner
        if (canRequestTrial) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFF59E0B).copy(alpha = 0.12f)),
                border = BorderStroke(1.dp, Color(0xFFF59E0B).copy(alpha = 0.45f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.CardGiftcard,
                            contentDescription = "Free Trial",
                            tint = Color(0xFFF59E0B)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "One-Time 7-Day Free Trial",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "New members can request a one-time 7-day trial pass bound to this hardware device.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Button(
                        onClick = onRequestFreeTrial,
                        enabled = !isBusy,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("request_free_trial_button"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFF59E0B),
                            contentColor = Color.Black
                        )
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Request 7-Day Free Trial", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Select Membership Plan
        Text(
            text = "Select Membership Cycle Plan",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            plans.forEachIndexed { index, plan ->
                val isSelected = selectedPlanIndex == index
                Card(
                    onClick = { selectedPlanIndex = index },
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isSelected) {
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                        } else {
                            MaterialTheme.colorScheme.surface
                        }
                    ),
                    border = BorderStroke(
                        width = if (isSelected) 2.dp else 1.dp,
                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("select_plan_${plan.tierIndex}")
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = plan.planName,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "₹${plan.priceInr.toInt()}",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "${plan.validityDays} Days",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // Selected Plan QR & Payment Submission Card
        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "${selectedPlan.planName} — ₹${selectedPlan.priceInr.toInt()} (${selectedPlan.validityDays} Days)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Scan the official UPI QR or pay via UPI ID, then upload your payment screenshot and UTR reference below.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(14.dp))

                if (qrBitmap != null) {
                    Image(
                        bitmap = qrBitmap.asImageBitmap(),
                        contentDescription = "UPI QR Code",
                        modifier = Modifier
                            .size(210.dp)
                            .clip(RoundedCornerShape(14.dp)),
                        contentScale = ContentScale.Fit
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                } else {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.size(160.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(12.dp),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.QrCode2,
                                contentDescription = "QR Code",
                                modifier = Modifier.size(56.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Pay via UPI ID below",
                                style = MaterialTheme.typography.labelSmall,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }

                if (!selectedPlan.qrOnlyMode && pricingConfig.upiId.isNotBlank()) {
                    OutlinedButton(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("UPI ID", pricingConfig.upiId))
                            Toast.makeText(context, "UPI ID copied: ${pricingConfig.upiId}", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.testTag("copy_upi_button")
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "Copy UPI ID", modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("UPI: ${pricingConfig.upiId}")
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(14.dp))

                // Mandatory Payment Screenshot Upload
                Text(
                    text = "Step 1: Attach Payment Screenshot (Mandatory)",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.align(Alignment.Start)
                )
                Spacer(modifier = Modifier.height(8.dp))

                if (screenshotPreviewBitmap != null) {
                    Image(
                        bitmap = screenshotPreviewBitmap.asImageBitmap(),
                        contentDescription = "Payment Screenshot Preview",
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                            .clip(RoundedCornerShape(12.dp)),
                        contentScale = ContentScale.Crop
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }

                OutlinedButton(
                    onClick = {
                        screenshotPicker.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("pick_payment_screenshot_button")
                ) {
                    Icon(Icons.Default.AddPhotoAlternate, contentDescription = "Pick Screenshot")
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(if (screenshotBase64.isBlank()) "Select Payment Screenshot" else "Change Payment Screenshot")
                }

                Spacer(modifier = Modifier.height(14.dp))

                // UTR / Reference ID Input
                Text(
                    text = "Step 2: Enter 12-Digit UPI UTR / Reference Number",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.align(Alignment.Start)
                )
                Spacer(modifier = Modifier.height(6.dp))

                OutlinedTextField(
                    value = utrInput,
                    onValueChange = {
                        utrInput = it
                        errorText = null
                    },
                    label = { Text("UPI Reference / UTR Number") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("utr_input_field")
                )

                errorText?.let {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = it,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                Button(
                    onClick = {
                        if (screenshotBase64.isBlank()) {
                            errorText = "Please attach your UPI payment screenshot first."
                            return@Button
                        }
                        if (utrInput.trim().length < 6) {
                            errorText = "Please enter a valid UPI UTR / transaction reference."
                            return@Button
                        }
                        onSubmitUpiPayment(
                            selectedPlan.planName,
                            selectedPlan.priceInr,
                            selectedPlan.validityDays,
                            utrInput.trim(),
                            screenshotBase64
                        )
                        utrInput = ""
                        screenshotBase64 = ""
                    },
                    enabled = !hasPendingRequest && !isBusy,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("submit_payment_request_button")
                ) {
                    Icon(Icons.Default.Send, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (hasPendingRequest) "Verification In Progress..." else "Submit Payment for Admin Verification",
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // User's Payment & Trial Request History
        if (myRequests.isNotEmpty()) {
            Text(
                text = "Your Membership Requests",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            myRequests.forEach { req ->
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "${req.planName} (₹${req.amountInr.toInt()} • ${req.validityDays}d)",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Ref: ${req.utrReference}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = when (req.status) {
                                "APPROVED" -> Color(0xFF10B981).copy(alpha = 0.18f)
                                "REJECTED" -> MaterialTheme.colorScheme.error.copy(alpha = 0.18f)
                                else -> Color(0xFFF59E0B).copy(alpha = 0.18f)
                            }
                        ) {
                            Text(
                                text = req.status,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = when (req.status) {
                                    "APPROVED" -> Color(0xFF10B981)
                                    "REJECTED" -> MaterialTheme.colorScheme.error
                                    else -> Color(0xFFF59E0B)
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
