package com.phonebridge.android

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.RemoteInput
import com.phonebridge.android.service.PhoneBridgeForegroundService
import com.phonebridge.android.service.PhoneFinderManager
import com.phonebridge.android.ui.screens.MainScreen
import com.phonebridge.android.ui.theme.PhoneBridgeTheme

class MainActivity : ComponentActivity() {

    private val app get() = application as PhoneBridgeApp

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        // Handle runtime permission results
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Handle incoming intents (test reply or stop ringing action)
        handleIncomingIntents(intent)

        // Request baseline permissions on first launch
        requestBaselinePermissions()

        // Start Foreground Service to maintain continuous connection
        try {
            PhoneBridgeForegroundService.startService(this)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        setContent {
            PhoneBridgeTheme {
                MainScreen(
                    wsClient = app.wsClient,
                    discoveryListener = app.udpDiscovery,
                    mediaController = app.mediaController,
                    clipboardManager = app.clipboardSync,
                    fileTransferManager = app.fileTransfer,
                    phoneFinder = app.phoneFinder
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIncomingIntents(intent)
    }

    private fun handleIncomingIntents(intent: Intent?) {
        if (intent == null) return

        if (intent.action == PhoneFinderManager.ACTION_STOP_RINGING) {
            app.phoneFinder.stopRinging(notifyPc = true)
        }

        if (intent.action == "com.phonebridge.android.ACTION_TEST_REPLY") {
            val remoteInputBundle = RemoteInput.getResultsFromIntent(intent)
            val replyText = remoteInputBundle?.getCharSequence("key_text_reply")?.toString()
            if (!replyText.isNullOrBlank()) {
                Toast.makeText(this, "PC'den gelen yanıt: $replyText", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun requestBaselinePermissions() {
        val permissions = mutableListOf<String>()

        permissions.add(Manifest.permission.READ_PHONE_STATE)
        permissions.add(Manifest.permission.ANSWER_PHONE_CALLS)
        permissions.add(Manifest.permission.CALL_PHONE)
        permissions.add(Manifest.permission.READ_CALL_LOG)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        permissionLauncher.launch(permissions.toTypedArray())
    }
}