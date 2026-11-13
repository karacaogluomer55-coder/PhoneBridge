package com.phonebridge.android.service

import android.content.ComponentName
import android.content.Context
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.google.gson.Gson
import com.phonebridge.android.data.MediaCommandPayload
import com.phonebridge.android.data.MediaStatePayload
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

class PhoneBridgeMediaController(private val context: Context) {

    companion object {
        private const val TAG = "PhoneBridgeMedia"
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val mainHandler = Handler(Looper.getMainLooper())
    private val gson = Gson()

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val mediaSessionManager =
        context.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager

    private var activeController: MediaController? = null
    private var wsListenerJob: Job? = null

    private val _currentMediaState = MutableStateFlow<MediaStatePayload?>(null)
    val currentMediaState: StateFlow<MediaStatePayload?> = _currentMediaState.asStateFlow()

    private val controllerCallback = object : MediaController.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackState?) {
            super.onPlaybackStateChanged(state)
            dispatchMediaUpdate()
        }

        override fun onMetadataChanged(metadata: MediaMetadata?) {
            super.onMetadataChanged(metadata)
            dispatchMediaUpdate()
        }
    }

    private val sessionsChangedListener =
        MediaSessionManager.OnActiveSessionsChangedListener { controllers ->
            Log.d(TAG, "Active media sessions count: ${controllers?.size ?: 0}")
            updateActiveController(controllers?.firstOrNull())
        }

    fun start() {
        listenToWebSocketCommands()
        bindActiveSessionListener()
    }

    fun stop() {
        wsListenerJob?.cancel()
        try {
            mediaSessionManager.removeOnActiveSessionsChangedListener(sessionsChangedListener)
        } catch (e: Exception) {
        }
        activeController?.unregisterCallback(controllerCallback)
        activeController = null
    }

    private fun bindActiveSessionListener() {
        try {
            val componentName = ComponentName(context, PhoneBridgeNotificationListener::class.java)
            mediaSessionManager.addOnActiveSessionsChangedListener(
                sessionsChangedListener,
                componentName,
                mainHandler
            )
            val controllers = mediaSessionManager.getActiveSessions(componentName)
            updateActiveController(controllers.firstOrNull())
        } catch (e: SecurityException) {
            Log.w(TAG, "Notification listener permission needed for MediaSession access: ${e.message}")
        } catch (e: Exception) {
            Log.e(TAG, "Error binding media sessions: ${e.message}")
        }
    }

    private fun updateActiveController(newController: MediaController?) {
        activeController?.unregisterCallback(controllerCallback)
        activeController = newController
        activeController?.registerCallback(controllerCallback, mainHandler)
        dispatchMediaUpdate()
    }

    private fun dispatchMediaUpdate() {
        scope.launch {
            val controller = activeController
            val metadata = controller?.metadata
            val playbackState = controller?.playbackState

            val title = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE)
                ?: metadata?.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE)
                ?: "Şu anda parça çalmıyor"

            val artist = metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST)
                ?: metadata?.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
                ?: "Bilinmeyen Sanatçı"

            val album = metadata?.getString(MediaMetadata.METADATA_KEY_ALBUM)
                ?: metadata?.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE)
                ?: ""
            val duration = metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L

            val albumArtBitmap = metadata?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
                ?: metadata?.getBitmap(MediaMetadata.METADATA_KEY_ART)

            var albumArtBase64: String? = null
            if (albumArtBitmap != null) {
                try {
                    val maxDimension = 240
                    val scaled = if (albumArtBitmap.width > maxDimension || albumArtBitmap.height > maxDimension) {
                        val ratio = maxDimension.toFloat() / maxOf(albumArtBitmap.width, albumArtBitmap.height)
                        android.graphics.Bitmap.createScaledBitmap(
                            albumArtBitmap,
                            (albumArtBitmap.width * ratio).toInt(),
                            (albumArtBitmap.height * ratio).toInt(),
                            true
                        )
                    } else {
                        albumArtBitmap
                    }
                    val stream = java.io.ByteArrayOutputStream()
                    scaled.compress(android.graphics.Bitmap.CompressFormat.JPEG, 75, stream)
                    albumArtBase64 = android.util.Base64.encodeToString(stream.toByteArray(), android.util.Base64.NO_WRAP)
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to encode album art: ${e.message}")
                }
            }

            val isPlaying = playbackState?.state == PlaybackState.STATE_PLAYING
            val position = playbackState?.position ?: 0L

            val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
            val curVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
            val volumePct = (curVolume * 100) / maxVolume

            val sourceApp = controller?.packageName ?: "Android Media"

            val payload = MediaStatePayload(
                title = title,
                artist = artist,
                album = album,
                sourceApp = sourceApp,
                isPlaying = isPlaying,
                position = position,
                duration = duration,
                volume = volumePct,
                albumArtBase64 = albumArtBase64,
                canSkipNext = true,
                canSkipPrevious = true,
                canPause = true,
                canPlay = true
            )

            _currentMediaState.value = payload

            val client = PhoneBridgeWebSocketClient.getInstance(context)
            client.sendPacket(PacketTypes.MEDIA_STATE_UPDATE, payload)
        }
    }

    private fun listenToWebSocketCommands() {
        val client = PhoneBridgeWebSocketClient.getInstance(context)
        wsListenerJob = scope.launch {
            client.packetFlow.collect { packet ->
                if (packet.type == PacketTypes.MEDIA_COMMAND) {
                    handleMediaCommand(packet)
                }
            }
        }
    }

    private fun handleMediaCommand(packet: com.phonebridge.android.data.BridgePacket) {
        val payloadElement = packet.payload ?: return
        try {
            val cmdPayload = gson.fromJson(payloadElement, MediaCommandPayload::class.java)
            val command = cmdPayload.command.uppercase()
            val value = cmdPayload.value

            Log.i(TAG, "Received MEDIA_COMMAND: $command, value=$value")

            mainHandler.post {
                val controls = activeController?.transportControls
                when (command) {
                    "PLAY" -> controls?.play()
                    "PAUSE" -> controls?.pause()
                    "PLAY_PAUSE" -> {
                        val isPlaying = activeController?.playbackState?.state == PlaybackState.STATE_PLAYING
                        if (isPlaying) {
                            controls?.pause()
                        } else {
                            controls?.play()
                        }
                    }
                    "NEXT" -> controls?.skipToNext()
                    "PREV" -> controls?.skipToPrevious()
                    "SET_VOLUME" -> {
                        if (value != null) {
                            val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                            val targetIndex = (value * maxVolume / 100).coerceIn(0, maxVolume)
                            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, targetIndex, 0)
                            dispatchMediaUpdate()
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error processing media command: ${e.message}")
        }
    }
}