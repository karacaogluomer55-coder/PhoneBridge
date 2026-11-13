package com.phonebridge.android.ui.screens

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.phonebridge.android.data.DiscoveredPc
import com.phonebridge.android.network.PhoneBridgeWebSocketClient
import com.phonebridge.android.network.UdpDiscoveryListener
import com.phonebridge.android.service.ClipboardSyncManager
import com.phonebridge.android.service.FileTransferManager
import com.phonebridge.android.service.PhoneBridgeMediaController
import com.phonebridge.android.service.PhoneFinderManager
import com.phonebridge.android.ui.theme.DarkCardBorder
import com.phonebridge.android.ui.theme.DarkSurface
import com.phonebridge.android.ui.theme.DarkSurfaceVariant
import com.phonebridge.android.ui.theme.FluentBlue
import com.phonebridge.android.ui.theme.TextMuted
import com.phonebridge.android.ui.theme.TextPrimary
import com.phonebridge.android.ui.theme.TextSecondary
import kotlinx.coroutines.launch

enum class ScreenDestination {
    DEVICE_LIST,
    DEVICE_DETAIL,
    PRESENTATION,
    MEDIA_CONTROL,
    TOUCHPAD,
    COMMANDS,
    FILES,
    PLUGINS,
    SETTINGS,
    ABOUT
}

