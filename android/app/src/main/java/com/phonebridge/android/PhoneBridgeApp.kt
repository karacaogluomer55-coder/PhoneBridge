package com.phonebridge.android

import android.app.Application
import android.util.Log
import com.phonebridge.android.data.PreferencesManager
import com.phonebridge.android.network.PhoneBridgeWebSocketClient
import com.phonebridge.android.network.UdpDiscoveryListener
import com.phonebridge.android.service.ClipboardSyncManager
import com.phonebridge.android.service.FileTransferManager
import com.phonebridge.android.service.PhoneBridgeForegroundService
import com.phonebridge.android.service.PhoneBridgeMediaController
import com.phonebridge.android.service.PhoneFinderManager
import com.phonebridge.android.service.SmsConversationManager

class PhoneBridgeApp : Application() {

    companion object {
        lateinit var instance: PhoneBridgeApp
            private set
    }

    lateinit var wsClient: PhoneBridgeWebSocketClient
        private set

    lateinit var udpDiscovery: UdpDiscoveryListener
        private set

    lateinit var mediaController: PhoneBridgeMediaController
        private set

    lateinit var smsManager: SmsConversationManager
        private set

    lateinit var prefs: PreferencesManager
        private set

    lateinit var clipboardSync: ClipboardSyncManager
        private set

    lateinit var fileTransfer: FileTransferManager
        private set

    lateinit var phoneFinder: PhoneFinderManager
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this

        prefs = PreferencesManager(this)
        wsClient = PhoneBridgeWebSocketClient.getInstance(this)
        udpDiscovery = UdpDiscoveryListener(this)
        mediaController = PhoneBridgeMediaController(this)
        smsManager = SmsConversationManager(this)
        clipboardSync = ClipboardSyncManager.getInstance(this, wsClient)
        fileTransfer = FileTransferManager.getInstance(this)
        phoneFinder = PhoneFinderManager.getInstance(this, wsClient)

        // Start UDP discovery to detect PC broadcasts on port 8766
        udpDiscovery.startListening()

        // Start media controller listener
        mediaController.start()

        // Start SMS conversation manager
        smsManager.start()

        // Start persistent foreground service if autoConnect is enabled
        if (prefs.autoConnect) {
            try {
                PhoneBridgeForegroundService.startService(this)
            } catch (e: Exception) {
                Log.w("PhoneBridgeApp", "Failed to start foreground service from Application onCreate: ${e.message}")
            }
        }
    }
}