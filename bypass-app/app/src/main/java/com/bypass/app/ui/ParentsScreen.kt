package com.bypass.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bypass.app.ui.theme.*

@Composable
fun ParentsScreen(
    isRunning: Boolean,
    onToggle: () -> Unit,
    onExitParentsMode: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(OkxBlack)
            .padding(24.dp)
    ) {
        // Exit icon at top right (OKX style subtle icon)
        IconButton(
            onClick = onExitParentsMode,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .size(36.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(OkxSurface2)
                .border(1.dp, OkxBorder, RoundedCornerShape(8.dp))
        ) {
            Icon(
                imageVector = Icons.Default.Tune,
                contentDescription = "Расширенный режим",
                tint = OkxTextSecondary,
                modifier = Modifier.size(16.dp)
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // OKX Apex Shield Badge
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(if (isRunning) OkxGreenBg else OkxSurface2)
                    .border(2.dp, if (isRunning) OkxGreen else OkxBorder, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Shield,
                    contentDescription = null,
                    tint = if (isRunning) OkxGreen else OkxTextSecondary,
                    modifier = Modifier.size(36.dp)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "TURNAPEX ЗАЩИТА",
                fontSize = 24.sp,
                fontWeight = FontWeight.Black,
                color = OkxWhite,
                letterSpacing = 1.sp,
                textAlign = TextAlign.Center
            )

            Text(
                text = "Безопасный доступ через инфраструктуру VK",
                fontSize = 13.sp,
                color = OkxTextSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 4.dp)
            )

            Spacer(modifier = Modifier.height(40.dp))

            // Giant Friendly One-Touch Button
            Button(
                onClick = onToggle,
                modifier = Modifier
                    .fillMaxWidth(0.9f)
                    .height(96.dp),
                shape = RoundedCornerShape(20.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isRunning) OkxGreen else OkxWhite
                ),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(if (isRunning) OkxBlack else OkxRed)
                )
                Spacer(modifier = Modifier.width(14.dp))
                Text(
                    text = if (isRunning) "ИНТЕРНЕТ ВКЛЮЧЕН" else "ВКЛЮЧИТЬ ИНТЕРНЕТ",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Black,
                    color = OkxBlack,
                    letterSpacing = 0.5.sp
                )
            }

            Spacer(modifier = Modifier.height(30.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth(0.9f)
                    .clip(RoundedCornerShape(14.dp))
                    .background(OkxSurface1)
                    .border(1.dp, OkxBorder, RoundedCornerShape(14.dp))
                    .padding(vertical = 18.dp, horizontal = 16.dp)
            ) {
                Text(
                    text = "Ничего не трогай. Всё работает автоматически.",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = OkxTextSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}
