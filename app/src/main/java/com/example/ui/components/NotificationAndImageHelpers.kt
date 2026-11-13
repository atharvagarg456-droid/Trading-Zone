package com.example.ui.components

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.net.Uri
import android.os.Build
import android.util.Base64
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import java.io.ByteArrayOutputStream
import kotlin.math.min

object NotificationHelper {
    const val CHANNEL_BROADCAST = "trading_zone_broadcast_alerts"
    const val CHANNEL_CYCLE_PAYMENT = "trading_zone_cycle_reminders"
    const val CHANNEL_OFFLINE_REMINDER = "trading_zone_offline_data_reminders"
    const val CHANNEL_BG_SERVICE = "trading_zone_bg_sync"

    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                ?: return

            val broadcastChannel = NotificationChannel(
                CHANNEL_BROADCAST,
                "Broadcast Message Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Private alerts when a new broadcast message is posted"
                lockscreenVisibility = Notification.VISIBILITY_PRIVATE
            }

            val cycleChannel = NotificationChannel(
                CHANNEL_CYCLE_PAYMENT,
                "Subscription Cycle Reminders",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Reminders when your subscription cycle is expiring"
            }

            val offlineChannel = NotificationChannel(
                CHANNEL_OFFLINE_REMINDER,
                "Offline Mobile Data Reminders",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Alerts when your phone is offline for 1 hour so you don't miss instant messages"
            }

            val bgChannel = NotificationChannel(
                CHANNEL_BG_SERVICE,
                "Trading Zone Background Sync",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps Trading Zone connected for instant notifications"
                setShowBadge(false)
            }

            manager.createNotificationChannels(
                listOf(broadcastChannel, cycleChannel, offlineChannel, bgChannel)
            )
        }
    }

    private fun canPostNotifications(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    private fun createLaunchPendingIntent(context: Context, requestCode: Int): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    /**
     * Strictly private notification: never discloses message title, risk level, or content.
     * Displays only "Trading Zone" and "1 new message".
     */
    fun showBroadcastNotification(
        context: Context,
        isEdited: Boolean = false,
        notificationId: Int = 1001
    ) {
        ensureChannels(context)
        if (!canPostNotifications(context)) return

        val title = if (isEdited) "Trading Zone • Message Updated" else "Trading Zone"
        val body = "1 new message"

        val notification = NotificationCompat.Builder(context, CHANNEL_BROADCAST)
            .setSmallIcon(android.R.drawable.ic_dialog_email)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setContentIntent(createLaunchPendingIntent(context, notificationId))
            .setAutoCancel(true)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(notificationId, notification)
        } catch (_: SecurityException) {
        }
    }

    /**
     * Triggered when the device has been offline / disconnected from mobile data or Wi-Fi
     * for the user's configured interval (default 1 hour).
     */
    fun showOfflineConnectDataNotification(
        context: Context,
        offlineMinutes: Int = 60
    ) {
        ensureChannels(context)
        if (!canPostNotifications(context)) return

        val durationLabel = if (offlineMinutes >= 60) {
            val hrs = offlineMinutes / 60
            "$hrs hour${if (hrs > 1) "s" else ""}"
        } else {
            "$offlineMinutes minutes"
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_OFFLINE_REMINDER)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setContentTitle("Trading Zone • No Network Connected")
            .setContentText("Offline for $durationLabel. Connect mobile data or Wi-Fi for instant notifications.")
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    "Your mobile has been disconnected from network data for $durationLabel. " +
                        "Please turn on mobile data or Wi-Fi to receive instant Trading Zone trade notifications."
                )
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(createLaunchPendingIntent(context, 4040))
            .setAutoCancel(true)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(4040, notification)
        } catch (_: SecurityException) {
        }
    }

    fun showCyclePaymentReminderNotification(
        context: Context,
        isExpired: Boolean,
        daysLeft: Int
    ) {
        ensureChannels(context)
        if (!canPostNotifications(context)) return

        val title = if (isExpired) {
            "Trading Zone • Membership Expired"
        } else {
            "Trading Zone • $daysLeft Day(s) Remaining"
        }
        val body = if (isExpired) {
            "Renew your membership plan to continue viewing verified broadcasts and Commodity updates."
        } else {
            "Your membership cycle ends in $daysLeft day(s). Tap to view renewal plans."
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_CYCLE_PAYMENT)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(createLaunchPendingIntent(context, 2020))
            .setAutoCancel(true)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(2020, notification)
        } catch (_: SecurityException) {
        }
    }

    fun buildBackgroundSyncNotification(context: Context, isConnected: Boolean = true): Notification {
        ensureChannels(context)
        val statusText = if (isConnected) {
            "Instant notification listener active"
        } else {
            "Offline — Connect mobile data for instant notifications"
        }
        return NotificationCompat.Builder(context, CHANNEL_BG_SERVICE)
            .setSmallIcon(android.R.drawable.ic_popup_sync)
            .setContentTitle("Trading Zone")
            .setContentText(statusText)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setContentIntent(createLaunchPendingIntent(context, 3030))
            .build()
    }
}

object ImageCodecHelper {

    fun encodeUriToBase64Jpeg(
        context: Context,
        uri: Uri,
        maxDimension: Int = 720,
        quality: Int = 72
    ): String? {
        return try {
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: return null
            val original = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
            encodeBitmapToBase64Jpeg(original, maxDimension, quality)
        } catch (_: Exception) {
            null
        }
    }

