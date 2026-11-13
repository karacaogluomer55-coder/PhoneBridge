package com.phonebridge.android.ui.screens

import android.content.Intent
import android.net.Uri
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Share
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
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.phonebridge.android.ui.theme.DarkCardBorder
import com.phonebridge.android.ui.theme.DarkSurface
import com.phonebridge.android.ui.theme.DarkSurfaceVariant
import com.phonebridge.android.ui.theme.FluentBlue
import com.phonebridge.android.ui.theme.TextMuted
import com.phonebridge.android.ui.theme.TextPrimary
import com.phonebridge.android.ui.theme.TextSecondary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(
    onOpenDrawer: () -> Unit,
    onBack: (() -> Unit)? = null
) {
    val context = LocalContext.current
    var showLicensesDialog by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }

    fun openUrl(url: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            context.startActivity(intent)
        } catch (e: Exception) {
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Hakkında",
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
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp)),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF262628))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(50.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .border(1.5.dp, Color.White.copy(alpha = 0.8f), RoundedCornerShape(12.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.PhoneAndroid,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(28.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(16.dp))

                        Column {
                            Text(
                                text = "PhoneBridge",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Sürüm 1.36.0",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.White.copy(alpha = 0.7f)
                            )
                        }
                    }
                }
            }

            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(22.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceAround
                    ) {
                        AboutGridActionItem(
                            title = "Hata Bildir",
                            icon = Icons.Default.BugReport,
                            onClick = { openUrl("https://github.com/karacaogluomer55-coder/PhoneBridge/issues") }
                        )
                        AboutGridActionItem(
                            title = "Bağış yap",
                            icon = Icons.Default.AttachMoney,
                            onClick = { openUrl("https://github.com/sponsors/karacaogluomer55-coder") }
                        )
                        AboutGridActionItem(
                            title = "Kaynak Kod",
                            icon = Icons.Default.Code,
                            onClick = { openUrl("https://github.com/karacaogluomer55-coder/PhoneBridge") }
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceAround
                    ) {
                        AboutGridActionItem(
                            title = "Lisanslar",
                            icon = Icons.Default.Description,
                            onClick = { showLicensesDialog = true }
                        )
                        AboutGridActionItem(
                            title = "PhoneBridge Hakkında",
                            icon = Icons.Default.Info,
                            onClick = { showAboutDialog = true }
                        )
                        AboutGridActionItem(
                            title = "Web Sitesi",
                            icon = Icons.Default.Language,
                            onClick = { openUrl("https://github.com/karacaogluomer55-coder/PhoneBridge") }
                        )
                    }
                }
            }

            item {
                Text(
                    text = "Katkıda Bulunanlar (Contributors)",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier.padding(top = 10.dp, bottom = 4.dp)
                )
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    AuthorEntry(name = "Ömer Faruk Karaca", role = "Kurucu ve Baş Geliştirici (WinUI 3 & Android)")
                    AuthorEntry(name = "PhoneBridge Topluluğu", role = "Açık Kaynak Katkıda Bulunanlar (Contributors)")
                }
            }

            item {
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }

    if (showLicensesDialog) {
        AlertDialog(
            onDismissRequest = { showLicensesDialog = false },
            title = {
                Text("Açık Kaynak Lisansları", fontWeight = FontWeight.Bold, color = TextPrimary)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "PhoneBridge, MIT Lisansı kapsamında yayımlanan açık kaynaklı bir yazılımdır.",
                        color = TextSecondary,
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = "Kullanılan Başlıca Kütüphaneler:",
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary,
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = "• Jetpack Compose & Material 3 (Apache 2.0)\n• OkHttp 4 (Apache 2.0)\n• Google Gson (Apache 2.0)\n• ZXing QR Core (Apache 2.0)\n• KotlinX Coroutines (Apache 2.0)",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMuted
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = { showLicensesDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = FluentBlue)
                ) {
                    Text("Kapat")
                }
            },
            containerColor = DarkSurface,
            shape = RoundedCornerShape(16.dp)
        )
    }

    if (showAboutDialog) {
        AlertDialog(
            onDismissRequest = { showAboutDialog = false },
            title = {
                Text("PhoneBridge Projesi", fontWeight = FontWeight.Bold, color = TextPrimary)
            },
            text = {
                Text(
                    text = "PhoneBridge, telefonunuz ile Windows bilgisayarınız arasındaki iletişimi en az gecikmeyle sağlayan güçlü bir çözümdür. Bildirim senkronizasyonu, uzaktan kumanda, slayt sunum kontrolleri, çoklu ortam denetimi, dosya ve pano paylaşımı gibi özellikleri tek çatı altında sunar.",
                    color = TextSecondary,
                    style = MaterialTheme.typography.bodySmall
                )
            },
            confirmButton = {
                Button(
                    onClick = { showAboutDialog = false },
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

@Composable
fun AboutActionRow(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(DarkSurfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = FluentBlue,
                modifier = Modifier.size(20.dp)
            )
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )
        }

        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
            contentDescription = null,
            tint = TextMuted,
            modifier = Modifier.size(14.dp)
        )
    }
}

@Composable
fun DeveloperItem(
    name: String,
    role: String,
    desc: String
) {
    Column {
        Text(
            text = name,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Bold,
            color = TextPrimary
        )
        Text(
            text = role,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            color = FluentBlue
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = desc,
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary
        )
    }
}

@Composable
fun AboutGridActionItem(
    title: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = title,
            tint = Color.White.copy(alpha = 0.9f),
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Normal,
            color = Color.White.copy(alpha = 0.9f),
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun AuthorEntry(
    name: String,
    role: String
) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(
            text = name,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            color = Color.White
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = role,
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.65f)
        )
    }
}

@Composable
fun DividerLight() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(DarkCardBorder.copy(alpha = 0.5f))
    )
}