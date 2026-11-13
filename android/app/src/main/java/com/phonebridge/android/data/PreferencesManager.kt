package com.phonebridge.android.data

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.util.UUID

class PreferencesManager(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("phone_bridge_prefs", Context.MODE_PRIVATE)
    private val gson = Gson()

    companion object {
        private const val KEY_PC_IP = "key_pc_ip"
        private const val KEY_PC_PORT = "key_pc_port"
        private const val KEY_AUTO_CONNECT = "key_auto_connect"
        private const val KEY_DEVICE_ID = "key_device_id"
        private const val KEY_CUSTOM_DEVICE_NAME = "key_custom_device_name"
        private const val KEY_THEME_MODE = "key_theme_mode"
        private const val KEY_PERSISTENT_NOTIFICATION = "key_persistent_notification"
        private const val KEY_BLUETOOTH_ENABLED = "key_bluetooth_enabled"
        private const val KEY_TRUSTED_NETWORKS = "key_trusted_networks"
        private const val KEY_PAIRED_DEVICES_JSON = "key_paired_devices_json"
        private const val KEY_LAST_DEVICE_NAME = "key_last_device_name"
        private const val DEFAULT_PORT = 8765
    }

    var pcIp: String
        get() = prefs.getString(KEY_PC_IP, "") ?: ""
        set(value) = prefs.edit().putString(KEY_PC_IP, value.trim()).apply()

    var pcPort: Int
        get() = prefs.getInt(KEY_PC_PORT, DEFAULT_PORT)
        set(value) = prefs.edit().putInt(KEY_PC_PORT, value).apply()

    var autoConnect: Boolean
        get() = prefs.getBoolean(KEY_AUTO_CONNECT, true)
        set(value) = prefs.edit().putBoolean(KEY_AUTO_CONNECT, value).apply()

    var lastConnectedDeviceName: String
        get() = prefs.getString(KEY_LAST_DEVICE_NAME, "OMER-LAPTOP") ?: "OMER-LAPTOP"
        set(value) = prefs.edit().putString(KEY_LAST_DEVICE_NAME, value).apply()

    val deviceId: String
        get() {
            var id = prefs.getString(KEY_DEVICE_ID, null)
            if (id.isNullOrBlank()) {
                id = UUID.randomUUID().toString()
                prefs.edit().putString(KEY_DEVICE_ID, id).apply()
            }
            return id
        }

    var customDeviceName: String
        get() = prefs.getString(KEY_CUSTOM_DEVICE_NAME, "") ?: ""
        set(value) = prefs.edit().putString(KEY_CUSTOM_DEVICE_NAME, value.trim()).apply()

    val effectiveDeviceName: String
        get() {
            val custom = customDeviceName
            if (custom.isNotBlank()) return custom
            val model = Build.MODEL
            val manufacturer = Build.MANUFACTURER
            return if (model.startsWith(manufacturer, ignoreCase = true)) {
                model
            } else {
                "$manufacturer $model"
            }
        }

    var themeMode: String
        get() = prefs.getString(KEY_THEME_MODE, "dark") ?: "dark"
        set(value) = prefs.edit().putString(KEY_THEME_MODE, value).apply()

    var persistentNotification: Boolean
        get() = prefs.getBoolean(KEY_PERSISTENT_NOTIFICATION, true)
        set(value) = prefs.edit().putBoolean(KEY_PERSISTENT_NOTIFICATION, value).apply()

    var bluetoothEnabled: Boolean
        get() = prefs.getBoolean(KEY_BLUETOOTH_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_BLUETOOTH_ENABLED, value).apply()

    var trustedNetworks: Set<String>
        get() = prefs.getStringSet(KEY_TRUSTED_NETWORKS, emptySet()) ?: emptySet()
        set(value) = prefs.edit().putStringSet(KEY_TRUSTED_NETWORKS, value).apply()

    fun addTrustedNetwork(ssid: String) {
        val current = trustedNetworks.toMutableSet()
        current.add(ssid)
        trustedNetworks = current
    }

    fun removeTrustedNetwork(ssid: String) {
        val current = trustedNetworks.toMutableSet()
        current.remove(ssid)
        trustedNetworks = current
    }

    fun getPairedDevices(): List<PairedDevice> {
        val json = prefs.getString(KEY_PAIRED_DEVICES_JSON, null)
        if (json.isNullOrBlank()) {
            return listOf(
                PairedDevice("OMER-LAPTOP-ID", "OMER-LAPTOP", "10.0.2.2", 8765),
                PairedDevice("DESKTOP-ECGOOIP-ID", "DESKTOP-ECGOOIP", "192.168.1.110", 8765),
                PairedDevice("LRT-W09-ID", "LRT-W09", "192.168.1.120", 8765)
            )
        }
        return try {
            val type = object : TypeToken<List<PairedDevice>>() {}.type
            gson.fromJson(json, type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun addPairedDevice(device: PairedDevice) {
        val current = getPairedDevices().toMutableList()
        current.removeAll { it.id == device.id || it.ip == device.ip }
        current.add(0, device)
        val json = gson.toJson(current)
        prefs.edit().putString(KEY_PAIRED_DEVICES_JSON, json).apply()
        lastConnectedDeviceName = device.name
    }

    fun removePairedDevice(deviceIdOrIp: String) {
        val current = getPairedDevices().toMutableList()
        current.removeAll { it.id == deviceIdOrIp || it.ip == deviceIdOrIp }
        val json = gson.toJson(current)
        prefs.edit().putString(KEY_PAIRED_DEVICES_JSON, json).apply()
    }

    fun isDevicePaired(deviceIdOrIp: String): Boolean {
        return getPairedDevices().any { it.id == deviceIdOrIp || it.ip == deviceIdOrIp }
    }
}