package com.phonebridge.android.network

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.os.Build
import android.util.Log
import com.google.gson.Gson
import com.phonebridge.android.data.BridgePacket
import com.phonebridge.android.data.ConnectionState
import com.phonebridge.android.data.DeviceStatusPayload
import com.phonebridge.android.data.DiscoveredPc
import com.phonebridge.android.data.PacketTypes
import com.phonebridge.android.data.PairRequestPayload
import com.phonebridge.android.data.PairResponsePayload
import com.phonebridge.android.data.PairedDevice
import com.phonebridge.android.data.PreferencesManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class PhoneBridgeWebSocketClient private constructor(private val context: Context) {

    companion object {
        private const val TAG = "PhoneBridgeWS"
        private const val MIN_RECONNECT_DELAY_MS = 2000L
        private const val MAX_RECONNECT_DELAY_MS = 30000L

        @Volatile
        private var INSTANCE: PhoneBridgeWebSocketClient? = null

        fun getInstance(context: Context): PhoneBridgeWebSocketClient {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: PhoneBridgeWebSocketClient(context.applicationContext).also {
                    INSTANCE = it
                }
            }
        }
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val prefs = PreferencesManager(context)
    private val gson = Gson()

    private val okHttpClient = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .pingInterval(15, TimeUnit.SECONDS)
        .build()

    private var currentWebSocket: WebSocket? = null
    private var reconnectJob: Job? = null
    private var pingJob: Job? = null
    private var currentReconnectDelay = MIN_RECONNECT_DELAY_MS
    private val shouldStayConnected = AtomicBoolean(false)

    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _packetFlow = MutableSharedFlow<BridgePacket>(extraBufferCapacity = 64)
    val packetFlow: SharedFlow<BridgePacket> = _packetFlow.asSharedFlow()

    private val _serverAddress = MutableStateFlow("")
    val serverAddress: StateFlow<String> = _serverAddress.asStateFlow()

    private val _latencyMs = MutableStateFlow(0L)
    val latencyMs: StateFlow<Long> = _latencyMs.asStateFlow()

    private val _activeConnectedDevice = MutableStateFlow<DiscoveredPc?>(null)
    val activeConnectedDevice: StateFlow<DiscoveredPc?> = _activeConnectedDevice.asStateFlow()

    private val _incomingPairingRequest = MutableStateFlow<PairRequestPayload?>(null)
    val incomingPairingRequest: StateFlow<PairRequestPayload?> = _incomingPairingRequest.asStateFlow()

    private var lastPingSentTime: Long = 0L

    init {
        if (prefs.pcIp.isNotBlank()) {
            _activeConnectedDevice.value = DiscoveredPc(
                ip = prefs.pcIp,
                name = prefs.lastConnectedDeviceName,
                port = prefs.pcPort
            )
        }
    }

    fun connect(host: String? = null, port: Int? = null, deviceName: String? = null) {
        val targetHost = (host ?: prefs.pcIp).trim()
        val targetPort = port ?: prefs.pcPort

        if (targetHost.isBlank()) {
            Log.w(TAG, "Cannot connect: Host IP is blank.")
            _connectionState.value = ConnectionState.DISCONNECTED
            return
        }

        prefs.pcIp = targetHost
        prefs.pcPort = targetPort
        if (!deviceName.isNullOrBlank()) {
            prefs.lastConnectedDeviceName = deviceName
            _activeConnectedDevice.value = DiscoveredPc(targetHost, deviceName, targetPort)
        } else if (_activeConnectedDevice.value?.ip != targetHost) {
            _activeConnectedDevice.value = DiscoveredPc(targetHost, prefs.lastConnectedDeviceName, targetPort)
        }

        _serverAddress.value = "$targetHost:$targetPort"
        shouldStayConnected.set(true)

        initiateConnection(targetHost, targetPort)
    }

    private fun initiateConnection(host: String, port: Int) {
        disconnectInternal(clearRetry = false)

        _connectionState.value = ConnectionState.CONNECTING
        Log.i(TAG, "Connecting to ws://$host:$port/ws ...")

        val url = "ws://$host:$port/ws"
        val request = Request.Builder()
            .url(url)
            .build()

        currentWebSocket = okHttpClient.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.i(TAG, "WebSocket connected successfully to $url")
                _connectionState.value = ConnectionState.CONNECTED
                currentReconnectDelay = MIN_RECONNECT_DELAY_MS
                startPingKeepAlive()
                sendDeviceStatus()
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                handleIncomingMessage(text)
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "WebSocket closing: $code / $reason")
                webSocket.close(1000, null)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "WebSocket closed: $code / $reason")
                _connectionState.value = ConnectionState.DISCONNECTED
                stopPingKeepAlive()
                scheduleReconnectIfAllowed()
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "WebSocket failure: ${t.message}")
                _connectionState.value = ConnectionState.ERROR
                stopPingKeepAlive()
                scheduleReconnectIfAllowed()
            }
        })
    }

    fun initiatePairing(host: String, port: Int, targetName: String) {
        prefs.pcIp = host
        prefs.pcPort = port
        prefs.lastConnectedDeviceName = targetName
        _activeConnectedDevice.value = DiscoveredPc(host, targetName, port)

        connect(host, port, targetName)

        scope.launch {
            var retries = 0
            while (retries < 25 && _connectionState.value != ConnectionState.CONNECTED) {
                delay(200)
                retries++
            }

            if (_connectionState.value == ConnectionState.CONNECTED) {
                val pairPayload = PairRequestPayload(
                    deviceId = prefs.deviceId,
                    deviceName = prefs.effectiveDeviceName
                )
                sendPacket(PacketTypes.PAIR_REQUEST, pairPayload)

                prefs.addPairedDevice(
                    PairedDevice(
                        id = host,
                        name = targetName,
                        ip = host,
                        port = port
                    )
                )
            }
        }
    }

    fun acceptPairing(req: PairRequestPayload) {
        val payload = PairResponsePayload(
            accepted = true,
            deviceId = prefs.deviceId,
            deviceName = prefs.effectiveDeviceName
        )
        sendPacket(PacketTypes.PAIR_ACCEPT, payload)

        val host = prefs.pcIp
        val port = prefs.pcPort
        val name = req.deviceName.ifBlank { "PC" }
        prefs.addPairedDevice(
            PairedDevice(
                id = req.deviceId.ifBlank { host },
                name = name,
                ip = host,
                port = port
            )
        )
        prefs.lastConnectedDeviceName = name
        _activeConnectedDevice.value = DiscoveredPc(host, name, port)
        _incomingPairingRequest.value = null
    }

    fun rejectPairing(req: PairRequestPayload) {
        val payload = PairResponsePayload(
            accepted = false,
            deviceId = prefs.deviceId,
            deviceName = prefs.effectiveDeviceName
        )
        sendPacket(PacketTypes.PAIR_REJECT, payload)
        _incomingPairingRequest.value = null
        disconnect()
    }

    fun unpairCurrentDevice() {
        val active = _activeConnectedDevice.value ?: DiscoveredPc(prefs.pcIp, prefs.lastConnectedDeviceName, prefs.pcPort)
        sendPacket(PacketTypes.UNPAIR, mapOf("deviceId" to prefs.deviceId))
        prefs.removePairedDevice(active.ip)
        prefs.removePairedDevice(active.name)
        _activeConnectedDevice.value = null
        disconnect()
    }

    fun disconnect() {
        shouldStayConnected.set(false)
        reconnectJob?.cancel()
        disconnectInternal(clearRetry = true)
    }

    private fun disconnectInternal(clearRetry: Boolean) {
        stopPingKeepAlive()
        if (clearRetry) {
            currentReconnectDelay = MIN_RECONNECT_DELAY_MS
        }
        try {
            currentWebSocket?.close(1000, "Normal closure")
        } catch (e: Exception) {
        }
        currentWebSocket = null
        _connectionState.value = ConnectionState.DISCONNECTED
    }

    private fun scheduleReconnectIfAllowed() {
        if (!shouldStayConnected.get()) return

        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            Log.d(TAG, "Reconnecting in ${currentReconnectDelay}ms...")
            delay(currentReconnectDelay)
            currentReconnectDelay = (currentReconnectDelay * 2).coerceAtMost(MAX_RECONNECT_DELAY_MS)
            if (shouldStayConnected.get() && _connectionState.value != ConnectionState.CONNECTED) {
                val host = prefs.pcIp
                val port = prefs.pcPort
                if (host.isNotBlank()) {
                    initiateConnection(host, port)
                }
            }
        }
    }

    private fun startPingKeepAlive() {
        pingJob?.cancel()
        pingJob = scope.launch {
            while (isActive && _connectionState.value == ConnectionState.CONNECTED) {
                delay(10000)
                lastPingSentTime = System.currentTimeMillis()
                val pingPacket = BridgePacket(type = PacketTypes.PING, timestamp = lastPingSentTime)
                sendPacket(pingPacket)
            }
        }
    }

    private fun stopPingKeepAlive() {
        pingJob?.cancel()
        pingJob = null
    }

    private fun handleIncomingMessage(json: String) {
        val packet = BridgePacket.fromJson(json) ?: return

        when (packet.type) {
            PacketTypes.PONG -> {
                if (lastPingSentTime > 0) {
                    val rtt = System.currentTimeMillis() - lastPingSentTime
                    _latencyMs.value = rtt
                }
            }
            PacketTypes.PING -> {
                val pong = BridgePacket(type = PacketTypes.PONG)
                sendPacket(pong)
            }
            PacketTypes.PAIR_REQUEST -> {
                try {
                    val req = gson.fromJson(packet.payload, PairRequestPayload::class.java)
                    if (req != null) {
                        _incomingPairingRequest.value = req
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing PAIR_REQUEST: ${e.message}")
                }
            }
            PacketTypes.PAIR_ACCEPT -> {
                _incomingPairingRequest.value = null
                val currentHost = prefs.pcIp
                val currentPort = prefs.pcPort
                val name = prefs.lastConnectedDeviceName
                prefs.addPairedDevice(
                    PairedDevice(
                        id = currentHost,
                        name = name,
                        ip = currentHost,
                        port = currentPort
                    )
                )
                _activeConnectedDevice.value = DiscoveredPc(currentHost, name, currentPort)
            }
            PacketTypes.PAIR_REJECT -> {
                _incomingPairingRequest.value = null
                disconnect()
            }
            PacketTypes.UNPAIR -> {
                val currentHost = prefs.pcIp
                prefs.removePairedDevice(currentHost)
                _activeConnectedDevice.value = null
                disconnect()
            }
            else -> {
                scope.launch {
                    _packetFlow.emit(packet)
                }
            }
        }
    }

    fun sendPacket(packet: BridgePacket): Boolean {
        val ws = currentWebSocket
        if (ws != null && _connectionState.value == ConnectionState.CONNECTED) {
            val json = BridgePacket.toJson(packet)
            return ws.send(json)
        }
        return false
    }

    fun <T> sendPacket(type: String, payload: T): Boolean {
        val packet = BridgePacket.create(type, payload)
        return sendPacket(packet)
    }

    fun sendDeviceStatus() {
        scope.launch {
            try {
                val batteryStatus = getBatteryStatus()
                val wifiSignal = getWifiSignal()
                val ip = getLocalIpAddress()

                val deviceName = prefs.effectiveDeviceName
                val deviceModel = Build.MODEL
                val os = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"

                val payload = DeviceStatusPayload(
                    deviceId = prefs.deviceId,
                    deviceName = deviceName,
                    deviceModel = deviceModel,
                    operatingSystem = os,
                    battery = batteryStatus.first,
                    isCharging = batteryStatus.second,
                    wifiSignalLevel = wifiSignal,
                    ipAddress = ip
                )

                sendPacket(PacketTypes.HANDSHAKE, payload)
                sendPacket(PacketTypes.DEVICE_STATUS, payload)
            } catch (e: Exception) {
                Log.e(TAG, "Error gathering device status: ${e.message}")
            }
        }
    }

    private fun getBatteryStatus(): Pair<Int, Boolean> {
        val ifilter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        val batteryStatus: Intent? = context.registerReceiver(null, ifilter)

        val level = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: 50
        val scale = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: 100
        val pct = if (scale > 0) (level * 100 / scale) else level

        val status = batteryStatus?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL

        return Pair(pct, isCharging)
    }

    private fun getWifiSignal(): Int {
        return try {
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            val rssi = wifiManager?.connectionInfo?.rssi ?: -70
            WifiManager.calculateSignalLevel(rssi, 5)
        } catch (e: Exception) {
            4
        }
    }

    private fun getLocalIpAddress(): String {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val intf = interfaces.nextElement()
                val addrs = intf.inetAddresses
                while (addrs.hasMoreElements()) {
                    val addr = addrs.nextElement()
                    if (!addr.isLoopbackAddress && addr is Inet4Address) {
                        return addr.hostAddress ?: ""
                    }
                }
            }
        } catch (e: Exception) {
        }
        return "127.0.0.1"
    }
}