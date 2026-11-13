package com.phonebridge.android.ui.screens

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Laptop
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.TabletAndroid
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.phonebridge.android.data.ConnectionState
import com.phonebridge.android.data.DiscoveredPc
import com.phonebridge.android.data.PairedDevice
import com.phonebridge.android.data.PreferencesManager
import com.phonebridge.android.network.PhoneBridgeWebSocketClient
import com.phonebridge.android.network.UdpDiscoveryListener
import com.phonebridge.android.ui.theme.DarkCardBorder
import com.phonebridge.android.ui.theme.DarkSurface
import com.phonebridge.android.ui.theme.DarkSurfaceVariant
import com.phonebridge.android.ui.theme.FluentBlue
import com.phonebridge.android.ui.theme.StatusConnected
import com.phonebridge.android.ui.theme.StatusConnecting
import com.phonebridge.android.ui.theme.TextMuted
import com.phonebridge.android.ui.theme.TextPrimary
import com.phonebridge.android.ui.theme.TextSecondary
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceListScreen(
    wsClient: PhoneBridgeWebSocketClient,
    discoveryListener: UdpDiscoveryListener,
    onOpenDrawer: () -> Unit,
    onDeviceSelected: (DiscoveredPc) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember { PreferencesManager(context) }

    val connectionState by wsClient.connectionState.collectAsState()
    val activeDevice by wsClient.activeConnectedDevice.collectAsState()
    val discoveredPcs by discoveryListener.discoveredPcs.collectAsState()

    var showMenu by remember { mutableStateOf(false) }
    var isRefreshing by remember { mutableStateOf(false) }

    var rememberedDevices by remember { mutableStateOf(prefs.getPairedDevices()) }

    val reloadRemembered = {
        rememberedDevices = prefs.getPairedDevices()
    }

    val connectedPc: DiscoveredPc? = if (connectionState == ConnectionState.CONNECTED && activeDevice != null) {
        activeDevice
    } else {
        null
    }

    val availableDevices = discoveredPcs.filter { discovered ->
        connectedPc?.ip != discovered.ip
    }

    val offlineRememberedDevices = rememberedDevices.filter { paired ->
        connectedPc?.ip != paired.ip && availableDevices.none { it.ip == paired.ip }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "PhoneBridge Aygıtları",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onOpenDrawer) {
                        Icon(
                            imageVector = Icons.Default.Menu,
                            contentDescription = "Menü",
                            tint = TextPrimary
                        )
                    }
                },
                actions = {
                    IconButton(onClick = {
                        scope.launch {
                            isRefreshing = true
                            discoveryListener.stopListening()
                            delay(200)
                            discoveryListener.startListening()
                            reloadRemembered()
                            delay(400)
                            isRefreshing = false
                        }
                    }) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Yenile",
                            tint = if (isRefreshing) FluentBlue else TextPrimary
                        )
                    }
                    IconButton(onClick = { showMenu = true }) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "Daha Fazla",
                            tint = TextPrimary
                        )
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Tümünü Yenile") },
                            leadingIcon = { Icon(Icons.Default.Refresh, contentDescription = null) },
                            onClick = {
                                showMenu = false
                                discoveryListener.startListening()
                                reloadRemembered()
                            }
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = DarkSurface
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Text(
                    text = "PhoneBridge'te çalışan, aynı ağdaki diğer aygıtlar burada gözükmelidir.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.7f),
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
                )
            }

            item {
                SectionHeader(title = "Bağlı aygıtlar")
            }

            if (connectedPc != null) {
                item {
                    ConnectedDeviceCard(
                        device = connectedPc,
                        onClick = {
                            onDeviceSelected(connectedPc)
                        }
                    )
                }
            }

            item {
                SectionHeader(
                    title = "Anımsanan aygıtlar",
                    badge = if (availableDevices.isNotEmpty()) "${availableDevices.size} canlı" else null,
                    trailingIndicator = {
                        RadarPulseIndicator(color = FluentBlue)
                    }
                )
            }

            if (availableDevices.isNotEmpty()) {
                items(availableDevices, key = { it.ip }) { discovered ->
                    val isConnecting = (connectionState == ConnectionState.CONNECTING && activeDevice?.ip == discovered.ip)
                    AvailableDeviceCard(
                        device = discovered,
                        isConnecting = isConnecting,
                        onClick = {
                            wsClient.initiatePairing(discovered.ip, discovered.port, discovered.name)
                            reloadRemembered()
                            onDeviceSelected(discovered)
                        }
                    )
                }
            } else {
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .border(1.dp, DarkCardBorder, RoundedCornerShape(12.dp)),
                        colors = CardDefaults.cardColors(containerColor = DarkSurface)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadarPulseIndicator(
                                modifier = Modifier.size(32.dp),
                                color = FluentBlue
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Column {
                                Text(
                                    text = "Ağdaki bilgisayarlar taranıyor...",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = TextPrimary
                                )
                                Text(
                                    text = "PC'nizde PhoneBridge açık ve aynı Wi-Fi ağına bağlı olmalıdır.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextMuted
                                )
                            }
                        }
                    }
                }
            }

            if (offlineRememberedDevices.isNotEmpty()) {
                item {
                    SectionHeader(title = "Anımsanan aygıtlar")
                }

                items(offlineRememberedDevices, key = { it.id }) { remembered ->
                    RememberedDeviceCard(
                        device = remembered,
                        onConnect = {
                            wsClient.connect(remembered.ip, remembered.port, remembered.name)
                            onDeviceSelected(DiscoveredPc(remembered.ip, remembered.name, remembered.port))
                        },
                        onForget = {
                            prefs.removePairedDevice(remembered.id)
                            reloadRemembered()
                        }
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Composable
fun RadarPulseIndicator(
    modifier: Modifier = Modifier,
    color: Color = FluentBlue
) {
    val infiniteTransition = rememberInfiniteTransition(label = "RadarPulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1.9f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "RadarScale"
    )
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.7f,
        targetValue = 0.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "RadarAlpha"
    )

    Box(
        modifier = modifier.size(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(20.dp)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    this.alpha = alpha
                }
                .clip(CircleShape)
                .background(color)
        )
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color)
        )
    }
}

