package com.phonebridge.android.service

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import com.google.gson.Gson
import com.phonebridge.android.data.ClipboardPayload
import com.phonebridge.android.data.PacketTypes
import com.phonebridge.android.network.PhoneBridgeWebSocketClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ClipboardSyncManager(
    private val context: Context,
    private val wsClient: PhoneBridgeWebSocketClient
) {
    companion object {
        private const val TAG = "ClipboardSyncManager"

        @Volatile
        private var INSTANCE: ClipboardSyncManager? = null

        fun getInstance(context: Context, wsClient: PhoneBridgeWebSocketClient): ClipboardSyncManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: ClipboardSyncManager(context.applicationContext, wsClient).also {
                    INSTANCE = it
                }
            }
        }
    }

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    private val gson = Gson()
    private val mainHandler = Handler(Looper.getMainLooper())

    private val _lastReceivedText = MutableStateFlow<String?>(null)
    val lastReceivedText: StateFlow<String?> = _lastReceivedText.asStateFlow()

    private val _lastSentText = MutableStateFlow<String?>(null)
    val lastSentText: StateFlow<String?> = _lastSentText.asStateFlow()

    init {
        listenForIncomingClipboardUpdates()
    }

    private fun listenForIncomingClipboardUpdates() {
        scope.launch {
            wsClient.packetFlow.collect { packet ->
                if (packet.type == PacketTypes.CLIPBOARD_UPDATE) {
                    try {
                        val payload = if (packet.payload != null) {
                            gson.fromJson(packet.payload, ClipboardPayload::class.java)
                        } else null

                        payload?.text?.let { receivedText ->
                            handleIncomingClipboardText(receivedText)
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error parsing incoming clipboard payload: ${e.message}")
                    }
                }
            }
        }
    }

    private fun handleIncomingClipboardText(text: String) {
        mainHandler.post {
            try {
                val clip = ClipData.newPlainText("PhoneBridge Clipboard", text)
                clipboard?.setPrimaryClip(clip)
                _lastReceivedText.value = text

                val preview = if (text.length > 40) text.take(40) + "…" else text
                Toast.makeText(context, "Panoya PC'den kopyalandı:\n\"$preview\"", Toast.LENGTH_SHORT).show()
                Log.i(TAG, "PC clipboard successfully copied to Android: $preview")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to set primary clip: ${e.message}")
            }
        }
    }

    fun sendClipboardToPc(): Pair<Boolean, String> {
        val currentText = getCurrentClipboardText()
        if (currentText.isNullOrBlank()) {
            return Pair(false, "Pano boş veya okunamadı.")
        }

        val payload = ClipboardPayload(
            text = currentText,
            timestamp = System.currentTimeMillis()
        )

        val sent = wsClient.sendPacket(PacketTypes.CLIPBOARD_UPDATE, payload)
        if (sent) {
            _lastSentText.value = currentText
            val preview = if (currentText.length > 40) currentText.take(40) + "…" else currentText
            Toast.makeText(context, "Pano PC'ye gönderildi:\n\"$preview\"", Toast.LENGTH_SHORT).show()
            return Pair(true, "Pano PC'ye başarıyla iletildi.")
        } else {
            return Pair(false, "PC'ye bağlı değil.")
        }
    }

    fun getCurrentClipboardText(): String? {
        return try {
            val clip = clipboard?.primaryClip
            if (clip != null && clip.itemCount > 0) {
                clip.getItemAt(0)?.coerceToText(context)?.toString()
            } else null
        } catch (e: Exception) {
            Log.e(TAG, "Could not read clipboard: ${e.message}")
            null
        }
    }

    fun copyToClipboard(text: String) {
        try {
            val clip = ClipData.newPlainText("PhoneBridge", text)
            clipboard?.setPrimaryClip(clip)
            Toast.makeText(context, "Panoya kopyalandı", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to copy text: ${e.message}")
        }
    }
}