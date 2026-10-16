package com.bypass.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bypass.app.TrafficStats
import com.bypass.app.ui.theme.*

@Composable
fun DashboardScreen(
    isRunning: Boolean,
    stats: TrafficStats,
    turnMode: String,
    rtpMasking: Boolean,
    onToggleVpn: () -> Unit,
    onModeChange: (String) -> Unit,
    onRtpToggle: (Boolean) -> Unit,
    onOpenSettings: () -> Unit
) {
    var splitDropdownExpanded by remember { mutableStateOf(false) }
    var splitMode by remember { mutableStateOf("Только приложения") }

    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(OkxBlack)
            .padding(horizontal = 16.dp)
            .verticalScroll(scrollState)
    ) {
        Spacer(modifier = Modifier.height(20.dp))

        // Top Navigation Bar (OKX Pro Style)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // OKX Apex Logo Symbol
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(OkxSurface2)
                        .border(1.dp, OkxBorder, RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "▲",
                        color = OkxGreen,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Black
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = "TURNAPEX",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Black,
                        color = OkxWhite,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = "VK TURN Stealth Tunnel",
                        fontSize = 11.sp,
                        color = OkxTextSecondary
                    )
                }
            }

            // Settings Icon
            IconButton(
                onClick = onOpenSettings,
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(OkxSurface2)
                    .border(1.dp, OkxBorder, RoundedCornerShape(10.dp))
            ) {
                Icon(
                    imageVector = Icons.Default.Tune,
                    contentDescription = "Настройки",
                    tint = OkxTextPrimary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Hero Metric Card (OKX Asset / Position Card)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(OkxSurface1)
                .border(1.dp, if (isRunning) OkxGreen.copy(alpha = 0.35f) else OkxBorder, RoundedCornerShape(16.dp))
                .padding(18.dp)
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Status Pill
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(if (isRunning) OkxGreenBg else OkxRedBg)
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(if (isRunning) OkxGreen else OkxRed)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isRunning) "ПОДКЛЮЧЕНО" else "ОТКЛЮЧЕНО",
                            color = if (isRunning) OkxGreen else OkxRed,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        )
                    }

                    // Ping Latency Badge
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "RTT ",
                            fontSize = 11.sp,
                            color = OkxTextTertiary,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = "${if (isRunning) stats.latencyMs else 0} ms",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isRunning) OkxGreen else OkxTextSecondary,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Crypto-style Speeds Display
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = "СКОРОСТЬ ЗАГРУЗКИ",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = OkxTextSecondary,
                            letterSpacing = 0.5.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(
                                text = "↓ ${if (isRunning) stats.rxMbps else 0.0}",
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Black,
                                color = OkxGreen,
                                fontFamily = FontFamily.Monospace
                            )
                            Text(
                                text = " Mbps",
                                fontSize = 12.sp,
                                color = OkxTextSecondary,
                                modifier = Modifier.padding(bottom = 3.dp, start = 2.dp)
                            )
                        }
                    }

                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "СКОРОСТЬ ОТДАЧИ",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = OkxTextSecondary,
                            letterSpacing = 0.5.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(
                                text = "↑ ${if (isRunning) stats.txMbps else 0.0}",
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Black,
                                color = OkxWhite,
                                fontFamily = FontFamily.Monospace
                            )
                            Text(
                                text = " Mbps",
                                fontSize = 12.sp,
                                color = OkxTextSecondary,
                                modifier = Modifier.padding(bottom = 3.dp, start = 2.dp)
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Main Primary CTA Button (OKX Buy/Trade style)
        Button(
            onClick = onToggleVpn,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isRunning) OkxSurface3 else OkxGreen
            ),
            border = if (isRunning) androidx.compose.foundation.BorderStroke(1.dp, OkxRed.copy(alpha = 0.6f)) else null
        ) {
            Icon(
                imageVector = if (isRunning) Icons.Default.PowerSettingsNew else Icons.Default.Bolt,
                contentDescription = null,
                tint = if (isRunning) OkxRed else OkxBlack,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = if (isRunning) "ОТКЛЮЧИТЬ ТУННЕЛЬ" else "ПОДКЛЮЧИТЬ TURNAPEX",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = if (isRunning) OkxRed else OkxBlack,
                letterSpacing = 0.5.sp
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Multi-TURN Mode Segmented Tabs (OKX Segmented Control)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(OkxSurface1)
                .border(1.dp, OkxBorder, RoundedCornerShape(14.dp))
                .padding(14.dp)
        ) {
            Text(
                text = "РЕЖИМ MULTI-TURN",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = OkxTextSecondary,
                letterSpacing = 0.5.sp
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Segmented Control Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(OkxSurface2)
                    .padding(3.dp)
            ) {
                val isAggregate = turnMode.equals("aggregate", true)

                // Tab 1: AGGREGATE
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isAggregate) OkxWhite else Color.Transparent)
                        .clickable { onModeChange("aggregate") },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "AGGREGATE (N×Скорость)",
                        fontSize = 12.sp,
                        fontWeight = if (isAggregate) FontWeight.Black else FontWeight.Medium,
                        color = if (isAggregate) OkxBlack else OkxTextSecondary
                    )
                }

                // Tab 2: FAILOVER
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (!isAggregate) OkxWhite else Color.Transparent)
                        .clickable { onModeChange("failover") },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "FAILOVER (<50ms)",
                        fontSize = 12.sp,
                        fontWeight = if (!isAggregate) FontWeight.Black else FontWeight.Medium,
                        color = if (!isAggregate) OkxBlack else OkxTextSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // RTP Masking Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "RTP-маскировка трафика",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = OkxWhite
                    )
                    Text(
                        text = "WebRTC видеозвонок VK (30 fps)",
                        fontSize = 11.sp,
                        color = OkxTextSecondary
                    )
                }
                Switch(
                    checked = rtpMasking,
                    onCheckedChange = onRtpToggle,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = OkxWhite,
                        checkedTrackColor = OkxGreen,
                        uncheckedThumbColor = OkxTextTertiary,
                        uncheckedTrackColor = OkxSurface3
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Target Matrix (OKX Market Pairs Grid)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(OkxSurface1)
                .border(1.dp, OkxBorder, RoundedCornerShape(14.dp))
                .padding(14.dp)
        ) {
            Text(
                text = "МОНИТОРИНГ ЦЕЛЕВЫХ СЕРВИСОВ",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = OkxTextSecondary,
                letterSpacing = 0.5.sp
            )

            Spacer(modifier = Modifier.height(10.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OkxTargetCard("Telegram", stats.targets.telegram && isRunning, Modifier.weight(1f))
                OkxTargetCard("Google", stats.targets.google && isRunning, Modifier.weight(1f))
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OkxTargetCard("YouTube", stats.targets.youtube && isRunning, Modifier.weight(1f))
                OkxTargetCard("TikTok", stats.targets.tiktok && isRunning, Modifier.weight(1f))
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Split Tunneling Card (OKX Asset Portfolio Style)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(OkxSurface1)
                .border(1.dp, OkxBorder, RoundedCornerShape(14.dp))
                .padding(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "РАЗДЕЛЬНОЕ ТУННЕЛИРОВАНИЕ",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = OkxTextSecondary,
                    letterSpacing = 0.5.sp
                )

                Box {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(OkxSurface2)
                            .border(1.dp, OkxBorder, RoundedCornerShape(6.dp))
                            .clickable { splitDropdownExpanded = true }
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = splitMode,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = OkxWhite
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.Default.ArrowDropDown,
                            contentDescription = null,
                            tint = OkxTextSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    DropdownMenu(
                        expanded = splitDropdownExpanded,
                        onDismissRequest = { splitDropdownExpanded = false },
                        modifier = Modifier.background(OkxSurface2)
                    ) {
                        DropdownMenuItem(
                            text = { Text("Только приложения (LIST)", color = OkxWhite) },
                            onClick = {
                                splitMode = "Только приложения"
                                splitDropdownExpanded = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Весь трафик (ALL)", color = OkxWhite) },
                            onClick = {
                                splitMode = "Весь трафик"
                                splitDropdownExpanded = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Все кроме указанных (EXCEPT)", color = OkxWhite) },
                            onClick = {
                                splitMode = "Все кроме указанных"
                                splitDropdownExpanded = false
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            OkxAppRow("Telegram Messenger", "org.telegram.messenger", isTunneled = true)
            OkxAppRow("YouTube", "com.google.android.youtube", isTunneled = true)
            OkxAppRow("TikTok", "com.zhiliaoapp.musically", isTunneled = true)
            OkxAppRow("Сбербанк Онлайн", "ru.sberbankmobile", isTunneled = false)
            OkxAppRow("Госуслуги", "ru.gosuslugi.portal", isTunneled = false)
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
fun OkxTargetCard(name: String, ok: Boolean, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(OkxSurface2)
            .border(1.dp, OkxBorder, RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = name, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = OkxWhite)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = if (ok) "LIVE" else "OFFLINE",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = if (ok) OkxGreen else OkxRed,
                fontFamily = FontFamily.Monospace
            )
            Spacer(modifier = Modifier.width(5.dp))
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(if (ok) OkxGreen else OkxRed)
            )
        }
    }
}

@Composable
fun OkxAppRow(name: String, pkg: String, isTunneled: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (isTunneled) OkxSurface3 else OkxSurface2),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = name.first().toString(),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isTunneled) OkxWhite else OkxTextSecondary
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(text = name, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = OkxWhite)
                Text(text = pkg, fontSize = 10.sp, color = OkxTextTertiary)
            }
        }

        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(4.dp))
                .background(if (isTunneled) OkxGreenBg else OkxSurface2)
                .padding(horizontal = 8.dp, vertical = 3.dp)
        ) {
            Text(
                text = if (isTunneled) "ТУННЕЛЬ" else "НАПРЯМУЮ",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = if (isTunneled) OkxGreen else OkxTextSecondary,
                letterSpacing = 0.5.sp
            )
        }
    }
}