@Composable
fun MainScreen(
    wsClient: PhoneBridgeWebSocketClient,
    discoveryListener: UdpDiscoveryListener,
    mediaController: PhoneBridgeMediaController,
    clipboardManager: ClipboardSyncManager,
    fileTransferManager: FileTransferManager,
    phoneFinder: PhoneFinderManager
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)

    var currentScreen by remember { mutableStateOf(ScreenDestination.DEVICE_LIST) }
    var selectedDevice by remember { mutableStateOf<DiscoveredPc?>(null) }

    val activeConnectedDevice by wsClient.activeConnectedDevice.collectAsState()
    val incomingPairingRequest by wsClient.incomingPairingRequest.collectAsState()

    BackHandler(enabled = currentScreen != ScreenDestination.DEVICE_LIST) {
        when (currentScreen) {
            ScreenDestination.PRESENTATION,
            ScreenDestination.MEDIA_CONTROL,
            ScreenDestination.TOUCHPAD,
            ScreenDestination.COMMANDS,
            ScreenDestination.FILES,
            ScreenDestination.PLUGINS -> {
                currentScreen = ScreenDestination.DEVICE_DETAIL
            }
            ScreenDestination.DEVICE_DETAIL,
            ScreenDestination.SETTINGS,
            ScreenDestination.ABOUT -> {
                currentScreen = ScreenDestination.DEVICE_LIST
            }
            ScreenDestination.DEVICE_LIST -> {
            }
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = DarkSurface,
                modifier = Modifier.width(300.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(FluentBlue),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.PhoneAndroid,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(30.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = "PhoneBridge",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )

                    Text(
                        text = "Windows & Android Mobil Köprü",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMuted
                    )
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(DarkCardBorder)
                )

                Spacer(modifier = Modifier.height(8.dp))

                NavigationDrawerItem(
                    icon = { Icon(Icons.Default.Devices, contentDescription = null) },
                    label = { Text("Aygıtlar", fontWeight = FontWeight.SemiBold) },
                    selected = currentScreen == ScreenDestination.DEVICE_LIST || currentScreen == ScreenDestination.DEVICE_DETAIL,
                    onClick = {
                        scope.launch { drawerState.close() }
                        currentScreen = ScreenDestination.DEVICE_LIST
                    },
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
                    colors = NavigationDrawerItemDefaults.colors(
                        selectedContainerColor = DarkSurfaceVariant,
                        selectedIconColor = FluentBlue,
                        selectedTextColor = TextPrimary,
                        unselectedIconColor = TextSecondary,
                        unselectedTextColor = TextSecondary
                    )
                )

                NavigationDrawerItem(
                    icon = { Icon(Icons.Default.Extension, contentDescription = null) },
                    label = { Text("Eklenti Ayarları", fontWeight = FontWeight.SemiBold) },
                    selected = currentScreen == ScreenDestination.PLUGINS,
                    onClick = {
                        scope.launch { drawerState.close() }
                        currentScreen = ScreenDestination.PLUGINS
                    },
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
                    colors = NavigationDrawerItemDefaults.colors(
                        selectedContainerColor = DarkSurfaceVariant,
                        selectedIconColor = FluentBlue,
                        selectedTextColor = TextPrimary,
                        unselectedIconColor = TextSecondary,
                        unselectedTextColor = TextSecondary
                    )
                )

                NavigationDrawerItem(
                    icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                    label = { Text("Ayarlar", fontWeight = FontWeight.SemiBold) },
                    selected = currentScreen == ScreenDestination.SETTINGS,
                    onClick = {
                        scope.launch { drawerState.close() }
                        currentScreen = ScreenDestination.SETTINGS
                    },
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
                    colors = NavigationDrawerItemDefaults.colors(
                        selectedContainerColor = DarkSurfaceVariant,
                        selectedIconColor = FluentBlue,
                        selectedTextColor = TextPrimary,
                        unselectedIconColor = TextSecondary,
                        unselectedTextColor = TextSecondary
                    )
                )

                NavigationDrawerItem(
                    icon = { Icon(Icons.Default.Info, contentDescription = null) },
                    label = { Text("Hakkında", fontWeight = FontWeight.SemiBold) },
                    selected = currentScreen == ScreenDestination.ABOUT,
                    onClick = {
                        scope.launch { drawerState.close() }
                        currentScreen = ScreenDestination.ABOUT
                    },
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
                    colors = NavigationDrawerItemDefaults.colors(
                        selectedContainerColor = DarkSurfaceVariant,
                        selectedIconColor = FluentBlue,
                        selectedTextColor = TextPrimary,
                        unselectedIconColor = TextSecondary,
                        unselectedTextColor = TextSecondary
                    )
                )

                Spacer(modifier = Modifier.weight(1f))

                Text(
                    text = "PhoneBridge v1.0.0",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMuted,
                    modifier = Modifier.padding(20.dp)
                )
            }
        }
    ) {
        when (currentScreen) {
            ScreenDestination.DEVICE_LIST -> {
                DeviceListScreen(
                    wsClient = wsClient,
                    discoveryListener = discoveryListener,
                    onOpenDrawer = { scope.launch { drawerState.open() } },
                    onDeviceSelected = { device ->
                        selectedDevice = device
                        currentScreen = ScreenDestination.DEVICE_DETAIL
                    }
                )
            }

            ScreenDestination.DEVICE_DETAIL -> {
                val currentDevice = selectedDevice ?: activeConnectedDevice ?: DiscoveredPc("127.0.0.1", "OMER-LAPTOP", 8765)
                DeviceDetailScreen(
                    device = currentDevice,
                    wsClient = wsClient,
                    clipboardManager = clipboardManager,
                    fileTransferManager = fileTransferManager,
                    onBack = { currentScreen = ScreenDestination.DEVICE_LIST },
                    onOpenDrawer = { scope.launch { drawerState.open() } },
                    onNavigatePresentation = { currentScreen = ScreenDestination.PRESENTATION },
                    onNavigateMedia = { currentScreen = ScreenDestination.MEDIA_CONTROL },
                    onNavigateTouchpad = { currentScreen = ScreenDestination.TOUCHPAD },
                    onNavigateCommands = { currentScreen = ScreenDestination.COMMANDS },
                    onNavigatePlugins = { currentScreen = ScreenDestination.PLUGINS }
                )
            }

            ScreenDestination.PRESENTATION -> {
                PresentationRemoteScreen(
                    wsClient = wsClient,
                    onBack = { currentScreen = ScreenDestination.DEVICE_DETAIL }
                )
            }

            ScreenDestination.MEDIA_CONTROL -> {
                MediaControlScreen(
                    wsClient = wsClient,
                    mediaController = mediaController,
                    onBack = { currentScreen = ScreenDestination.DEVICE_DETAIL }
                )
            }

            ScreenDestination.TOUCHPAD -> {
                TouchpadScreen(
                    wsClient = wsClient,
                    onBack = { currentScreen = ScreenDestination.DEVICE_DETAIL }
                )
            }

            ScreenDestination.COMMANDS -> {
                RemoteCommandsScreen(
                    wsClient = wsClient,
                    onBack = { currentScreen = ScreenDestination.DEVICE_DETAIL }
                )
            }

            ScreenDestination.FILES -> {
                FileClipboardScreen(
                    clipboardManager = clipboardManager,
                    fileTransferManager = fileTransferManager,
                    wsClient = wsClient,
                    onBack = { currentScreen = ScreenDestination.DEVICE_DETAIL }
                )
            }

            ScreenDestination.PLUGINS -> {
                PluginsScreen(
                    onOpenDrawer = { scope.launch { drawerState.open() } },
                    onBack = { currentScreen = ScreenDestination.DEVICE_DETAIL }
                )
            }

            ScreenDestination.SETTINGS -> {
                SettingsScreen(
                    wsClient = wsClient,
                    onOpenDrawer = { scope.launch { drawerState.open() } },
                    onBack = { currentScreen = ScreenDestination.DEVICE_LIST }
                )
            }

            ScreenDestination.ABOUT -> {
                AboutScreen(
                    onOpenDrawer = { scope.launch { drawerState.open() } },
                    onBack = { currentScreen = ScreenDestination.DEVICE_LIST }
                )
            }
        }
    }

    if (incomingPairingRequest != null) {
        val req = incomingPairingRequest!!
        AlertDialog(
            onDismissRequest = {
                wsClient.rejectPairing(req)
            },
            title = {
                Text(
                    text = "Eşleşme İsteği Alındı",
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
            },
            text = {
                Text(
                    text = "${req.deviceName} sizinle eşleşmek istiyor. Kabul edilsin mi?",
                    color = TextSecondary
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        wsClient.acceptPairing(req)
                        Toast.makeText(context, "${req.deviceName} ile eşleşildi!", Toast.LENGTH_SHORT).show()
                        selectedDevice = DiscoveredPc(wsClient.serverAddress.value.substringBefore(":"), req.deviceName, 8765)
                        currentScreen = ScreenDestination.DEVICE_DETAIL
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = FluentBlue)
                ) {
                    Text("Kabul Et", color = Color.White)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = {
                        wsClient.rejectPairing(req)
                    }
                ) {
                    Text("Reddet", color = TextSecondary)
                }
            },
            containerColor = DarkSurface,
            shape = RoundedCornerShape(16.dp)
        )
    }
}