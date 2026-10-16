package com.bypass.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RectangleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bypass.app.TrafficStats
import com.bypass.app.ui.theme.TermBlack
import com.bypass.app.ui.theme.TermBorder
import com.bypass.app.ui.theme.TermWhite

@Composable
fun ParentsScreen(
    isRunning: Boolean,
    stats: TrafficStats = TrafficStats(),
    onToggle: () -> Unit,
    onExitParentsMode: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(TermBlack)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .width(280.dp)
                .border(1.dp, TermBorder, RectangleShape)
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "╔══ INTERNET ═════════",
                color = TermWhite,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = if (isRunning) "║    ██ ON ██        " else "║    [OFFLINE]       ",
                color = TermWhite,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )
            Text(
                text = "╚═════════════════════",
                color = TermWhite,
                fontFamily = FontFamily.Monospace,
                fontSize = 14.sp
            )

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "↓↓ ${String.format("%.1f", stats.rxMbps)}  ↑↑ ${String.format("%.1f", stats.txMbps)} Mbps",
                color = TermWhite,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp
            )

            Spacer(modifier = Modifier.height(24.dp))

            Box(
                modifier = Modifier
                    .border(2.dp, TermWhite, RectangleShape)
                    .clickable { onToggle() }
                    .padding(horizontal = 24.dp, vertical = 12.dp)
            ) {
                Text(
                    text = if (isRunning) "[O]FF" else "[O]N",
                    color = TermWhite,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            TerminalButton("[E]XIT TO NORMAL") {
                onExitParentsMode()
            }
        }
    }
}
