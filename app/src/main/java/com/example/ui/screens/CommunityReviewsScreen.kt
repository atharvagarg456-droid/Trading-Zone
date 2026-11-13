package com.example.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.data.model.BroadcastMessage
import com.example.data.model.COMMODITY_IDENTITY_WARNING
import com.example.data.model.CommunityReview
import com.example.data.model.PricingConfig
import com.example.data.model.UserProfile
import com.example.ui.components.ImageCodecHelper
import java.text.SimpleDateFormat
import java.util.Locale

@Composable
fun CommunityReviewsScreen(
    latestTradeMessage: BroadcastMessage?,
    reviews: List<CommunityReview>,
    userProfile: UserProfile?,
    pricingConfig: PricingConfig,
    onlinePeopleCount: Int,
    isAdmin: Boolean,
    isBusy: Boolean,
    onSubmitReview: (
        tradeMessageId: String,
        tradeMessageTitle: String,
        tradeRiskLevel: String,
        topicTag: String,
        opinionText: String,
        rating: Int
    ) -> Unit,
    onDeleteReview: (String) -> Unit,
    onTogglePinReview: (reviewId: String, pinned: Boolean) -> Unit = { _, _ -> },
    onToggleCommodityChatLock: (Boolean) -> Unit = {},
    onMuteUserInCommodity: (userId: String, userName: String) -> Unit = { _, _ -> },
    onNavigateToMembership: () -> Unit
) {
    val isEntitled = isAdmin || (userProfile?.isEntitledNow() == true)
    val dailyLimit = pricingConfig.dailyReviewLimitPerUser.toInt().coerceAtLeast(1)
    val usedToday = userProfile?.usedReviewsToday() ?: 0
    val remainingToday = userProfile?.remainingReviewsToday(dailyLimit) ?: dailyLimit
    val isUserMuted = userProfile?.isCommodityMuted == true
    val isChatLocked = pricingConfig.commodityChatLocked

    var selectedTopic by remember { mutableStateOf("Commodity") }
    var filterTopic by remember { mutableStateOf("All") }
    var opinionText by remember { mutableStateOf("") }

    val commodityCategories = remember {
        listOf("Commodity", "Gold & Silver", "Crude & Energy", "Base Metals")
    }

    val filteredReviews = remember(reviews, filterTopic) {
        if (filterTopic == "All") {
            reviews
        } else {
            reviews.filter { it.topicTag.equals(filterTopic, ignoreCase = true) }
        }
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        if (!isEntitled) {
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
                    shape = RoundedCornerShape(24.dp),
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
                            text = "Commodity Section Locked",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Only verified members with active subscription days left can read or post messages in the Commodity section.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(18.dp))
                        Button(
                            onClick = onNavigateToMembership,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("View Membership Plans")
                        }
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 680.dp)
                    .testTag("community_reviews_list"),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // 1. Header Card: Commodity Section + Admin Controls + Strict Identity/Phone Warning (No Star Rating & No Risk Level)
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(22.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(modifier = Modifier.padding(18.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Commodity",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold
                                )
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(16.dp),
                                        color = Color(0xFF10B981).copy(alpha = 0.16f)
                                    ) {
                                        Text(
                                            text = "● $onlinePeopleCount Online",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = Color(0xFF10B981),
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                        )
                                    }
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = if (remainingToday > 0 || isAdmin) {
                                            MaterialTheme.colorScheme.primaryContainer
                                        } else {
                                            MaterialTheme.colorScheme.errorContainer
                                        }
                                    ) {
                                        Text(
                                            text = if (isAdmin) {
                                                "Admin Unlimited"
                                            } else {
                                                "$remainingToday / $dailyLimit left today"
                                            },
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.SemiBold,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                }
                            }

                            if (isAdmin) {
                                Spacer(modifier = Modifier.height(10.dp))
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = MaterialTheme.colorScheme.secondaryContainer,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 12.dp, vertical = 10.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "Admin Commodity Controls",
                                                style = MaterialTheme.typography.labelLarge,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSecondaryContainer
                                            )
                                            Text(
                                                text = if (isChatLocked) {
                                                    "Commodity chat is currently PAUSED for members."
                                                } else {
                                                    "Members can post (Max $dailyLimit/day • Pin & Phone/ID Block active)."
                                                },
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSecondaryContainer
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(8.dp))
                                        OutlinedButton(
                                            onClick = { onToggleCommodityChatLock(!isChatLocked) },
                                            modifier = Modifier.testTag("toggle_commodity_lock_button")
                                        ) {
                                            Text(if (isChatLocked) "Unlock Chat" else "Pause Chat")
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            // Strict No-Refund Identity / Phone / ID Disclosure Warning Banner
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = MaterialTheme.colorScheme.errorContainer,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("commodity_identity_warning_banner")
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.Top
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Warning,
                                        contentDescription = "Strict Warning",
                                        tint = MaterialTheme.colorScheme.onErrorContainer,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = COMMODITY_IDENTITY_WARNING,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onErrorContainer,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            // Commodity Category Selector (No Star Rating, No Risk Level)
                            Text(
                                text = "Select Commodity Topic:",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                commodityCategories.forEach { topic ->
                                    FilterChip(
                                        selected = selectedTopic == topic,
                                        onClick = { selectedTopic = topic },
                                        label = { Text(topic, style = MaterialTheme.typography.labelSmall) },
                                        modifier = Modifier.testTag("topic_chip_$topic")
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            if (!isAdmin && isUserMuted) {
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.errorContainer,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = "Your account has been muted from Commodity messaging by the Admin.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onErrorContainer,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.padding(14.dp)
                                    )
                                }
                            } else if (!isAdmin && isChatLocked) {
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = "Commodity messaging is currently paused by the Admin. Please check back shortly.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(14.dp)
                                    )
                                }
                            } else {
                                OutlinedTextField(
                                    value = opinionText,
                                    onValueChange = { opinionText = it },
                                    label = {
                                        Text("Write your Commodity message (No phone numbers, IDs, or personal info)...")
                                    },
                                    minLines = 3,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("community_review_input")
                                )

                                Spacer(modifier = Modifier.height(8.dp))

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Info,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Protection Active: Phone numbers, emails, UPI IDs & personal handles are strictly blocked and reported to Admin. Abusive words are blurred (▓▓▓▓).",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                Spacer(modifier = Modifier.height(12.dp))

                                Button(
                                    onClick = {
                                        if (opinionText.isNotBlank()) {
                                            onSubmitReview(
                                                latestTradeMessage?.id ?: "commodity_post",
                                                latestTradeMessage?.title ?: "Commodity Discussion",
                                                "MEDIUM",
                                                selectedTopic,
                                                opinionText,
                                                5
                                            )
                                            opinionText = ""
                                        }
                                    },
                                    enabled = !isBusy && opinionText.isNotBlank() && (isAdmin || remainingToday > 0),
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(48.dp)
                                        .testTag("submit_community_review_button")
                                ) {
                                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        if (!isAdmin && remainingToday <= 0) {
                                            "Daily Message Limit Reached ($usedToday/$dailyLimit)"
                                        } else {
                                            "Send Commodity Message"
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                // 2. Filter Bar for Commodity Feed
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        (listOf("All") + commodityCategories).forEach { filter ->
                            FilterChip(
                                selected = filterTopic == filter,
                                onClick = { filterTopic = filter },
                                label = { Text(filter, style = MaterialTheme.typography.labelSmall) }
                            )
                        }
                    }
                }

                if (filteredReviews.isEmpty()) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Text(
                                text = "No Commodity messages in this category yet. Share your Commodity view above!",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(20.dp)
                            )
                        }
                    }
                } else {
                    items(filteredReviews, key = { it.id.ifBlank { it.userId + it.createdAt } }) { review ->
                        CommunityReviewItemCard(
                            review = review,
                            isAdmin = isAdmin,
                            canDelete = isAdmin || review.userId == userProfile?.userId,
                            onTogglePin = { onTogglePinReview(review.id, !review.isPinned) },
                            onMuteUser = { onMuteUserInCommodity(review.userId, review.userName) },
                            onDelete = { onDeleteReview(review.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CommunityReviewItemCard(
    review: CommunityReview,
    isAdmin: Boolean,
    canDelete: Boolean,
    onTogglePin: () -> Unit,
    onMuteUser: () -> Unit,
    onDelete: () -> Unit
) {
    val avatarBmp = remember(review.userPhotoBase64) {
        ImageCodecHelper.decodeBase64ToBitmap(review.userPhotoBase64)
    }
    val dateFormat = remember { SimpleDateFormat("dd MMM • hh:mm a", Locale.getDefault()) }
    val dateLabel = remember(review.createdAt) {
        review.createdAt?.toDate()?.let { dateFormat.format(it) } ?: "Just now"
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (review.isPinned) {
                    Modifier.border(
                        width = 1.5.dp,
                        color = MaterialTheme.colorScheme.primary,
                        shape = RoundedCornerShape(18.dp)
                    )
                } else {
                    Modifier
                }
            )
            .testTag("community_review_card_${review.id}"),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            if (review.isPinned) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier
                        .padding(bottom = 8.dp)
                        .testTag("pinned_commodity_badge_${review.id}")
                ) {
                    Text(
                        text = "📌 Pinned Message",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    if (avatarBmp != null) {
                        Image(
                            bitmap = avatarBmp.asImageBitmap(),
                            contentDescription = "Member avatar",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .border(1.5.dp, MaterialTheme.colorScheme.primary, CircleShape)
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Person, contentDescription = null)
                        }
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = review.userName,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.primaryContainer
                            ) {
                                Text(
                                    text = review.topicTag,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Text(
                            text = dateLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (isAdmin) {
                        OutlinedButton(
                            onClick = onTogglePin,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            modifier = Modifier
                                .height(32.dp)
                                .testTag("pin_commodity_review_${review.id}")
                        ) {
                            Text(
                                text = if (review.isPinned) "Unpin" else "Pin",
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                        OutlinedButton(
                            onClick = onMuteUser,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Text("Block Msg", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    if (canDelete) {
                        IconButton(onClick = onDelete) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Delete Commodity Post",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }

            if (review.hasBlurredWords) {
                Spacer(modifier = Modifier.height(6.dp))
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.errorContainer
                ) {
                    Text(
                        text = "Word Blurred • Admin Alerted",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = review.publicContent,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
