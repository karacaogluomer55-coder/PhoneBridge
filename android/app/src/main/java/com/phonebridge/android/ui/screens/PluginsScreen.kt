package com.phonebridge.android.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Mouse
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.phonebridge.android.ui.theme.DarkCardBorder
import com.phonebridge.android.ui.theme.DarkSurface
import com.phonebridge.android.ui.theme.TextPrimary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PluginsScreen(
    onOpenDrawer: () -> Unit,
    onBack: (() -> Unit)? = null
) {
    var notifSyncEnabled by remember { mutableStateOf(true) }
    var clipboardSyncEnabled by remember { mutableStateOf(true) }
    var callSyncEnabled by remember { mutableStateOf(true) }
    var mediaControlEnabled by remember { mutableStateOf(true) }
    var fileShareEnabled by remember { mutableStateOf(true) }
    var remoteInputEnabled by remember { mutableStateOf(true) }
    var remoteCommandsEnabled by remember { mutableStateOf(true) }
    var phoneFinderEnabled by remember { mutableStateOf(true) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Eklenti Ayarları",
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
                SectionHeader(title = "Aktif Eklentiler")
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
                            title = "Bildirim Senkronizasyonu",
                            subtitle = "Telefon bildirimlerini PC'ye yansıtın ve doğrudan yanıtlayın.",
                            icon = Icons.Default.Notifications,
                            checked = notifSyncEnabled,
                            onCheckedChange = { notifSyncEnabled = it }
                        )

                        DividerLight()

                        SettingsSwitchRow(
                            title = "Pano Eşitleme",
                            subtitle = "Kopyalanan metinleri telefon ve PC arasında otomatik paylaşın.",
                            icon = Icons.Default.ContentPaste,
                            checked = clipboardSyncEnabled,
                            onCheckedChange = { clipboardSyncEnabled = it }
                        )

                        DividerLight()

                        SettingsSwitchRow(
                            title = "Telefon Arama & Arayan Kimliği",
                            subtitle = "Gelen aramaları bilgisayarda görün, yanıtlayın veya reddedin.",
                            icon = Icons.Default.Call,
                            checked = callSyncEnabled,
                            onCheckedChange = { callSyncEnabled = it }
                        )

                        DividerLight()

                        SettingsSwitchRow(
                            title = "Çoklu Ortam Denetimi",
                            subtitle = "Bilgisayardaki müzik ve videoları telefondan uzaktan kontrol edin.",
                            icon = Icons.Default.PlayCircle,
                            checked = mediaControlEnabled,
                            onCheckedChange = { mediaControlEnabled = it }
                        )

                        DividerLight()

                        SettingsSwitchRow(
                            title = "Dosya Aktarımı",
                            subtitle = "Tek dokunuşla fotoğraf ve belgeleri bilgisayara gönderin.",
                            icon = Icons.Default.Description,
                            checked = fileShareEnabled,
                            onCheckedChange = { fileShareEnabled = it }
                        )

                        DividerLight()

                        SettingsSwitchRow(
                            title = "Sanal Giriş ve Dokunmatik Yüzey",
                            subtitle = "Telefon ekranını bilgisayarınız için kablosuz mouse ve klavye yapın.",
                            icon = Icons.Default.Mouse,
                            checked = remoteInputEnabled,
                            onCheckedChange = { remoteInputEnabled = it }
                        )

                        DividerLight()

                        SettingsSwitchRow(
                            title = "Uzaktan Komutlar",
                            subtitle = "Bilgisayarı kilitleyin, uyutun veya sesini tek tıkla kapatın.",
                            icon = Icons.Default.FlashOn,
                            checked = remoteCommandsEnabled,
                            onCheckedChange = { remoteCommandsEnabled = it }
                        )

                        DividerLight()

                        SettingsSwitchRow(
                            title = "Aygıtı Çaldır (Zil Sesi)",
                            subtitle = "Kayıp aygıtınızı bulmak için karşı cihazda yüksek sesli zil çalın.",
                            icon = Icons.Default.NotificationsActive,
                            checked = phoneFinderEnabled,
                            onCheckedChange = { phoneFinderEnabled = it }
                        )
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}