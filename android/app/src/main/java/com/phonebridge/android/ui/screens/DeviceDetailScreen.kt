package com.phonebridge.android.ui.screens

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CoPresent
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Mouse
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.phonebridge.android.data.DiscoveredPc
import com.phonebridge.android.data.PacketTypes
import com.phonebridge.android.data.PreferencesManager
import com.phonebridge.android.data.RingActionPayload
import com.phonebridge.android.network.PhoneBridgeWebSocketClient
import com.phonebridge.android.service.ClipboardSyncManager
import com.phonebridge.android.service.FileTransferManager
import com.phonebridge.android.service.FileTransferStatus
import com.phonebridge.android.ui.theme.DarkCardBorder
import com.phonebridge.android.ui.theme.DarkSurface
import com.phonebridge.android.ui.theme.DarkSurfaceVariant
import com.phonebridge.android.ui.theme.FluentBlue
import com.phonebridge.android.ui.theme.StatusConnected
import com.phonebridge.android.ui.theme.StatusError
import com.phonebridge.android.ui.theme.StatusWarning
import com.phonebridge.android.ui.theme.TextMuted
import com.phonebridge.android.ui.theme.TextPrimary
import com.phonebridge.android.ui.theme.TextSecondary
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceDetailScreen(
    device: DiscoveredPc,
    wsClient: PhoneBridgeWebSocketClient,
    clipboardManager: ClipboardSyncManager,
    fileTransferManager: FileTransferManager,
    onBack: () -> Unit,
    onOpenDrawer: () -> Unit,
    onNavigatePresentation: () -> Unit,
    onNavigateMedia: () -> Unit,
    onNavigateTouchpad: () -> Unit,
    onNavigateCommands: () -> Unit,
    onNavigatePlugins: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember { PreferencesManager(context) }
    val transferStatus by fileTransferManager.transferStatus.collectAsState()

    var showMenu by remember { mutableStateOf(false) }
    var showEncryptionDialog by remember { mutableStateOf(false) }
    var showUnpairConfirmDialog by remember { mutableStateOf(false) }
    var showPermissionInfoDialog by remember { mutableStateOf(false) }
    var isRingingPc by remember { mutableStateOf(false) }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            scope.launch {
                fileTransferManager.uploadFile(it)
            }
            Toast.makeText(context, "Dosya aktarımı başlatıldı...", Toast.LENGTH_SHORT).show()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = device.name,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Geri",
                            tint = TextPrimary
                        )
                    }
                },
                actions = {
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
                            text = { Text("Aygıtı Çaldır (PC Zil)") },
                            leadingIcon = { Icon(Icons.Default.NotificationsActive, contentDescription = null) },
                            onClick = {
                                showMenu = false
                                isRingingPc = !isRingingPc
                                val action = if (isRingingPc) "START" else "STOP"
                                wsClient.sendPacket(PacketTypes.RING_PC, RingActionPayload(action))
                                Toast.makeText(
                                    context,
                                    if (isRingingPc) "PC'de zil sesi başlatıldı" else "PC zili durduruldu",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Şifreleme Bilgisi") },
                            leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                            onClick = {
                                showMenu = false
                                showEncryptionDialog = true
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Eklenti Ayarları") },
                            leadingIcon = { Icon(Icons.Default.Settings, contentDescription = null) },
                            onClick = {
                                showMenu = false
                                onNavigatePlugins()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Eşleşmeyi Kaldır", color = StatusError) },
                            leadingIcon = { Icon(Icons.Default.Warning, contentDescription = null, tint = StatusError) },
                            onClick = {
                                showMenu = false
                                showUnpairConfirmDialog = true
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            AnimatedVisibility(visible = transferStatus is FileTransferStatus.Uploading) {
                if (transferStatus is FileTransferStatus.Uploading) {
                    val status = transferStatus as FileTransferStatus.Uploading
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp)
                            .clip(RoundedCornerShape(12.dp)),
                        colors = CardDefaults.cardColors(containerColor = DarkSurface)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Gönderiliyor: ${status.fileName}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextPrimary
                                )
                                Text(
                                    text = "%${(status.progress * 100).toInt()}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = FluentBlue
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            LinearProgressIndicator(
                                progress = { status.progress },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(4.dp)
                                    .clip(RoundedCornerShape(2.dp)),
                                color = FluentBlue,
                                trackColor = DarkSurfaceVariant
                            )
                        }
                    }
                }
            }

            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                item {
                    BridgeActionGridCard(
                        title = "Dosyaları gönder",
                        icon = Icons.Default.Description,
                        onClick = {
                            filePickerLauncher.launch("*/*")
                        }
                    )
                }

                item {
                    BridgeActionGridCard(
                        title = "Pano Gönder",
                        icon = Icons.Default.ContentPaste,
                        onClick = {
                            val (sent, msg) = clipboardManager.sendClipboardToPc()
                            if (!sent) {
                                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                            }
                        }
                    )
                }

                item {
                    BridgeActionGridCard(
                        title = "Sunum uzaktan\nkumandası",
                        icon = Icons.Default.Upload,
                        onClick = onNavigatePresentation
                    )
                }

                item {
                    BridgeActionGridCard(
                        title = "Çoklu ortam denetimi",
                        icon = Icons.Default.PlayArrow,
                        onClick = onNavigateMedia
                    )
                }

                item {
                    BridgeActionGridCard(
                        title = "Uzaktan giriş",
                        icon = Icons.Default.TouchApp,
                        onClick = onNavigateTouchpad
                    )
                }

                item {
                    BridgeActionGridCard(
                        title = "Komut Çalıştır",
                        icon = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        onClick = onNavigateCommands
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 8.dp)
                    .clickable { showPermissionInfoDialog = true }
            ) {
                Text(
                    text = "Bazı Eklentiler çalışmak için izne gereksinim duyar (daha fazla bilgi için dokunun):",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.75f),
                    lineHeight = 18.sp
                )
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = "Kişiler Eşzamanlayıcısı",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Normal,
                    color = Color.White.copy(alpha = 0.95f)
                )
            }
        }
    }

    if (showEncryptionDialog) {
        AlertDialog(
            onDismissRequest = { showEncryptionDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Lock, contentDescription = null, tint = StatusConnected)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Şifreleme & Bağlantı Bilgisi", fontWeight = FontWeight.Bold, color = TextPrimary)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Aygıt: ${device.name}",
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary
                    )
                    Text(
                        text = "IP Adresi: ${device.ip}:${device.port}",
                        color = TextSecondary
                    )
                    Text(
                        text = "Protokol: PhoneBridge Yerel WebSocket & TLS",
                        color = TextSecondary
                    )
                    Text(
                        text = "Cihaz Kimliği: ${prefs.deviceId}",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMuted
                    )
                    Text(
                        text = "Durum: Şifreli yerel ağ tüneli aktif.",
                        style = MaterialTheme.typography.bodySmall,
                        color = StatusConnected
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = { showEncryptionDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = FluentBlue)
                ) {
                    Text("Tamam")
                }
            },
            containerColor = DarkSurface,
            shape = RoundedCornerShape(16.dp)
        )
    }

    if (showUnpairConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showUnpairConfirmDialog = false },
            title = {
                Text("Eşleşmeyi Kaldır", fontWeight = FontWeight.Bold, color = TextPrimary)
            },
            text = {
                Text(
                    text = "${device.name} aygıtı ile eşleşmeyi kaldırmak istediğinizden emin misiniz? Bağlantı kesilecek.",
                    color = TextSecondary
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        wsClient.unpairCurrentDevice()
                        showUnpairConfirmDialog = false
                        onBack()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = StatusError)
                ) {
                    Text("Eşleşmeyi Kaldır")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showUnpairConfirmDialog = false }) {
                    Text("İptal")
                }
            },
            containerColor = DarkSurface,
            shape = RoundedCornerShape(16.dp)
        )
    }

    if (showPermissionInfoDialog) {
        AlertDialog(
            onDismissRequest = { showPermissionInfoDialog = false },
            title = {
                Text("Gerekli İzinler", fontWeight = FontWeight.Bold, color = TextPrimary)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "PhoneBridge özelliklerinin eksiksiz çalışabilmesi için aşağıdaki sistem izinleri gereklidir:",
                        color = TextSecondary
                    )
                    Text(
                        text = "• Bildirim Erişimi: PC'de telefon bildirimlerini görmek ve doğrudan yanıtlamak için.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextPrimary
                    )
                    Text(
                        text = "• Telefon & Kişiler İzni: Gelen çağrıları PC'de yanıtlamak ve kişi adlarını eşleştirmek için.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextPrimary
                    )
                    Text(
                        text = "• Pil Optimizasyonu: Arka planda kesintisiz bağlı kalabilmek için.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextPrimary
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showPermissionInfoDialog = false
                        try {
                            val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                data = Uri.fromParts("package", context.packageName, null)
                            }
                            context.startActivity(intent)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = FluentBlue)
                ) {
                    Text("İzinleri Aç")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showPermissionInfoDialog = false }) {
                    Text("Kapat")
                }
            },
            containerColor = DarkSurface,
            shape = RoundedCornerShape(16.dp)
        )
    }
}

@Composable
fun BridgeActionGridCard(
    title: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(118.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF262628))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.Start
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = Color.White.copy(alpha = 0.95f),
                modifier = Modifier.size(26.dp)
            )

            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = Color.White.copy(alpha = 0.95f),
                textAlign = TextAlign.Start,
                maxLines = 2,
                lineHeight = 18.sp
            )
        }
    }
}