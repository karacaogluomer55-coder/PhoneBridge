package com.phonebridge.android.service

import android.app.Notification
import android.app.PendingIntent
import android.app.RemoteInput
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.telephony.SmsManager
import android.util.Base64
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.gson.Gson
import com.phonebridge.android.data.NotificationPayload
import com.phonebridge.android.data.NotificationReplyPayload
import com.phonebridge.android.data.PacketTypes
import com.phonebridge.android.network.PhoneBridgeWebSocketClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.util.concurrent.ConcurrentHashMap

class PhoneBridgeNotificationListener : NotificationListenerService() {

    companion object {
        private const val TAG = "NotificationListener"

        data class ReplyAction(
            val pendingIntent: PendingIntent,
            val remoteInput: RemoteInput
        )

        private val replyActionCache = ConcurrentHashMap<String, ReplyAction>()

        private val _lastNotification = MutableStateFlow<NotificationPayload?>(null)
        val lastNotification: StateFlow<NotificationPayload?> = _lastNotification.asStateFlow()

        @Volatile
        var isListenerConnected: Boolean = false
            private set
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var wsListenerJob: Job? = null
    private val gson = Gson()

    override fun onListenerConnected() {
        super.onListenerConnected()
        isListenerConnected = true
        Log.i(TAG, "NotificationListener connected successfully")
        listenToWebSocketReplies()
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        isListenerConnected = false
        wsListenerJob?.cancel()
        Log.w(TAG, "NotificationListener disconnected")
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null) return

        val pkg = sbn.packageName
        if (pkg == packageName) return

        val notification = sbn.notification ?: return

        val isOngoing = (notification.flags and Notification.FLAG_ONGOING_EVENT) != 0
        val isForegroundService = (notification.flags and Notification.FLAG_FOREGROUND_SERVICE) != 0

        val extras = notification.extras ?: Bundle()
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
            ?: extras.getCharSequence(Notification.EXTRA_TITLE_BIG)?.toString()
            ?: ""

        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
            ?: extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()
            ?: extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString()
            ?: ""

        if (title.isBlank() && text.isBlank()) return

        if (isOngoing && (pkg.contains("android") || pkg.contains("systemui"))) {
            return
        }

        val appName = getAppName(pkg)
        val notifKey = sbn.key ?: "${pkg}_${sbn.id}"

        val sender = extras.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE)?.toString()
            ?: extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
            ?: extras.getCharSequence("android.title")?.toString()
            ?: title

        val appIconBase64 = extractAppIconBase64(pkg, notification)

        val replyAction = extractReplyAction(notification)
        var canReply = false
        if (replyAction != null) {
            replyActionCache[notifKey] = replyAction
            canReply = true
        }

        val payload = NotificationPayload(
            id = notifKey,
            appName = appName,
            packageName = pkg,
            title = title,
            sender = sender,
            content = text,
            appIconBase64 = appIconBase64,
            timestamp = sbn.postTime,
            canReply = canReply
        )

        _lastNotification.value = payload

        Log.d(TAG, "Extracted notification: [$appName] Sender: $sender | $title: $text (canReply=$canReply, hasIcon=${appIconBase64 != null})")

        val client = PhoneBridgeWebSocketClient.getInstance(applicationContext)
        client.sendPacket(PacketTypes.NOTIFICATION, payload)
    }

    private fun extractAppIconBase64(pkg: String, notification: Notification): String? {
        return try {
            val largeIcon = notification.getLargeIcon()
            val bmp = if (largeIcon != null) {
                val drawable = largeIcon.loadDrawable(this)
                drawable?.let { drawableToBitmap(it) }
            } else null

            val finalBmp = bmp ?: run {
                val pm = packageManager
                val appInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    pm.getApplicationInfo(pkg, PackageManager.ApplicationInfoFlags.of(0))
                } else {
                    @Suppress("DEPRECATION")
                    pm.getApplicationInfo(pkg, 0)
                }
                val drawable = pm.getApplicationIcon(appInfo)
                drawableToBitmap(drawable)
            }

            finalBmp?.let { b ->
                val scaled = if (b.width > 96 || b.height > 96) {
                    Bitmap.createScaledBitmap(b, 96, 96, true)
                } else b
                val output = ByteArrayOutputStream()
                scaled.compress(Bitmap.CompressFormat.PNG, 85, output)
                Base64.encodeToString(output.toByteArray(), Base64.NO_WRAP)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to extract notification icon: ${e.message}")
            null
        }
    }

    private fun drawableToBitmap(drawable: Drawable): Bitmap {
        if (drawable is BitmapDrawable && drawable.bitmap != null) {
            return drawable.bitmap
        }
        val width = if (drawable.intrinsicWidth > 0) drawable.intrinsicWidth else 96
        val height = if (drawable.intrinsicHeight > 0) drawable.intrinsicHeight else 96
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        return bitmap
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        if (sbn == null) return
        val notifKey = sbn.key ?: "${sbn.packageName}_${sbn.id}"
        replyActionCache.remove(notifKey)
    }

    private fun extractReplyAction(notification: Notification): ReplyAction? {
        val actions = notification.actions ?: return null

        for (action in actions) {
            val remoteInputs = action.remoteInputs ?: continue
            for (input in remoteInputs) {
                if (input.allowFreeFormInput) {
                    val pendingIntent = action.actionIntent ?: continue
                    return ReplyAction(pendingIntent, input)
                }
            }
        }
        return null
    }

    private fun listenToWebSocketReplies() {
        val client = PhoneBridgeWebSocketClient.getInstance(applicationContext)
        wsListenerJob = scope.launch {
            client.packetFlow.collect { packet ->
                if (packet.type == PacketTypes.NOTIFICATION_REPLY) {
                    handleNotificationReply(packet)
                }
            }
        }
    }

    private fun handleNotificationReply(packet: com.phonebridge.android.data.BridgePacket) {
        val payloadElement = packet.payload ?: return
        try {
            val replyPayload = gson.fromJson(payloadElement, NotificationReplyPayload::class.java)
            val notifId = replyPayload.id
            val replyText = replyPayload.replyText

            if (replyText.isBlank()) return

            if (!notifId.isNullOrBlank() && replyActionCache.containsKey(notifId)) {
                val action = replyActionCache[notifId]
                if (action != null) {
                    val intent = Intent()
                    val bundle = Bundle()
                    bundle.putCharSequence(action.remoteInput.resultKey, replyText)
                    RemoteInput.addResultsToIntent(arrayOf(action.remoteInput), intent, bundle)

                    action.pendingIntent.send(this, 0, intent)
                    Log.i(TAG, "Sent inline reply to notification [$notifId]: '$replyText'")
                    return
                }
            }

            val targetNumber = replyPayload.sender ?: notifId
            if (!targetNumber.isNullOrBlank() && targetNumber.any { it.isDigit() }) {
                try {
                    val smsManager = getSystemService(SmsManager::class.java)
                    smsManager?.sendTextMessage(targetNumber, null, replyText, null, null)
                    Log.i(TAG, "Sent SMS fallback to $targetNumber: '$replyText'")
                } catch (smsEx: Exception) {
                    Log.e(TAG, "SMS fallback failed: ${smsEx.message}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error handling notification reply: ${e.message}")
        }
    }

    private fun getAppName(packageName: String): String {
        return try {
            val pm = packageManager
            val info = pm.getApplicationInfo(packageName, 0)
            pm.getApplicationLabel(info).toString()
        } catch (e: Exception) {
            packageName.substringAfterLast('.')
        }
    }
}