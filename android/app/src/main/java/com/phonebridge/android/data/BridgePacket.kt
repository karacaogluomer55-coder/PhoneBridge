package com.phonebridge.android.data

import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.annotations.SerializedName

object PacketTypes {
    const val HANDSHAKE = "HANDSHAKE"
    const val DEVICE_STATUS = "DEVICE_STATUS"
    const val INCOMING_CALL = "INCOMING_CALL"
    const val CALL_ACTION = "CALL_ACTION"
    const val CALL_ENDED = "CALL_ENDED"
    const val NOTIFICATION = "NOTIFICATION"
    const val NOTIFICATION_REPLY = "NOTIFICATION_REPLY"
    const val MEDIA_STATE_UPDATE = "MEDIA_STATE_UPDATE"
    const val MEDIA_COMMAND = "MEDIA_COMMAND"
    const val SMS_CONVERSATIONS = "SMS_CONVERSATIONS"
    const val SEND_SMS = "SEND_SMS"
    const val PING = "PING"
    const val PONG = "PONG"

    const val MOUSE_MOVE = "MOUSE_MOVE"
    const val MOUSE_CLICK = "MOUSE_CLICK"
    const val MOUSE_SCROLL = "MOUSE_SCROLL"
    const val KEYBOARD_INPUT = "KEYBOARD_INPUT"
    const val CLIPBOARD_UPDATE = "CLIPBOARD_UPDATE"
    const val REMOTE_COMMAND = "REMOTE_COMMAND"
    const val RING_PHONE = "RING_PHONE"
    const val RING_PC = "RING_PC"

    const val PAIR_REQUEST = "PAIR_REQUEST"
    const val PAIR_ACCEPT = "PAIR_ACCEPT"
    const val PAIR_REJECT = "PAIR_REJECT"
    const val UNPAIR = "UNPAIR"
}

data class BridgePacket(
    @SerializedName("type")
    val type: String,

    @SerializedName("payload")
    val payload: JsonElement? = null,

    @SerializedName("timestamp")
    val timestamp: Long = System.currentTimeMillis()
) {
    companion object {
        private val gson = Gson()

        fun <T> create(type: String, payloadData: T): BridgePacket {
            val jsonTree = gson.toJsonTree(payloadData)
            return BridgePacket(
                type = type,
                payload = jsonTree,
                timestamp = System.currentTimeMillis()
            )
        }

        fun fromJson(json: String): BridgePacket? {
            return try {
                gson.fromJson(json, BridgePacket::class.java)
            } catch (e: Exception) {
                null
            }
        }

        fun toJson(packet: BridgePacket): String {
            return gson.toJson(packet)
        }
    }
}