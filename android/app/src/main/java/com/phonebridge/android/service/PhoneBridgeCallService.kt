package com.phonebridge.android.service

import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.provider.ContactsContract
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.InCallService
import android.telecom.VideoProfile
import android.util.Log
import com.google.gson.Gson
import com.phonebridge.android.data.CallActionPayload
import com.phonebridge.android.data.IncomingCallPayload
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
import java.net.URLDecoder

class PhoneBridgeCallService : InCallService() {

    companion object {
        private const val TAG = "PhoneBridgeCallService"

        @Volatile
        var activeCall: Call? = null
            private set

        private val _currentCallState = MutableStateFlow<String?>("IDLE")
        val currentCallState: StateFlow<String?> = _currentCallState.asStateFlow()

        private val _callerInfo = MutableStateFlow<Pair<String, String>?>(null)
        val callerInfo: StateFlow<Pair<String, String>?> = _callerInfo.asStateFlow()

        fun answerCall() {
            activeCall?.let {
                try {
                    it.answer(VideoProfile.STATE_AUDIO_ONLY)
                    Log.i(TAG, "Call answered via Telecom")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to answer call: ${e.message}")
                }
            }
        }

        fun rejectCall() {
            activeCall?.let {
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        it.reject(Call.REJECT_REASON_DECLINED)
                    } else {
                        it.reject(false, null)
                    }
                    Log.i(TAG, "Call rejected via Telecom")
                } catch (e: Exception) {
                    try { it.disconnect() } catch (ex: Exception) { }
                    Log.e(TAG, "Failed to reject call: ${e.message}")
                }
            }
        }

        fun endCall() {
            activeCall?.let {
                try {
                    it.disconnect()
                    Log.i(TAG, "Call disconnected via Telecom")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to disconnect call: ${e.message}")
                }
            }
        }

        fun setAudioRoute(route: String) {
            instance?.applyAudioRoute(route)
        }

        fun setMute(mute: Boolean) {
            instance?.setMuted(mute)
        }

        @Volatile
        private var instance: PhoneBridgeCallService? = null
    }

    fun applyAudioRoute(route: String) {
        try {
            if (route.equals("PC", ignoreCase = true) || route.equals("Bluetooth", ignoreCase = true)) {
                setAudioRoute(CallAudioState.ROUTE_BLUETOOTH)
                Log.i(TAG, "Audio route set to BLUETOOTH")
            } else if (route.equals("Speaker", ignoreCase = true)) {
                setAudioRoute(CallAudioState.ROUTE_SPEAKER)
                Log.i(TAG, "Audio route set to SPEAKER")
            } else {
                setAudioRoute(CallAudioState.ROUTE_EARPIECE)
                Log.i(TAG, "Audio route set to EARPIECE")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set audio route: ${e.message}")
        }
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var wsListenerJob: Job? = null
    private val gson = Gson()

    private val callCallback = object : Call.Callback() {
        override fun onStateChanged(call: Call, state: Int) {
            super.onStateChanged(call, state)
            Log.d(TAG, "Call state changed: $state")

            when (state) {
                Call.STATE_RINGING -> {
                    _currentCallState.value = "RINGING"
                    dispatchIncomingCallPacket(call)
                }
                Call.STATE_ACTIVE -> {
                    _currentCallState.value = "ACTIVE"
                    val client = PhoneBridgeWebSocketClient.getInstance(applicationContext)
                    client.sendPacket(PacketTypes.CALL_ACTION, CallActionPayload("ACCEPT", "PC"))
                }
                Call.STATE_DISCONNECTED -> {
                    _currentCallState.value = "DISCONNECTED"
                    val client = PhoneBridgeWebSocketClient.getInstance(applicationContext)
                    client.sendPacket(PacketTypes.CALL_ACTION, CallActionPayload("END", null))
                    client.sendPacket(PacketTypes.CALL_ENDED, mapOf("reason" to "DISCONNECTED"))
                    activeCall = null
                    _callerInfo.value = null
                }
                Call.STATE_HOLDING -> {
                    _currentCallState.value = "HOLDING"
                }
                Call.STATE_DIALING -> {
                    _currentCallState.value = "DIALING"
                }
            }
        }
    }

    override fun onCallAudioStateChanged(audioState: CallAudioState) {
        super.onCallAudioStateChanged(audioState)
        Log.d(TAG, "Audio state: route=${audioState.route}, isMuted=${audioState.isMuted}")
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        Log.i(TAG, "PhoneBridgeCallService created")
        listenToWebSocketCommands()
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        wsListenerJob?.cancel()
        Log.i(TAG, "PhoneBridgeCallService destroyed")
    }

    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)
        Log.i(TAG, "Telecom onCallAdded: state=${call.state}")
        activeCall = call
        call.registerCallback(callCallback)

        if (call.state == Call.STATE_RINGING) {
            _currentCallState.value = "RINGING"
            dispatchIncomingCallPacket(call)
        } else if (call.state == Call.STATE_ACTIVE) {
            _currentCallState.value = "ACTIVE"
        }
    }

    override fun onCallRemoved(call: Call) {
        super.onCallRemoved(call)
        Log.i(TAG, "Telecom onCallRemoved")
        call.unregisterCallback(callCallback)
        if (activeCall == call) {
            activeCall = null
            _currentCallState.value = "IDLE"
            _callerInfo.value = null
            val client = PhoneBridgeWebSocketClient.getInstance(applicationContext)
            client.sendPacket(PacketTypes.CALL_ACTION, CallActionPayload("END", null))
            client.sendPacket(PacketTypes.CALL_ENDED, mapOf("reason" to "REMOVED"))
        }
    }

    private fun dispatchIncomingCallPacket(call: Call) {
        scope.launch {
            val handle = call.details?.handle
            var number = ""
            if (handle != null) {
                try {
                    val schemeSpecific = handle.schemeSpecificPart
                    number = URLDecoder.decode(schemeSpecific, "UTF-8")
                } catch (e: Exception) {
                    number = handle.toString()
                }
            }

            var name = call.details?.callerDisplayName
            if (name.isNullOrBlank()) {
                name = resolveContactName(number)
            }
            if (name.isNullOrBlank()) {
                name = if (number.isNotBlank()) number else "Gizli / Bilinmeyen Numara"
            }

            _callerInfo.value = Pair(name, number)

            val callId = call.details?.let {
                "${it.creationTimeMillis}_${number.hashCode()}"
            } ?: System.currentTimeMillis().toString()

            val payload = IncomingCallPayload(
                callId = callId,
                callerName = name,
                callerNumber = number
            )

            Log.i(TAG, "Sending INCOMING_CALL packet: $name ($number)")
            val client = PhoneBridgeWebSocketClient.getInstance(applicationContext)
            client.sendPacket(PacketTypes.INCOMING_CALL, payload)
        }
    }

    private fun resolveContactName(phoneNumber: String): String? {
        if (phoneNumber.isBlank()) return null
        return try {
            val uri = Uri.withAppendedPath(
                ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                Uri.encode(phoneNumber)
            )
            val projection = arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME)
            val cursor: Cursor? = contentResolver.query(uri, projection, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    val nameIndex = it.getColumnIndex(ContactsContract.PhoneLookup.DISPLAY_NAME)
                    if (nameIndex != -1) {
                        return it.getString(nameIndex)
                    }
                }
            }
            null
        } catch (e: Exception) {
            Log.w(TAG, "Could not resolve contact name: ${e.message}")
            null
        }
    }

    private fun listenToWebSocketCommands() {
        val client = PhoneBridgeWebSocketClient.getInstance(applicationContext)
        wsListenerJob = scope.launch {
            client.packetFlow.collect { packet ->
                if (packet.type == PacketTypes.CALL_ACTION) {
                    handleCallActionFromPc(packet)
                }
            }
        }
    }

    private fun handleCallActionFromPc(packet: com.phonebridge.android.data.BridgePacket) {
        val payload = packet.payload ?: return
        try {
            val actionPayload = gson.fromJson(payload, CallActionPayload::class.java)
            val action = actionPayload.action.uppercase()
            val route = actionPayload.route ?: "PC"

            Log.i(TAG, "Received CALL_ACTION from PC: action=$action, route=$route")

            when (action) {
                "ACCEPT" -> {
                    answerCall()
                    if (route.isNotBlank()) {
                        setAudioRoute(route)
                    }
                }
                "REJECT" -> {
                    rejectCall()
                }
                "END" -> {
                    endCall()
                }
                "ROUTE_CHANGE" -> {
                    setAudioRoute(route)
                }
                "MUTE" -> {
                    setMuted(true)
                }
                "UNMUTE" -> {
                    setMuted(false)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse call action: ${e.message}")
        }
    }
}