package com.phonebridge.android.service

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.provider.ContactsContract
import android.telephony.SmsManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.google.gson.Gson
import com.phonebridge.android.data.PacketTypes
import com.phonebridge.android.data.SendSmsPayload
import com.phonebridge.android.data.SmsConversation
import com.phonebridge.android.network.PhoneBridgeWebSocketClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class SmsConversationManager(private val context: Context) {

    companion object {
        private const val TAG = "PhoneBridgeSms"
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val gson = Gson()
    private var wsListenerJob: Job? = null

    fun start() {
        listenForOutgoingSms()
        syncRecentConversations()
    }

    fun stop() {
        wsListenerJob?.cancel()
    }

    fun syncRecentConversations() {
        scope.launch {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) 
                != PackageManager.PERMISSION_GRANTED) {
                Log.w(TAG, "READ_SMS permission not granted; skipping SMS sync.")
                return@launch
            }

            try {
                val uri = Uri.parse("content://sms")
                val projection = arrayOf("_id", "thread_id", "address", "body", "date", "read")
                val cursor: Cursor? = context.contentResolver.query(
                    uri,
                    projection,
                    null,
                    null,
                    "date DESC LIMIT 40"
                )

                val conversationMap = LinkedHashMap<String, SmsConversation>()

                cursor?.use { c ->
                    val addressIndex = c.getColumnIndex("address")
                    val bodyIndex = c.getColumnIndex("body")
                    val dateIndex = c.getColumnIndex("date")
                    val threadIndex = c.getColumnIndex("thread_id")
                    val readIndex = c.getColumnIndex("read")

                    while (c.moveToNext()) {
                        val address = if (addressIndex >= 0) c.getString(addressIndex) ?: "" else ""
                        if (address.isBlank()) continue

                        val normalizedKey = address.replace("\\s+".toRegex(), "")
                        if (!conversationMap.containsKey(normalizedKey)) {
                            val body = if (bodyIndex >= 0) c.getString(bodyIndex) ?: "" else ""
                            val date = if (dateIndex >= 0) c.getLong(dateIndex) else System.currentTimeMillis()
                            val threadId = if (threadIndex >= 0) c.getLong(threadIndex) else 0L
                            val isRead = if (readIndex >= 0) c.getInt(readIndex) == 1 else true
                            val contactName = resolveContactName(address)

                            conversationMap[normalizedKey] = SmsConversation(
                                threadId = threadId,
                                contactName = contactName,
                                phoneNumber = address,
                                lastMessage = body,
                                timestamp = date,
                                isRead = isRead
                            )
                        }
                    }
                }

                val list = conversationMap.values.toList()
                Log.i(TAG, "Synced ${list.size} SMS conversations.")

                val client = PhoneBridgeWebSocketClient.getInstance(context)
                client.sendPacket(PacketTypes.SMS_CONVERSATIONS, list)

            } catch (e: Exception) {
                Log.e(TAG, "Error querying SMS content resolver: ${e.message}")
            }
        }
    }

    private fun resolveContactName(phoneNumber: String): String {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) 
            != PackageManager.PERMISSION_GRANTED) {
            return phoneNumber
        }

        try {
            val uri = Uri.withAppendedPath(
                ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                Uri.encode(phoneNumber)
            )
            val cursor = context.contentResolver.query(
                uri,
                arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME),
                null,
                null,
                null
            )
            cursor?.use { c ->
                if (c.moveToFirst()) {
                    val nameIndex = c.getColumnIndex(ContactsContract.PhoneLookup.DISPLAY_NAME)
                    if (nameIndex >= 0) {
                        val name = c.getString(nameIndex)
                        if (!name.isNullOrBlank()) return name
                    }
                }
            }
        } catch (e: Exception) {
        }
        return phoneNumber
    }

    private fun listenForOutgoingSms() {
        val client = PhoneBridgeWebSocketClient.getInstance(context)
        wsListenerJob = scope.launch {
            client.packetFlow.collect { packet ->
                if (packet.type == PacketTypes.SEND_SMS) {
                    handleSendSms(packet)
                }
            }
        }
    }

    private fun handleSendSms(packet: com.phonebridge.android.data.BridgePacket) {
        val payloadElement = packet.payload ?: return
        try {
            val sendPayload = gson.fromJson(payloadElement, SendSmsPayload::class.java)
            val phone = sendPayload.phoneNumber
            val text = sendPayload.message

            if (phone.isBlank() || text.isBlank()) return

            Log.i(TAG, "Dispatching SMS to $phone: $text")

            if (ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) 
                == PackageManager.PERMISSION_GRANTED) {
                val smsManager: SmsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    context.getSystemService(SmsManager::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    SmsManager.getDefault()
                }

                val parts = smsManager.divideMessage(text)
                if (parts.size > 1) {
                    smsManager.sendMultipartTextMessage(phone, null, parts, null, null)
                } else {
                    smsManager.sendTextMessage(phone, null, text, null, null)
                }
                Log.i(TAG, "SMS successfully sent via SmsManager.")

                scope.launch {
                    kotlinx.coroutines.delay(1500)
                    syncRecentConversations()
                }
            } else {
                Log.w(TAG, "SEND_SMS permission not granted on Android.")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send SMS: ${e.message}")
        }
    }
}