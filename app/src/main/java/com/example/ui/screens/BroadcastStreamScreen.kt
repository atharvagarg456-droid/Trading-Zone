package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MarkEmailUnread
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.model.BroadcastMessage
import com.example.data.model.DeviceRecord
import com.example.data.model.MessageReadReceipt
import com.example.data.model.PricingConfig
import com.example.data.model.UserProfile
import com.example.ui.components.ImageCodecHelper
import java.text.SimpleDateFormat
import java.util.Locale

@Composable
fun TradeRiskBadge(riskLevel: String) {
    val upper = riskLevel.uppercase(Locale.ROOT)
    val (bg, fg, label) = when (upper) {
        "HIGH" -> Triple(Color(0xFFEF4444).copy(alpha = 0.18f), Color(0xFFEF4444), "HIGH RISK")
        "LOW" -> Triple(Color(0xFF10B981).copy(alpha = 0.18f), Color(0xFF10B981), "LOW RISK")
        else -> Triple(Color(0xFFF59E0B).copy(alpha = 0.18f), Color(0xFFF59E0B), "MEDIUM RISK")
    }
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = bg
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = fg,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        )
    }
}

@Composable
fun BroadcastStreamScreen(
    messages: List<BroadcastMessage>,
    openedMessageIds: Set<String> = emptySet(),
    allMessageReadReceipts: List<MessageReadReceipt> = emptyList(),
    userProfile: UserProfile?,
    deviceRecord: DeviceRecord?,
    pricingConfig: PricingConfig,
    onlinePeopleCount: Int,
    compactFeedMode: Boolean = false,
    largeTextMode: Boolean = false,
    isAdmin: Boolean,
    onMarkMessageOpened: (BroadcastMessage) -> Unit = {},
    onRequestFreeTrial: () -> Unit,
    onNavigateToMembership: () -> Unit,
    onNavigateToReviews: () -> Unit = {},
    onReplyToMessageRequest: (BroadcastMessage) -> Unit = {},
    onEditMessageRequest: (BroadcastMessage) -> Unit,
    onDeleteMessageRequest: (String) -> Unit
) {
    val isEntitled = isAdmin || (userProfile?.isEntitledNow() == true)
    var expandedPhotoBase64 by remember { mutableStateOf<String?>(null) }
    var inspectSeenByMessage by remember { mutableStateOf<BroadcastMessage?>(null) }
    var previewQuotedMessage by remember { mutableStateOf<BroadcastMessage?>(null) }

    val receiptsByMessageId = remember(allMessageReadReceipts) {
        allMessageReadReceipts.groupBy { it.messageId }
    }

    val unopenedCount = remember(messages, openedMessageIds, isAdmin) {
        if (isAdmin) 0 else messages.count { it.id.isNotBlank() && it.id !in openedMessageIds }
    }

    if (expandedPhotoBase64 != null) {
        val bmp = remember(expandedPhotoBase64) {
            ImageCodecHelper.decodeBase64ToBitmap(expandedPhotoBase64)
        }
        Dialog(
            onDismissRequest = { expandedPhotoBase64 = null },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.92f))
                    .clickable { expandedPhotoBase64 = null }
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                if (bmp != null) {
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = "Full screen chart",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }

    if (inspectSeenByMessage != null) {
        val msg = inspectSeenByMessage!!
        val seenList = receiptsByMessageId[msg.id].orEmpty()
        SeenByMembersDialog(
            messageTitle = msg.title,
            receipts = seenList,
            onDismiss = { inspectSeenByMessage = null }
        )
    }

    if (previewQuotedMessage != null) {
        val quoted = previewQuotedMessage!!
        AlertDialog(
            onDismissRequest = { previewQuotedMessage = null },
            title = { Text(quoted.title) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    TradeRiskBadge(riskLevel = quoted.riskLevel)
                    Text(
                        text = quoted.content,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { previewQuotedMessage = null }) {
                    Text("Close")
                }
            }
        )
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        if (!isEntitled) {
            val canClaimTrial = userProfile?.trialUsed == false && deviceRecord?.trialClaimed != true
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 460.dp),
                    shape = RoundedCornerShape(26.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Verified Trade Broadcasts Locked",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Activate your 1-time 7-Day Free Trial or a UPI QR Membership Plan (${pricingConfig.planName} • ₹${pricingConfig.priceInr.toInt()}) to unlock live trade broadcasts and marked chart setups.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(18.dp))
                        if (canClaimTrial) {
                            Button(
                                onClick = onRequestFreeTrial,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("claim_free_trial_button")
                            ) {
                                Text("Activate 1-Time 7-Day Free Trial")
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                        }
                        OutlinedButton(
                            onClick = onNavigateToMembership,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("View UPI QR Membership Plans")
                        }
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 680.dp)
                    .testTag("broadcast_stream_list"),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(if (compactFeedMode) 10.dp else 14.dp)
            ) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(22.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    Brush.horizontalGradient(
                                        colors = listOf(
                                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                                            MaterialTheme.colorScheme.surface
                                        )
                                    )
                                )
                                .padding(18.dp)
                        ) {
                            Column {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Official Trade Broadcasts",
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Surface(
                                        shape = RoundedCornerShape(16.dp),
                                        color = Color(0xFF10B981).copy(alpha = 0.18f)
                                    ) {
                                        Text(
                                            text = "● $onlinePeopleCount Online",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = Color(0xFF10B981),
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = if (unopenedCount > 0) {
                                        "You have $unopenedCount new unopened message(s). Tap the envelope button to open."
                                    } else {
                                        "All trade broadcasts opened • Tap Commodity to discuss setups."
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                OutlinedButton(
                                    onClick = onNavigateToReviews,
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text("Open Commodity Section")
                                }
                            }
                        }
                    }
                }

                if (messages.isEmpty()) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Text(
                                text = "No broadcast messages published yet.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(24.dp)
                            )
                        }
                    }
                } else {
                    items(messages, key = { it.id.ifBlank { it.title + it.createdAt } }) { message ->
                        val isOpened = isAdmin || message.id in openedMessageIds
                        val messageReceipts = receiptsByMessageId[message.id].orEmpty()

                        BroadcastMessageCard(
                            message = message,
                            isOpened = isOpened,
                            seenByCount = messageReceipts.size,
                            largeTextMode = largeTextMode,
                            isAdmin = isAdmin,
                            onOpenEnvelope = { onMarkMessageOpened(message) },
                            onInspectSeenBy = { inspectSeenByMessage = message },
                            onClickQuotedReply = {
                                val target = messages.find { it.id == message.replyToMessageId }
                                if (target != null) {
                                    previewQuotedMessage = target
                                }
                            },
                            onExpandPhoto = { photo -> expandedPhotoBase64 = photo },
                            onReply = { onReplyToMessageRequest(message) },
                            onEdit = { onEditMessageRequest(message) },
                            onDelete = { onDeleteMessageRequest(message.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BroadcastMessageCard(
    message: BroadcastMessage,
    isOpened: Boolean,
    seenByCount: Int,
    largeTextMode: Boolean,
    isAdmin: Boolean,
    onOpenEnvelope: () -> Unit,
    onInspectSeenBy: () -> Unit,
    onClickQuotedReply: () -> Unit,
    onExpandPhoto: (String) -> Unit,
    onReply: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val uriHandler = LocalUriHandler.current
    val dateFormat = remember { SimpleDateFormat("dd MMM yyyy • hh:mm a", Locale.getDefault()) }
    val dateStr = remember(message.createdAt) {
        message.createdAt?.toDate()?.let { dateFormat.format(it) } ?: "Just now"
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (message.isPinned) {
                    Modifier.border(1.5.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(20.dp))
                } else {
                    Modifier
                }
            )
            .testTag("broadcast_card_${message.id}"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            // Unopened Envelope State for Members
            AnimatedVisibility(
                visible = !isOpened,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(56.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.MarkEmailUnread,
                                contentDescription = "New Unopened Message",
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Trading Zone • 1 New Message",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Posted $dateStr by ${message.authorName}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Button(
                        onClick = onOpenEnvelope,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("open_message_envelope_${message.id}")
                    ) {
                        Text("1 New Message — Tap to Open", fontWeight = FontWeight.Bold)
                    }
                }
            }

            // Opened Message State with Smooth Spring Reveal
            AnimatedVisibility(
                visible = isOpened,
                enter = fadeIn(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) +
                    expandVertically(animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy))
            ) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (message.isPinned) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer
                                ) {
                                    Text(
                                        text = "📌 PINNED",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                    )
                                }
                            }
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.secondaryContainer
                            ) {
                                Text(
                                    text = message.category,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                            TradeRiskBadge(riskLevel = message.riskLevel)
                        }

                        if (isAdmin) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                OutlinedButton(
                                    onClick = onInspectSeenBy,
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                    modifier = Modifier
                                        .height(32.dp)
                                        .testTag("seen_by_button_${message.id}")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Visibility,
                                        contentDescription = "Seen By",
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Seen ($seenByCount)",
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                }
                                IconButton(
                                    onClick = onReply,
                                    modifier = Modifier.testTag("reply_message_${message.id}")
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.Reply,
                                        contentDescription = "Reply to Message",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                                IconButton(onClick = onEdit) {
                                    Icon(
                                        imageVector = Icons.Default.Edit,
                                        contentDescription = "Edit Message"
                                    )
                                }
                                IconButton(onClick = onDelete) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Delete Message",
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                    }

                    // WhatsApp-style Quoted Reply Bar if replying to a previous message
                    if (message.replyToMessageId.isNotBlank() || message.replyToTitle.isNotBlank()) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onClickQuotedReply() }
                                .testTag("quoted_reply_bar_${message.id}")
                        ) {
                            Row(modifier = Modifier.padding(10.dp)) {
                                Box(
                                    modifier = Modifier
                                        .width(4.dp)
                                        .height(40.dp)
                                        .clip(RoundedCornerShape(2.dp))
                                        .background(MaterialTheme.colorScheme.primary)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "↩ Replying to: ${message.replyToTitle.ifBlank { "Previous Trade Message" }}",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (message.replyToSnippet.isNotBlank()) {
                                        Text(
                                            text = message.replyToSnippet,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = message.title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = "By ${message.authorName} • $dateStr" + if (message.isEdited) " • (Edited)" else "",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(10.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = message.content,
                        style = if (largeTextMode) {
                            MaterialTheme.typography.bodyLarge.copy(fontSize = 18.sp, lineHeight = 26.sp)
                        } else {
                            MaterialTheme.typography.bodyLarge
                        },
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    if (message.markedPhotoBase64.isNotBlank()) {
                        val chartBmp = remember(message.markedPhotoBase64) {
                            ImageCodecHelper.decodeBase64ToBitmap(message.markedPhotoBase64)
                        }
                        if (chartBmp != null) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Image(
                                bitmap = chartBmp.asImageBitmap(),
                                contentDescription = "Marked Chart Photo",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 180.dp, max = 280.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .clickable { onExpandPhoto(message.markedPhotoBase64) }
                            )
                        }
                    }

                    if (message.linkUrl.isNotBlank()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedButton(
                            onClick = {
                                try {
                                    uriHandler.openUri(message.linkUrl)
                                } catch (_: Exception) {
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(message.linkTitle.ifBlank { "Open Reference Link" })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SeenByMembersDialog(
    messageTitle: String,
    receipts: List<MessageReadReceipt>,
    onDismiss: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("dd MMM • hh:mm a", Locale.getDefault()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(
                    text = "Seen By (${receipts.size} Members)",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = messageTitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        text = {
            if (receipts.isEmpty()) {
                Text(
                    text = "No members have clicked to open this message yet.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 360.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(receipts, key = { it.id.ifBlank { it.userId } }) { receipt ->
                        val avatarBmp = remember(receipt.userPhotoBase64) {
                            ImageCodecHelper.decodeBase64ToBitmap(receipt.userPhotoBase64)
                        }
                        val timeStr = remember(receipt.viewedAt) {
                            receipt.viewedAt?.toDate()?.let { dateFormat.format(it) } ?: "Recently"
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (avatarBmp != null) {
                                Image(
                                    bitmap = avatarBmp.asImageBitmap(),
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(CircleShape)
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.surfaceVariant),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.Person, contentDescription = null)
                                }
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = receipt.userName,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "${receipt.userEmail} • Opened $timeStr",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}
