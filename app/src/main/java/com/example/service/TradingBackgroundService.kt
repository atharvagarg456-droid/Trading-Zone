package com.example.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.content.ContextCompat
import com.example.R
import com.example.data.local.NetworkMonitor
import com.example.data.local.SecurityPreferences
import com.example.data.model.BroadcastMessage
import com.example.ui.components.NotificationHelper
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class TradingBackgroundService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var messageListener: ListenerRegistration? = null
    private var knownMessageSignatures = mutableMapOf<String, String>()
    private var hasSeededInitialSnapshot = false

    override fun onCreate() {
        super.onCreate()
        val isOnline = NetworkMonitor.isCurrentlyConnected(this)
        val notification = NotificationHelper.buildBackgroundSyncNotification(this, isOnline)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    3030,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                )
            } else {
                startForeground(3030, notification)
            }
        } catch (_: Exception) {
        }
        attachBackgroundBroadcastListener()
        startOfflineConnectivityMonitor()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (messageListener == null) {
            attachBackgroundBroadcastListener()
        }
        return START_STICKY
    }

    private fun attachBackgroundBroadcastListener() {
        val currentUser = Firebase.auth.currentUser ?: return
        val prefs = SecurityPreferences(this)
        val databaseId = getString(R.string.firestore_database_id)
        val db = FirebaseFirestore.getInstance(databaseId)

        messageListener?.remove()
        messageListener = db.collection("broadcast_messages")
            .whereEqualTo("visibility", "VERIFIED_MEMBERS")
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) return@addSnapshotListener
                if (Firebase.auth.currentUser?.uid != currentUser.uid) return@addSnapshotListener

                val messages = snapshot.toObjects(BroadcastMessage::class.java)
                if (!hasSeededInitialSnapshot) {
                    messages.forEach { msg ->
                        knownMessageSignatures[msg.id] = "${msg.title}|${msg.content}|${msg.isEdited}"
                    }
                    hasSeededInitialSnapshot = true
                    return@addSnapshotListener
                }

                for (msg in messages) {
                    val signature = "${msg.title}|${msg.content}|${msg.isEdited}"
                    val oldSig = knownMessageSignatures[msg.id]
                    if (oldSig == null || oldSig != signature) {
                        knownMessageSignatures[msg.id] = signature
                        if (prefs.isNotificationsEnabled()) {
                            NotificationHelper.showBroadcastNotification(
                                context = applicationContext,
                                isEdited = oldSig != null && msg.isEdited,
                                notificationId = msg.id.hashCode()
                            )
                        }
                    }
                }
            }
    }

    /**
     * Monitors live network connectivity and pushes a reminder notification if the phone
     * stays offline / without mobile data or Wi-Fi for the user's configured interval (default: 1 hour).
     */
    private fun startOfflineConnectivityMonitor() {
        val prefs = SecurityPreferences(applicationContext)
        serviceScope.launch {
            launch {
                NetworkMonitor.observeNetworkConnectivity(applicationContext).collect { connected ->
                    if (connected) {
                        prefs.setOfflineSinceMillis(0L)
                        prefs.setLastOfflineNotificationSentMillis(0L)
                    } else if (prefs.getOfflineSinceMillis() == 0L) {
                        prefs.setOfflineSinceMillis(System.currentTimeMillis())
                    }
                }
            }

            while (isActive) {
                val connected = NetworkMonitor.isCurrentlyConnected(applicationContext)
                val now = System.currentTimeMillis()
                if (connected) {
                    prefs.setOfflineSinceMillis(0L)
                    prefs.setLastOfflineNotificationSentMillis(0L)
                } else {
                    var offlineSince = prefs.getOfflineSinceMillis()
                    if (offlineSince == 0L) {
                        offlineSince = now
                        prefs.setOfflineSinceMillis(now)
                    }
                    val intervalMinutes = prefs.getOfflineReminderIntervalMinutes()
                    val thresholdMillis = intervalMinutes.toLong() * 60_000L
                    val offlineDuration = now - offlineSince
                    val lastSent = prefs.getLastOfflineNotificationSentMillis()

                    if (prefs.isOfflineDataReminderEnabled() &&
                        offlineDuration >= thresholdMillis &&
                        (lastSent == 0L || (now - lastSent) >= thresholdMillis)
                    ) {
                        prefs.setLastOfflineNotificationSentMillis(now)
                        NotificationHelper.showOfflineConnectDataNotification(
                            context = applicationContext,
                            offlineMinutes = intervalMinutes
                        )
                    }
                }
                delay(30_000L)
            }
        }
    }

    override fun onDestroy() {
        messageListener?.remove()
        messageListener = null
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        fun start(context: Context) {
            try {
                val intent = Intent(context, TradingBackgroundService::class.java)
                ContextCompat.startForegroundService(context, intent)
            } catch (_: Exception) {
            }
        }

        fun stop(context: Context) {
            try {
                val intent = Intent(context, TradingBackgroundService::class.java)
                context.stopService(intent)
            } catch (_: Exception) {
            }
        }
    }
}