    fun encodeBitmapToBase64Jpeg(
        bitmap: Bitmap,
        maxDimension: Int = 720,
        quality: Int = 72
    ): String {
        val ratio = min(
            maxDimension.toFloat() / bitmap.width.coerceAtLeast(1),
            maxDimension.toFloat() / bitmap.height.coerceAtLeast(1)
        ).coerceAtMost(1f)

        val targetW = (bitmap.width * ratio).toInt().coerceAtLeast(1)
        val targetH = (bitmap.height * ratio).toInt().coerceAtLeast(1)
        val scaled = if (targetW != bitmap.width || targetH != bitmap.height) {
            Bitmap.createScaledBitmap(bitmap, targetW, targetH, true)
        } else {
            bitmap
        }

        val out = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, quality, out)
        val b64 = Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
        return "data:image/jpeg;base64,$b64"
    }

    fun decodeBase64ToBitmap(base64Data: String?): Bitmap? {
        if (base64Data.isNullOrBlank()) return null
        return try {
            val clean = base64Data.substringAfter("base64,", base64Data)
            val bytes = Base64.decode(clean, Base64.DEFAULT)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        } catch (_: Exception) {
            null
        }
    }
}

data class DrawnStroke(
    val points: List<Offset>,
    val color: Color,
    val strokeWidth: Float = 8f
)

@Composable
fun PhotoMarkingDialog(
    sourceBitmap: Bitmap,
    onDismiss: () -> Unit,
    onSaveMarkedPhoto: (String) -> Unit
) {
    val strokes = remember { mutableStateListOf<DrawnStroke>() }
    val currentPoints = remember { mutableStateListOf<Offset>() }
    var selectedColor by remember { mutableStateOf(Color(0xFFEF4444)) }
    var canvasWidth by remember { mutableStateOf(1f) }
    var canvasHeight by remember { mutableStateOf(1f) }

    val palette = listOf(
        Color(0xFFEF4444),
        Color(0xFF10B981),
        Color(0xFFF59E0B),
        Color(0xFF3B82F6),
        Color(0xFFFFFFFF)
    )

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                Text(
                    text = "Mark Trade Levels on Chart",
                    style = MaterialTheme.typography.titleLarge
                )
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    palette.forEach { color ->
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(color)
                                .border(
                                    width = if (selectedColor == color) 3.dp else 1.dp,
                                    color = if (selectedColor == color) MaterialTheme.colorScheme.primary else Color.Gray,
                                    shape = CircleShape
                                )
                                .clickable { selectedColor = color }
                        )
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    OutlinedButton(onClick = { strokes.clear(); currentPoints.clear() }) {
                        Text("Clear")
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.Black)
                ) {
                    Canvas(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(selectedColor) {
                                detectDragGestures(
                                    onDragStart = { offset ->
                                        currentPoints.clear()
                                        currentPoints.add(offset)
                                    },
                                    onDrag = { change, _ ->
                                        change.consume()
                                        currentPoints.add(change.position)
                                    },
                                    onDragEnd = {
                                        if (currentPoints.size > 1) {
                                            strokes.add(
                                                DrawnStroke(
                                                    points = currentPoints.toList(),
                                                    color = selectedColor
                                                )
                                            )
                                        }
                                        currentPoints.clear()
                                    }
                                )
                            }
                    ) {
                        canvasWidth = size.width.coerceAtLeast(1f)
                        canvasHeight = size.height.coerceAtLeast(1f)
                        drawImage(
                            image = sourceBitmap.asImageBitmap(),
                            dstOffset = IntOffset.Zero,
                            dstSize = IntSize(size.width.toInt(), size.height.toInt())
                        )
                        strokes.forEach { stroke ->
                            for (i in 0 until stroke.points.size - 1) {
                                drawLine(
                                    color = stroke.color,
                                    start = stroke.points[i],
                                    end = stroke.points[i + 1],
                                    strokeWidth = stroke.strokeWidth,
                                    cap = StrokeCap.Round
                                )
                            }
                        }
                        for (i in 0 until currentPoints.size - 1) {
                            drawLine(
                                color = selectedColor,
                                start = currentPoints[i],
                                end = currentPoints[i + 1],
                                strokeWidth = 8f,
                                cap = StrokeCap.Round
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Cancel")
                    }
                    Button(
                        onClick = {
                            val resultBmp = Bitmap.createBitmap(
                                sourceBitmap.width,
                                sourceBitmap.height,
                                Bitmap.Config.ARGB_8888
                            )
                            val canvas = Canvas(resultBmp)
                            canvas.drawBitmap(sourceBitmap, 0f, 0f, null)
                            val scaleX = sourceBitmap.width.toFloat() / canvasWidth.coerceAtLeast(1f)
                            val scaleY = sourceBitmap.height.toFloat() / canvasHeight.coerceAtLeast(1f)
                            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                                style = Paint.Style.STROKE
                                strokeCap = Paint.Cap.ROUND
                            }
                            strokes.forEach { stroke ->
                                paint.color = stroke.color.toArgb()
                                paint.strokeWidth = stroke.strokeWidth * ((scaleX + scaleY) / 2f)
                                for (i in 0 until stroke.points.size - 1) {
                                    val p1 = stroke.points[i]
                                    val p2 = stroke.points[i + 1]
                                    canvas.drawLine(
                                        p1.x * scaleX,
                                        p1.y * scaleY,
                                        p2.x * scaleX,
                                        p2.y * scaleY,
                                        paint
                                    )
                                }
                            }
                            val encoded = ImageCodecHelper.encodeBitmapToBase64Jpeg(resultBmp)
                            onSaveMarkedPhoto(encoded)
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Save Marked Chart")
                    }
                }
            }
        }
    }
}
