package com.phonebridge.android.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.gson.Gson
import com.phonebridge.android.MainActivity
import com.phonebridge.android.R
import com.phonebridge.android.data.PacketTypes
import com.phonebridge.android.data.RingActionPayload
import com.phonebridge.android.network.PhoneBridgeWebSocketClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class PhoneFinderManager private constructor(
    private val context: Context,
    private val wsClient: PhoneBridgeWebSocketClient
) {
    companion object {
        private const val TAG = "PhoneFinderManager"
        private const val NOTIFICATION_ID = 9999
        private const val CHANNEL_ID = "phone_finder_alarm_channel"
        const val ACTION_STOP_RINGING = "com.phonebridge.android.ACTION_STOP_RINGING"

        @Volatile
        private var INSTANCE: PhoneFinderManager? = null

        fun getInstance(context: Context, wsClient: PhoneBridgeWebSocketClient): PhoneFinderManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: PhoneFinderManager(context.applicationContext, wsClient).also {
                    INSTANCE = it
                }
            }
        }
    }

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val gson = Gson()
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    private var mediaPlayer: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var previousVolume: Int? = null

    private val _isRinging = MutableStateFlow(false)
    val isRinging: StateFlow<Boolean> = _isRinging.asStateFlow()

    init {
        createNotificationChannel()
        listenForIncomingPackets()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Telefon Bulma Alarmı",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "PC tarafından tetiklenen telefon çaldırma bildirimleri"
                enableVibration(true)
            }
            val manager = context.getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun listenForIncomingPackets() {
        scope.launch {
            wsClient.packetFlow.collect { packet ->
                if (packet.type == PacketTypes.RING_PHONE) {
                    try {
                        val payload = if (packet.payload != null) {
                            gson.fromJson(packet.payload, RingActionPayload::class.java)
                        } else null

                        val action = payload?.action?.uppercase() ?: "START"
                        if (action == "STOP") {
                            stopRinging(notifyPc = false)
                        } else {
                            startRinging()
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error handling RING_PHONE packet: ${e.message}")
                    }
                }
            }
        }
    }

    @Synchronized
    fun startRinging() {
        if (_isRinging.value) return
        _isRinging.value = true
        Log.i(TAG, "Starting Find My Phone alarm!")

        try {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            wakeLock = powerManager?.newWakeLock(
                PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
                "phonebridge:find_my_phone_ring"
            )?.apply {
                acquire(60000L)
            }

            audioManager?.let { am ->
                val stream = AudioManager.STREAM_ALARM
                previousVolume = am.getStreamVolume(stream)
                val maxVol = am.getStreamMaxVolume(stream)
                am.setStreamVolume(stream, maxVol, 0)
            }

            val alertUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)

            mediaPlayer?.release()
            mediaPlayer = MediaPlayer().apply {
                setDataSource(context, alertUri)
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                isLooping = true
                prepare()
                start()
            }

            startVibration()
            showAlarmNotification()

        } catch (e: Exception) {
            Log.e(TAG, "Failed to start phone ring: ${e.message}", e)
        }
    }

    private fun startVibration() {
        try {
            vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vm?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }

            val pattern = longArrayOf(0, 500, 200, 500, 200)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createWaveform(pattern, 0))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(pattern, 0)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start vibrator: ${e.message}")
        }
    }

    private fun showAlarmNotification() {
        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openPendingIntent = PendingIntent.getActivity(
            context,
            101,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(context, MainActivity::class.java).apply {
            action = ACTION_STOP_RINGING
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val stopPendingIntent = PendingIntent.getActivity(
            context,
            102,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("🚨 Telefon Çalıyor!")
            .setContentText("Bilgisayardan 'Telefonumu Bul' tetiklendi.")
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setContentIntent(openPendingIntent)
            .addAction(R.drawable.ic_launcher_foreground, "Sesi Durdur", stopPendingIntent)
            .build()

        val notificationManager = context.getSystemService(NotificationManager::class.java)
        notificationManager?.notify(NOTIFICATION_ID, notification)
    }

    @Synchronized
    fun stopRinging(notifyPc: Boolean = true) {
        if (!_isRinging.value) return
        _isRinging.value = false
        Log.i(TAG, "Stopping Find My Phone alarm.")

        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
            mediaPlayer = null

            vibrator?.cancel()
            vibrator = null

            previousVolume?.let { vol ->
                audioManager?.setStreamVolume(AudioManager.STREAM_ALARM, vol, 0)
                previousVolume = null
            }

            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
            wakeLock = null

            val notificationManager = context.getSystemService(NotificationManager::class.java)
            notificationManager?.cancel(NOTIFICATION_ID)

            if (notifyPc) {
                wsClient.sendPacket(PacketTypes.RING_PHONE, RingActionPayload("STOP"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping ring: ${e.message}")
        }
    }
}