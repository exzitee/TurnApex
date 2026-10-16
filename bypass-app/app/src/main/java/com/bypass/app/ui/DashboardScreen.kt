package com.bypass.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bypass.app.TrafficStats
import com.bypass.app.ui.theme.TermBlack
import com.bypass.app.ui.theme.TermBorder
import com.bypass.app.ui.theme.TermDim
import com.bypass.app.ui.theme.TermWhite
import kotlinx.coroutines.delay

@Composable
fun DashboardScreen(
    isRunning: Boolean,
    stats: TrafficStats,
    turnMode: String,
    validHashesCount: Int = 3,
    totalHashesCount: Int = 3,
    turnServers: List<Pair<String, Int>> = listOf(
        "turn.vk.com" to 12,
        "turn-ttl.vk.com" to 34,
        "5.255.211.241" to 55
    ),
    onToggleVpn: () -> Unit,
    onOpenConfig: () -> Unit,
    onOpenHashes: () -> Unit,
    onOpenApps: () -> Unit,
    onOpenParents: () -> Unit,
    onReconnect: () -> Unit
) {
    val scrollState = rememberScrollState()
    var uptimeSeconds by remember { mutableLongStateOf(0L) }

    LaunchedEffect(isRunning) {
        if (isRunning) {
            while (true) {
                delay(1000)
                uptimeSeconds += 1
            }
        } else {
            uptimeSeconds = 0L
        }
    }

    val hours = (uptimeSeconds / 3600).toString().padStart(2, '0')
    val minutes = ((uptimeSeconds % 3600) / 60).toString().padStart(2, '0')
    val seconds = (uptimeSeconds % 60).toString().padStart(2, '0')
    val uptimeStr = "$hours:$minutes:$seconds"

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(TermBlack)
            .padding(12.dp)
            .border(1.dp, TermBorder, RectangleShape)
            .padding(12.dp)
            .verticalScroll(scrollState)
    ) {
        // Top Header Block
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, TermWhite, RectangleShape)
                .clickable { onToggleVpn() }
                .padding(10.dp)
        ) {
            Column {
                Text(
                    text = "╔══ VK TURN BYPASS v1.0 ═══════════",
                    color = TermWhite,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (isRunning) "║ STATUS: ██ RUNNING ██" else "║ STATUS: [OFFLINE] (TAP TO START)",
                    color = TermWhite,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
                Text(
                    text = "║ MULTI-TURN: ${turnMode.uppercase()} (${turnServers.size}/${turnServers.size})",
                    color = TermWhite,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp
                )
                Text(
                    text = "║ HASHES: $validHashesCount/$totalHashesCount VALID",
                    color = TermWhite,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp
                )
                Text(
                    text = "╚═══════════════════════════════════",
                    color = TermWhite,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Metrics Terminal Box
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, TermDim, RectangleShape)
                .padding(8.dp)
        ) {
            Column {
                Text(
                    text = "↓↓ ${String.format("%.1f", stats.rxMbps)} Mbps   ↑↑ ${String.format("%.1f", stats.txMbps)} Mbps",
                    color = TermWhite,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
                Text(
                    text = "RTT: ${stats.latencyMs}ms  |  LOSS: 0.1%",
                    color = TermWhite,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp
                )
                Text(
                    text = "UPTIME: $uptimeStr",
                    color = TermWhite,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Target Services
        Text(
            text = "── TARGET SERVICES ──",
            color = TermWhite,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp
        )
        Spacer(modifier = Modifier.height(4.dp))

        TargetServiceRow("Telegram", stats.targets.telegram, 42)
        TargetServiceRow("YouTube", stats.targets.youtube, 88)
        TargetServiceRow("Google", stats.targets.google, 31)
        TargetServiceRow("TikTok", stats.targets.tiktok, 120)

        Spacer(modifier = Modifier.height(14.dp))

        // Turn Servers
        Text(
            text = "── TURN SERVERS ──",
            color = TermWhite,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp
        )
        Spacer(modifier = Modifier.height(4.dp))

        turnServers.forEachIndexed { index, pair ->
            TurnServerRow(index + 1, pair.first, pair.second, isRunning)
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Bottom Actions Menu
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                TerminalButton("[C]ONFIG") { onOpenConfig() }
                TerminalButton("[H]ASHES") { onOpenHashes() }
                TerminalButton("[A]PPS") { onOpenApps() }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                TerminalButton(if (isRunning) "[T]OGGLE OFF" else "[T]OGGLE ON") { onToggleVpn() }
                TerminalButton("[R]ECONNECT") { onReconnect() }
                TerminalButton("[P]ARENTS") { onOpenParents() }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "▲ SWIPE LEFT/RIGHT TO SWITCH SCREENS ▼",
            color = TermDim,
            fontFamily = FontFamily.Monospace,
            fontSize = 10.sp,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )
    }
}

@Composable
fun TargetServiceRow(name: String, online: Boolean, pingMs: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = name.padEnd(10, ' '),
            color = TermWhite,
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp
        )
        Text(
            text = "│",
            color = TermDim,
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp
        )
        Text(
            text = if (online) "██ ONLINE ██" else "[OFFLINE]",
            color = TermWhite,
            fontFamily = FontFamily.Monospace,
            fontWeight = if (online) FontWeight.Bold else FontWeight.Normal,
            fontSize = 12.sp
        )
        Text(
            text = "${pingMs}ms",
            color = TermWhite,
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp
        )
    }
}

@Composable
fun TurnServerRow(index: Int, host: String, pingMs: Int, isRunning: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "#$index ${host.take(18)}".padEnd(20, ' '),
            color = TermWhite,
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp
        )
        Text(
            text = if (isRunning) "██ ${pingMs}ms ██" else "[READY]",
            color = TermWhite,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp
        )
    }
}

@Composable
fun TerminalButton(text: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .border(1.dp, TermWhite, RectangleShape)
            .clickable { onClick() }
            .padding(horizontal = 8.dp, vertical = 6.dp)
    ) {
        Text(
            text = text,
            color = TermWhite,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            fontSize = 11.sp
        )
    }
}
