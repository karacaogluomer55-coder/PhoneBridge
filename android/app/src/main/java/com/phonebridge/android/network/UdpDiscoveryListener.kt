package com.phonebridge.android.network

import android.content.Context
import android.net.wifi.WifiManager
import android.os.Build
import android.util.Log
import com.phonebridge.android.data.DiscoveredPc
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface

class UdpDiscoveryListener(private val context: Context) {

    companion object {
        private const val TAG = "UdpDiscovery"
        private const val DISCOVERY_PORT = 8766
        private const val PING_INTERVAL_MS = 3000L
        private const val STALE_TIMEOUT_MS = 20000L
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var listenJob: Job? = null
    private var pingJob: Job? = null
    private var multicastLock: WifiManager.MulticastLock? = null
    private var socket: DatagramSocket? = null

    private val _discoveredPcs = MutableStateFlow<List<DiscoveredPc>>(emptyList())
    val discoveredPcs: StateFlow<List<DiscoveredPc>> = _discoveredPcs.asStateFlow()

    @Synchronized
    fun startListening() {
        if (listenJob?.isActive == true && pingJob?.isActive == true) return

        stopListening()

        try {
            val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            multicastLock = wifi?.createMulticastLock("PhoneBridgeMulticastLock")?.apply {
                setReferenceCounted(true)
                acquire()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to acquire multicast lock: ${e.message}")
        }

        try {
            socket = DatagramSocket(null).apply {
                reuseAddress = true
                broadcast = true
                bind(InetSocketAddress(DISCOVERY_PORT))
                soTimeout = 4000
            }
            Log.i(TAG, "UDP socket bound to port $DISCOVERY_PORT with broadcast enabled")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to bind UDP socket to port $DISCOVERY_PORT: ${e.message}")
            try {
                socket = DatagramSocket().apply {
                    broadcast = true
                    soTimeout = 4000
                }
            } catch (ex: Exception) {
                Log.e(TAG, "Fallback socket creation failed: ${ex.message}")
            }
        }

        listenJob = scope.launch {
            val buffer = ByteArray(2048)

            while (isActive) {
                val currentSocket = socket
                if (currentSocket == null || currentSocket.isClosed) {
                    delay(1000)
                    continue
                }

                try {
                    val packet = DatagramPacket(buffer, buffer.size)
                    currentSocket.receive(packet)

                    val message = String(packet.data, 0, packet.length, Charsets.UTF_8).trim()
                    val senderRawIp = packet.address?.hostAddress ?: continue
                    val senderIp = senderRawIp.substringBefore('%')

                    if (message.isBlank()) continue

                    if (message.startsWith("{") && message.contains("\"type\"")) {
                        try {
                            val json = JSONObject(message)
                            val type = json.optString("type")

                            if (type.equals("IDENTITY", ignoreCase = true)) {
                                val pcName = json.optString("deviceName").ifBlank {
                                    json.optString("name", "Windows PC")
                                }
                                val port = if (json.has("tcpPort")) {
                                    json.optInt("tcpPort", 8765)
                                } else {
                                    json.optInt("port", 8765)
                                }

                                val found = DiscoveredPc(
                                    ip = senderIp,
                                    name = pcName,
                                    port = port,
                                    lastSeenTimestamp = System.currentTimeMillis()
                                )
                                Log.i(TAG, "Discovered PC via JSON IDENTITY: $pcName ($senderIp:$port)")
                                updateDiscoveredPcs(found)
                            }
                        } catch (e: Exception) {
                            Log.d(TAG, "JSON parse ignore: ${e.message}")
                        }
                    } else if (message.startsWith("PhoneBridge;", ignoreCase = true)) {
                        val parts = message.split(";")
                        if (parts.size >= 2) {
                            val pcName = parts[1]
                            val port = if (parts.size >= 3) parts[2].toIntOrNull() ?: 8765 else 8765
                            val found = DiscoveredPc(
                                ip = senderIp,
                                name = pcName,
                                port = port,
                                lastSeenTimestamp = System.currentTimeMillis()
                            )
                            Log.i(TAG, "Discovered PC via legacy beacon: $pcName ($senderIp:$port)")
                            updateDiscoveredPcs(found)
                        }
                    }
                } catch (e: java.net.SocketTimeoutException) {
                    pruneStalePcs()
                } catch (e: Exception) {
                    if (!isActive) break
                    Log.d(TAG, "Socket receive tick: ${e.message}")
                }
            }
        }

        pingJob = scope.launch {
            while (isActive) {
                broadcastDiscoveryPing()
                delay(PING_INTERVAL_MS)
            }
        }
    }

    private fun broadcastDiscoveryPing() {
        try {
            val pingJson = JSONObject().apply {
                put("type", "DISCOVERY_PING")
                put("deviceName", Build.MODEL ?: "Android Phone")
            }.toString()
            val pingBytes = pingJson.toByteArray(Charsets.UTF_8)

            val currentSocket = socket
            if (currentSocket != null && !currentSocket.isClosed) {
                try {
                    val broadcastAddr = InetAddress.getByName("255.255.255.255")
                    val pingPacket = DatagramPacket(pingBytes, pingBytes.size, broadcastAddr, DISCOVERY_PORT)
                    currentSocket.send(pingPacket)
                } catch (e: Exception) {
                    Log.w(TAG, "Error sending broadcast to 255.255.255.255: ${e.message}")
                }
                sendToInterfaceBroadcasts(currentSocket, pingBytes)
            } else {
                try {
                    DatagramSocket().use { tempSocket ->
                        tempSocket.broadcast = true
                        val broadcastAddr = InetAddress.getByName("255.255.255.255")
                        val pingPacket = DatagramPacket(pingBytes, pingBytes.size, broadcastAddr, DISCOVERY_PORT)
                        tempSocket.send(pingPacket)
                        sendToInterfaceBroadcasts(tempSocket, pingBytes)
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Fallback socket ping error: ${e.message}")
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "broadcastDiscoveryPing error: ${e.message}")
        }
    }

    private fun sendToInterfaceBroadcasts(sock: DatagramSocket, bytes: ByteArray) {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces() ?: return
            for (iface in interfaces) {
                if (!iface.isUp || iface.isLoopback) continue
                for (ifaceAddr in iface.interfaceAddresses) {
                    val bcast = ifaceAddr.broadcast ?: continue
                    try {
                        sock.send(DatagramPacket(bytes, bytes.size, bcast, DISCOVERY_PORT))
                    } catch (e: Exception) {
                    }
                }
            }
        } catch (e: Exception) {
        }
    }

    private fun updateDiscoveredPcs(pc: DiscoveredPc) {
        val current = _discoveredPcs.value.toMutableList()
        val index = current.indexOfFirst { it.ip == pc.ip }
        if (index >= 0) {
            current[index] = pc
        } else {
            current.add(pc)
        }
        _discoveredPcs.value = current
    }

    private fun pruneStalePcs() {
        val now = System.currentTimeMillis()
        val filtered = _discoveredPcs.value.filter { now - it.lastSeenTimestamp < STALE_TIMEOUT_MS }
        if (filtered.size != _discoveredPcs.value.size) {
            _discoveredPcs.value = filtered
        }
    }

    @Synchronized
    fun stopListening() {
        pingJob?.cancel()
        pingJob = null
        listenJob?.cancel()
        listenJob = null

        try {
            socket?.close()
        } catch (e: Exception) { }
        socket = null

        try {
            if (multicastLock?.isHeld == true) {
                multicastLock?.release()
            }
        } catch (e: Exception) { }
        multicastLock = null
    }
}