@Composable
fun SectionHeader(
    title: String,
    badge: String? = null,
    trailingIndicator: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Normal,
            color = Color.White.copy(alpha = 0.85f)
        )
        if (badge != null) {
            Spacer(modifier = Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF333336))
                    .padding(horizontal = 8.dp, vertical = 2.dp)
            ) {
                Text(
                    text = badge,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.7f)
                )
            }
        }
        if (trailingIndicator != null) {
            Spacer(modifier = Modifier.width(10.dp))
            trailingIndicator()
        }
    }
}

@Composable
fun DeviceIconBadge(
    isTablet: Boolean = false,
    isConnected: Boolean = false
) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (isConnected) Color(0xFF0078D4).copy(alpha = 0.25f)
                else Color(0xFF353538)
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = if (isTablet) Icons.Default.TabletAndroid else Icons.Default.Computer,
            contentDescription = null,
            tint = if (isConnected) Color(0xFF4CA6FF) else Color.White.copy(alpha = 0.95f),
            modifier = Modifier.size(24.dp)
        )
    }
}

@Composable
fun ConnectedDeviceCard(
    device: DiscoveredPc,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(74.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF262628))
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            DeviceIconBadge(isTablet = false, isConnected = true)

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = device.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White.copy(alpha = 0.95f)
                )
                Text(
                    text = "Bağlandı • Yerel Ağ",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF4CA6FF)
                )
            }
        }
    }
}

@Composable
fun AvailableDeviceCard(
    device: DiscoveredPc,
    isConnecting: Boolean = false,
    onClick: () -> Unit
) {
    val isTablet = device.name.startsWith("LRT", ignoreCase = true) || device.name.contains("tab", ignoreCase = true) || device.name.contains("pad", ignoreCase = true)
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(74.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF262628))
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            DeviceIconBadge(isTablet = isTablet, isConnected = false)

            Spacer(modifier = Modifier.width(16.dp))

            Text(
                text = device.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                color = Color.White.copy(alpha = 0.95f),
                modifier = Modifier.weight(1f)
            )

            if (isConnecting) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = Color.White,
                    strokeWidth = 2.dp
                )
            }
        }
    }
}

@Composable
fun RememberedDeviceCard(
    device: PairedDevice,
    onConnect: () -> Unit,
    onForget: () -> Unit
) {
    val isTablet = device.name.startsWith("LRT", ignoreCase = true) || device.name.contains("tab", ignoreCase = true) || device.name.contains("pad", ignoreCase = true)
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(74.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onConnect),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF262628))
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            DeviceIconBadge(isTablet = isTablet, isConnected = false)

            Spacer(modifier = Modifier.width(16.dp))

            Text(
                text = device.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Normal,
                color = Color.White.copy(alpha = 0.85f),
                modifier = Modifier.weight(1f)
            )

            IconButton(onClick = onForget) {
                Icon(
                    imageVector = Icons.Default.DeleteOutline,
                    contentDescription = "Unut",
                    tint = Color.White.copy(alpha = 0.4f),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
fun EmptySectionPlaceholder(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    message: String
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(DarkSurfaceVariant.copy(alpha = 0.3f))
            .padding(vertical = 20.dp, horizontal = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = TextMuted,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = TextMuted
            )
        }
    }
}