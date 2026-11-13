package com.phonebridge.android.ui.screens

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeDown
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.phonebridge.android.data.MediaCommandPayload
import com.phonebridge.android.data.PacketTypes
import com.phonebridge.android.network.PhoneBridgeWebSocketClient
import com.phonebridge.android.service.PhoneBridgeMediaController
import com.phonebridge.android.ui.theme.DarkCardBorder
import com.phonebridge.android.ui.theme.DarkSurface
import com.phonebridge.android.ui.theme.DarkSurfaceVariant
import com.phonebridge.android.ui.theme.FluentBlue
import com.phonebridge.android.ui.theme.TextMuted
import com.phonebridge.android.ui.theme.TextPrimary
import com.phonebridge.android.ui.theme.TextSecondary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaControlScreen(
    wsClient: PhoneBridgeWebSocketClient,
    mediaController: PhoneBridgeMediaController,
    onBack: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val mediaState by mediaController.currentMediaState.collectAsState()

    val isPlaying = mediaState?.isPlaying == true
    val titleText = mediaState?.title?.ifBlank { "Telefonda Parça Çalmıyor" } ?: "Telefonda Parça Çalmıyor"
    val artistText = mediaState?.artist?.ifBlank { "Telefonda Müzik Başlatın" } ?: "Telefonda Müzik Başlatın"
    val albumText = mediaState?.album ?: ""
    val durationMs = mediaState?.duration?.coerceAtLeast(1L) ?: 1L
    val positionMs = mediaState?.position ?: 0L

    var localVolume by remember { mutableFloatStateOf(mediaState?.volume?.toFloat() ?: 50f) }

    val albumArtBitmap = remember(mediaState?.albumArtBase64) {
        mediaState?.albumArtBase64?.let { base64Str ->
            try {
                val bytes = Base64.decode(base64Str, Base64.DEFAULT)
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
            } catch (e: Exception) {
                null
            }
        }
    }

    fun sendMediaCommand(cmd: String, value: Int? = null) {
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        val payload = MediaCommandPayload(command = cmd, value = value)
        wsClient.sendPacket(PacketTypes.MEDIA_COMMAND, payload)
    }

    fun formatDuration(ms: Long): String {
        if (ms <= 0) return "00:00"
        val totalSecs = ms / 1000
        val mins = totalSecs / 60
        val secs = totalSecs % 60
        return "%02d:%02d".format(mins, secs)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Çoklu Ortam Denetimi",
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
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(28.dp))
                    .border(1.dp, FluentBlue.copy(alpha = 0.25f), RoundedCornerShape(28.dp)),
                colors = CardDefaults.cardColors(
                    containerColor = DarkSurfaceVariant
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(FluentBlue.copy(alpha = 0.15f))
                            .padding(horizontal = 14.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Devices,
                            contentDescription = null,
                            tint = FluentBlue,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = if (!mediaState?.sourceApp.isNullOrBlank()) "Çalan: ${mediaState?.sourceApp}" else "Telefon Medyası",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = FluentBlue
                        )
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    Box(
                        modifier = Modifier
                            .size(220.dp)
                            .clip(RoundedCornerShape(24.dp))
                            .background(
                                Brush.verticalGradient(
                                    listOf(DarkSurface, FluentBlue.copy(alpha = 0.2f))
                                )
                            )
                            .border(1.5.dp, DarkCardBorder, RoundedCornerShape(24.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (albumArtBitmap != null) {
                            Image(
                                bitmap = albumArtBitmap,
                                contentDescription = "Albüm Kapağı",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.MusicNote,
                                contentDescription = null,
                                tint = FluentBlue,
                                modifier = Modifier.size(80.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    Text(
                        text = titleText,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = if (albumText.isNotBlank()) "$artistText • $albumText" else artistText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Column(modifier = Modifier.fillMaxWidth()) {
                        val progressFraction = if (durationMs > 0) (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f) else 0f
                        Slider(
                            value = progressFraction,
                            onValueChange = {},
                            enabled = false,
                            modifier = Modifier.fillMaxWidth(),
                            colors = SliderDefaults.colors(
                                thumbColor = FluentBlue,
                                activeTrackColor = FluentBlue,
                                inactiveTrackColor = DarkCardBorder
                            )
                        )

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = formatDuration(positionMs),
                                style = MaterialTheme.typography.labelSmall,
                                color = TextMuted
                            )
                            Text(
                                text = formatDuration(durationMs),
                                style = MaterialTheme.typography.labelSmall,
                                color = TextMuted
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { sendMediaCommand("PREV") }) {
                            Icon(
                                imageVector = Icons.Default.Shuffle,
                                contentDescription = "Karıştır",
                                tint = TextMuted,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        IconButton(
                            onClick = { sendMediaCommand("PREV") },
                            modifier = Modifier
                                .size(50.dp)
                                .clip(CircleShape)
                                .background(DarkSurface)
                        ) {
                            Icon(
                                imageVector = Icons.Default.SkipPrevious,
                                contentDescription = "Önceki Parça",
                                tint = TextPrimary,
                                modifier = Modifier.size(28.dp)
                            )
                        }

                        IconButton(
                            onClick = {
                                val cmd = if (isPlaying) "PAUSE" else "PLAY"
                                sendMediaCommand(cmd)
                            },
                            modifier = Modifier
                                .size(72.dp)
                                .clip(CircleShape)
                                .background(FluentBlue)
                        ) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = "Oynat / Duraklat",
                                tint = Color.White,
                                modifier = Modifier.size(38.dp)
                            )
                        }

                        IconButton(
                            onClick = { sendMediaCommand("NEXT") },
                            modifier = Modifier
                                .size(50.dp)
                                .clip(CircleShape)
                                .background(DarkSurface)
                        ) {
                            Icon(
                                imageVector = Icons.Default.SkipNext,
                                contentDescription = "Sonraki Parça",
                                tint = TextPrimary,
                                modifier = Modifier.size(28.dp)
                            )
                        }

                        IconButton(onClick = { sendMediaCommand("NEXT") }) {
                            Icon(
                                imageVector = Icons.Default.Repeat,
                                contentDescription = "Tekrarla",
                                tint = TextMuted,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .border(1.dp, DarkCardBorder, RoundedCornerShape(20.dp)),
                colors = CardDefaults.cardColors(
                    containerColor = DarkSurfaceVariant
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.VolumeDown,
                        contentDescription = "Ses Azalt",
                        tint = TextSecondary,
                        modifier = Modifier.size(22.dp)
                    )

                    Slider(
                        value = localVolume,
                        onValueChange = { localVolume = it },
                        onValueChangeFinished = {
                            sendMediaCommand("SET_VOLUME", localVolume.toInt())
                        },
                        valueRange = 0f..100f,
                        modifier = Modifier.weight(1f),
                        colors = SliderDefaults.colors(
                            thumbColor = FluentBlue,
                            activeTrackColor = FluentBlue,
                            inactiveTrackColor = DarkCardBorder
                        )
                    )

                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                        contentDescription = "Ses Arttır",
                        tint = TextSecondary,
                        modifier = Modifier.size(22.dp)
                    )

                    Text(
                        text = "${localVolume.toInt()}%",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary,
                        modifier = Modifier.width(42.dp),
                        textAlign = TextAlign.End
                    )
                }
            }
        }
    }
}