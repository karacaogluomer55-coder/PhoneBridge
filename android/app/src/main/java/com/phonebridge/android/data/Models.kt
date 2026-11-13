package com.phonebridge.android.data

import com.google.gson.annotations.SerializedName

enum class ConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    ERROR
}

data class DeviceStatusPayload(
    @SerializedName("deviceId")
    val deviceId: String,

    @SerializedName("deviceName")
    val deviceName: String,

    @SerializedName("deviceModel")
    val deviceModel: String,

    @SerializedName("operatingSystem")
    val operatingSystem: String,

    @SerializedName("battery")
    val battery: Int,

    @SerializedName("isCharging")
    val isCharging: Boolean,

    @SerializedName("wifiSignalLevel")
    val wifiSignalLevel: Int = 4,

    @SerializedName("ipAddress")
    val ipAddress: String = ""
)

data class IncomingCallPayload(
    @SerializedName("callId")
    val callId: String,

    @SerializedName("callerName")
    val callerName: String,

    @SerializedName("callerNumber")
    val callerNumber: String
)

data class CallActionPayload(
    @SerializedName("action")
    val action: String, // ACCEPT, REJECT, END, ROUTE_CHANGE, MUTE, UNMUTE

    @SerializedName("route")
    val route: String? = null // PC, Phone
)

data class NotificationPayload(
    @SerializedName("id")
    val id: String,

    @SerializedName("appName")
    val appName: String,

    @SerializedName("packageName")
    val packageName: String,

    @SerializedName("title")
    val title: String,

    @SerializedName("sender")
    val sender: String = "",

    @SerializedName("content")
    val content: String,

    @SerializedName("appIconBase64")
    val appIconBase64: String? = null,

    @SerializedName("timestamp")
    val timestamp: Long = System.currentTimeMillis(),

    @SerializedName("canReply")
    val canReply: Boolean = true
)

data class NotificationReplyPayload(
    @SerializedName("id")
    val id: String? = null,

    @SerializedName("sender")
    val sender: String? = null,

    @SerializedName("replyText")
    val replyText: String,

    @SerializedName("timestamp")
    val timestamp: Long? = null
)

data class MediaStatePayload(
    @SerializedName("title")
    val title: String,

    @SerializedName("artist")
    val artist: String,

    @SerializedName("album")
    val album: String = "",

    @SerializedName("sourceApp")
    val sourceApp: String = "",

    @SerializedName("isPlaying")
    val isPlaying: Boolean = false,

    @SerializedName("position")
    val position: Long = 0,

    @SerializedName("duration")
    val duration: Long = 0,

    @SerializedName("volume")
    val volume: Int = 50,

    @SerializedName("albumArtUrl")
    val albumArtUrl: String? = null,

    @SerializedName("albumArtBase64")
    val albumArtBase64: String? = null,

    @SerializedName("canSkipNext")
    val canSkipNext: Boolean = true,

    @SerializedName("canSkipPrevious")
    val canSkipPrevious: Boolean = true,

    @SerializedName("canPause")
    val canPause: Boolean = true,

    @SerializedName("canPlay")
    val canPlay: Boolean = true
)

data class MediaCommandPayload(
    @SerializedName("command")
    val command: String, // PLAY, PAUSE, PLAY_PAUSE, NEXT, PREV, SET_VOLUME

    @SerializedName("value")
    val value: Int? = null
)

data class DiscoveredPc(
    val ip: String,
    val name: String,
    val port: Int,
    val lastSeenTimestamp: Long = System.currentTimeMillis()
)

data class MouseMovePayload(
    @SerializedName("dx")
    val dx: Float,

    @SerializedName("dy")
    val dy: Float
)

data class MouseClickPayload(
    @SerializedName("button")
    val button: String, // "LEFT", "RIGHT", "MIDDLE"

    @SerializedName("action")
    val action: String = "CLICK" // "CLICK", "DOWN", "UP", "DOUBLE_CLICK"
)

data class MouseScrollPayload(
    @SerializedName("deltaY")
    val deltaY: Float,

    @SerializedName("deltaX")
    val deltaX: Float = 0f
)

data class KeyboardInputPayload(
    @SerializedName("key")
    val key: String? = null,

    @SerializedName("text")
    val text: String? = null,

    @SerializedName("modifiers")
    val modifiers: List<String> = emptyList()
)

data class ClipboardPayload(
    @SerializedName("text")
    val text: String,

    @SerializedName("timestamp")
    val timestamp: Long = System.currentTimeMillis()
)

data class RemoteCommandPayload(
    @SerializedName("command")
    val command: String,

    @SerializedName("param")
    val param: String? = null
)

data class RingActionPayload(
    @SerializedName("action")
    val action: String = "START"
)

data class PairedDevice(
    @SerializedName("id")
    val id: String,

    @SerializedName("name")
    val name: String,

    @SerializedName("ip")
    val ip: String,

    @SerializedName("port")
    val port: Int = 8765,

    @SerializedName("pairedTimestamp")
    val pairedTimestamp: Long = System.currentTimeMillis()
)

data class PairRequestPayload(
    @SerializedName("deviceId")
    val deviceId: String,

    @SerializedName("deviceName")
    val deviceName: String,

    @SerializedName("deviceType")
    val deviceType: String = "phone"
)

data class PairResponsePayload(
    @SerializedName("accepted")
    val accepted: Boolean,

    @SerializedName("deviceId")
    val deviceId: String,

    @SerializedName("deviceName")
    val deviceName: String = "",

    @SerializedName("reason")
    val reason: String = ""
)

data class SmsConversation(
    @SerializedName("threadId")
    val threadId: Long = 0,

    @SerializedName("contactName")
    val contactName: String = "",

    @SerializedName("phoneNumber")
    val phoneNumber: String = "",

    @SerializedName("lastMessage")
    val lastMessage: String = "",

    @SerializedName("timestamp")
    val timestamp: Long = System.currentTimeMillis(),

    @SerializedName("isRead")
    val isRead: Boolean = true
)

data class SendSmsPayload(
    @SerializedName("phoneNumber")
    val phoneNumber: String,

    @SerializedName("message")
    val message: String
)