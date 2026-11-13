package com.phonebridge.android.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BrightnessMedium
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.phonebridge.android.data.PreferencesManager
import com.phonebridge.android.network.PhoneBridgeWebSocketClient
import com.phonebridge.android.service.PhoneBridgeForegroundService
import com.phonebridge.android.ui.theme.DarkCardBorder
import com.phonebridge.android.ui.theme.DarkSurface
import com.phonebridge.android.ui.theme.DarkSurfaceVariant
import com.phonebridge.android.ui.theme.FluentBlue
import com.phonebridge.android.ui.theme.TextMuted
import com.phonebridge.android.ui.theme.TextPrimary
import com.phonebridge.android.ui.theme.TextSecondary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    wsClient: PhoneBridgeWebSocketClient,
    onOpenDrawer: () -> Unit,
    onBack: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val prefs = remember { PreferencesManager(context) }

    var deviceNameState by remember { mutableStateOf(prefs.effectiveDeviceName) }
    var themeModeState by remember { mutableStateOf(prefs.themeMode) }
    var persistentNotifState by remember { mutableStateOf(prefs.persistentNotification) }
    var bluetoothState by remember { mutableStateOf(prefs.bluetoothEnabled) }

    var showDeviceNameDialog by remember { mutableStateOf(false) }
    var showThemeDialog by remember { mutableStateOf(false) }
    var showManualIpDialog by remember { mutableStateOf(false) }
    var showTrustedNetworksDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Ayarlar",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Geri",
                                tint = TextPrimary
                            )
                        }
                    } else {
                        IconButton(onClick = onOpenDrawer) {
                            Icon(
                                imageVector = Icons.Default.Menu,
                                contentDescription = "Menü",
                                tint = TextPrimary
                            )
                        }
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
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                SectionHeader(title = "Aygıt ve Görünüm")
            }

            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .border(1.dp, DarkCardBorder, RoundedCornerShape(16.dp)),
                    colors = CardDefaults.cardColors(containerColor = DarkSurface)
                ) {
                    Column {
                        SettingsClickableRow(
                            title = "Aygıt adı",
                            subtitle = deviceNameState,
                            icon = Icons.Default.PhoneAndroid,
                            onClick = { showDeviceNameDialog = true }
                        )

                        DividerLight()

                        val themeLabel = when (themeModeState) {
                            "dark" -> "Koyu Tema"
                            "light" -> "Açık Tema"
                            else -> "Sistem Varsayılanı"
                        }
                        SettingsClickableRow(
                            title = "Tema seçimi",
                            subtitle = themeLabel,
                            icon = Icons.Default.BrightnessMedium,
                            onClick = { showThemeDialog = true }
                        )
                    }
                }
            }

            item {
                SectionHeader(title = "Bağlantı ve Bildirimler")
            }

            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .border(1.dp, DarkCardBorder, RoundedCornerShape(16.dp)),
                    colors = CardDefaults.cardColors(containerColor = DarkSurface)
                ) {
                    Column {
                        SettingsSwitchRow(
                            title = "Kalıcı bildirim",
                            subtitle = "Arka planda bağlantıyı canlı tutmak için ön plan bildirimini gösterir.",
                            icon = Icons.Default.Notifications,
                            checked = persistentNotifState,
                            onCheckedChange = { checked ->
                                persistentNotifState = checked
                                prefs.persistentNotification = checked
                                if (checked) {
                                    PhoneBridgeForegroundService.startService(context)
                                } else {
                                    PhoneBridgeForegroundService.stopService(context)
                                }
                            }
                        )

                        DividerLight()

                        val trustedCount = prefs.trustedNetworks.size
                        SettingsClickableRow(
                            title = "Güvenilir ağlar",
                            subtitle = if (trustedCount > 0) "$trustedCount kayıtlı Wi-Fi ağı" else "Tüm ağlarda bağlantıya izin ver",
                            icon = Icons.Default.Wifi,
                            onClick = { showTrustedNetworksDialog = true }
                        )

                        DividerLight()

                        SettingsClickableRow(
                            title = "IP'ye göre aygıtları ekle",
                            subtitle = "Gelişmiş: UDP keşfi engellenen ağlar için manuel IP girişi",
                            icon = Icons.Default.Router,
                            onClick = { showManualIpDialog = true }
                        )

                        DividerLight()

                        SettingsSwitchRow(
                            title = "Bluetooth",
                            subtitle = "Yakındaki Bluetooth cihazlarıyla yardımcı iletişim kur.",
                            icon = Icons.Default.Bluetooth,
                            checked = bluetoothState,
                            onCheckedChange = { checked ->
                                bluetoothState = checked
                                prefs.bluetoothEnabled = checked
                            }
                        )
                    }
                }
            }

            item {
                SectionHeader(title = "Gelişmiş ve Tanılama")
            }

            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .border(1.dp, DarkCardBorder, RoundedCornerShape(16.dp)),
                    colors = CardDefaults.cardColors(containerColor = DarkSurface)
                ) {
                    SettingsClickableRow(
                        title = "Günlükleri Dışa Aktar",
                        subtitle = "Bağlantı ve tanılama kayıtlarını panoya kopyala / paylaş",
                        icon = Icons.Default.FileDownload,
                        onClick = {
                            val diagnosticLog = buildString {
                                appendLine("=== PhoneBridge Android Companion Diagnostic Log ===")
                                appendLine("Device Name: ${prefs.effectiveDeviceName}")
                                appendLine("Device ID: ${prefs.deviceId}")
                                appendLine("Active Server: ${wsClient.serverAddress.value}")
                                appendLine("Connection State: ${wsClient.connectionState.value}")
                                appendLine("Latency: ${wsClient.latencyMs.value}ms")
                                appendLine("Paired Devices: ${prefs.getPairedDevices().size}")
                                appendLine("Android OS: ${android.os.Build.VERSION.RELEASE} (API ${android.os.Build.VERSION.SDK_INT})")
                                appendLine("Manufacturer: ${android.os.Build.MANUFACTURER} | Model: ${android.os.Build.MODEL}")
                            }

                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val clip = ClipData.newPlainText("PhoneBridge Diagnostic Logs", diagnosticLog)
                            clipboard.setPrimaryClip(clip)

                            Toast.makeText(context, "Tanılama günlükleri panoya kopyalandı", Toast.LENGTH_LONG).show()

                            try {
                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_SUBJECT, "PhoneBridge Diagnostic Log")
                                    putExtra(Intent.EXTRA_TEXT, diagnosticLog)
                                }
                                context.startActivity(Intent.createChooser(shareIntent, "Günlükleri Paylaş"))
                            } catch (e: Exception) {
                            }
                        }
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }

    if (showDeviceNameDialog) {
        var tempName by remember { mutableStateOf(prefs.effectiveDeviceName) }

        AlertDialog(
            onDismissRequest = { showDeviceNameDialog = false },
            title = {
                Text("Aygıt Adını Düzenle", fontWeight = FontWeight.Bold, color = TextPrimary)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Bilgisayarınızda ve diğer aygıtlarda görünecek adı belirleyin:",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                    OutlinedTextField(
                        value = tempName,
                        onValueChange = { tempName = it },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = FluentBlue,
                            unfocusedBorderColor = DarkCardBorder
                        )
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val trimmed = tempName.trim()
                        if (trimmed.isNotBlank()) {
                            prefs.customDeviceName = trimmed
                            deviceNameState = trimmed
                            wsClient.sendDeviceStatus()
                        }
                        showDeviceNameDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = FluentBlue)
                ) {
                    Text("Kaydet")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showDeviceNameDialog = false }) {
                    Text("İptal")
                }
            },
            containerColor = DarkSurface,
            shape = RoundedCornerShape(16.dp)
        )
    }

    if (showThemeDialog) {
        val themes = listOf(
            "dark" to "Koyu Tema (Önerilen)",
            "light" to "Açık Tema",
            "system" to "Sistem Varsayılanı"
        )

        AlertDialog(
            onDismissRequest = { showThemeDialog = false },
            title = {
                Text("Tema Seçimi", fontWeight = FontWeight.Bold, color = TextPrimary)
            },
            text = {
                Column {
                    themes.forEach { (key, label) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    themeModeState = key
                                    prefs.themeMode = key
                                    showThemeDialog = false
                                }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = themeModeState == key,
                                onClick = {
                                    themeModeState = key
                                    prefs.themeMode = key
                                    showThemeDialog = false
                                },
                                colors = RadioButtonDefaults.colors(selectedColor = FluentBlue)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = label, color = TextPrimary)
                        }
                    }
                }
            },
            confirmButton = {
                OutlinedButton(onClick = { showThemeDialog = false }) {
                    Text("Kapat")
                }
            },
            containerColor = DarkSurface,
            shape = RoundedCornerShape(16.dp)
        )
    }

    if (showManualIpDialog) {
        var ipInput by remember { mutableStateOf(prefs.pcIp.ifBlank { "192.168.1." }) }
        var portInput by remember { mutableStateOf(prefs.pcPort.toString()) }

        AlertDialog(
            onDismissRequest = { showManualIpDialog = false },
            title = {
                Text("IP'ye Göre Aygıt Ekle", fontWeight = FontWeight.Bold, color = TextPrimary)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Hedef bilgisayarın statik IP adresini girin:",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                    OutlinedTextField(
                        value = ipInput,
                        onValueChange = { ipInput = it },
                        label = { Text("IP Adresi") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = portInput,
                        onValueChange = { portInput = it },
                        label = { Text("Port (8765)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val port = portInput.toIntOrNull() ?: 8765
                        wsClient.initiatePairing(ipInput.trim(), port, "PC ($ipInput)")
                        showManualIpDialog = false
                        Toast.makeText(context, "$ipInput adresine bağlanılıyor...", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = FluentBlue)
                ) {
                    Text("Bağlan")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showManualIpDialog = false }) {
                    Text("İptal")
                }
            },
            containerColor = DarkSurface,
            shape = RoundedCornerShape(16.dp)
        )
    }

    if (showTrustedNetworksDialog) {
        var newSsid by remember { mutableStateOf("") }
        var currentTrusted by remember { mutableStateOf(prefs.trustedNetworks.toList()) }

        AlertDialog(
            onDismissRequest = { showTrustedNetworksDialog = false },
            title = {
                Text("Güvenilir Wi-Fi Ağları", fontWeight = FontWeight.Bold, color = TextPrimary)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "PhoneBridge'in yalnızca belirlenen güvenli ev/ofis Wi-Fi ağlarında otomatik bağlanmasını sağlayabilirsiniz.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )

                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = newSsid,
                            onValueChange = { newSsid = it },
                            label = { Text("Wi-Fi SSID (Ağ Adı)") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                if (newSsid.isNotBlank()) {
                                    prefs.addTrustedNetwork(newSsid.trim())
                                    currentTrusted = prefs.trustedNetworks.toList()
                                    newSsid = ""
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = FluentBlue)
                        ) {
                            Text("Ekle")
                        }
                    }

                    if (currentTrusted.isNotEmpty()) {
                        Text(
                            text = "Kayıtlı Ağlar:",
                            fontWeight = FontWeight.SemiBold,
                            color = TextPrimary,
                            style = MaterialTheme.typography.bodySmall
                        )
                        currentTrusted.forEach { ssid ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("• $ssid", color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
                                OutlinedButton(onClick = {
                                    prefs.removeTrustedNetwork(ssid)
                                    currentTrusted = prefs.trustedNetworks.toList()
                                }) {
                                    Text("Kaldır", fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { showTrustedNetworksDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = FluentBlue)
                ) {
                    Text("Tamam")
                }
            },
            containerColor = DarkSurface,
            shape = RoundedCornerShape(16.dp)
        )
    }
}