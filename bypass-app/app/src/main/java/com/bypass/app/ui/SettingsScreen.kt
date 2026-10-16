package com.bypass.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bypass.app.ui.theme.*

@Composable
fun SettingsScreen(
    vpsIp: String,
    vpsPort: String,
    psk: String,
    isParentsMode: Boolean,
    activeProfile: String,
    onSaveSettings: (String, String, String, Boolean, String) -> Unit,
    onOpenDeployer: () -> Unit,
    onBack: () -> Unit
) {
    var ipState by remember { mutableStateOf(vpsIp) }
    var portState by remember { mutableStateOf(vpsPort) }
    var pskState by remember { mutableStateOf(psk) }
    var parentsModeState by remember { mutableStateOf(isParentsMode) }
    var profileState by remember { mutableStateOf(activeProfile) }

    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(OkxBlack)
            .padding(16.dp)
            .verticalScroll(scrollState)
    ) {
        Spacer(modifier = Modifier.height(8.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(OkxSurface2)
                    .border(1.dp, OkxBorder, RoundedCornerShape(8.dp))
            ) {
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = "Назад",
                    tint = OkxWhite,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = "ПАРАМЕТРЫ TURNAPEX",
                fontSize = 17.sp,
                fontWeight = FontWeight.Black,
                color = OkxWhite,
                letterSpacing = 0.5.sp
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        // VPS Settings Card
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(OkxSurface1)
                .border(1.dp, OkxBorder, RoundedCornerShape(14.dp))
                .padding(16.dp)
        ) {
            Column {
                Text(
                    text = "ПАРАМЕТРЫ СЕРВЕРА (VPS)",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = OkxTextSecondary,
                    letterSpacing = 0.5.sp
                )
                Spacer(modifier = Modifier.height(14.dp))

                OutlinedTextField(
                    value = ipState,
                    onValueChange = { ipState = it },
                    label = { Text("IP адрес VPS", color = OkxTextSecondary) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = OkxWhite,
                        unfocusedTextColor = OkxWhite,
                        focusedBorderColor = OkxWhite,
                        unfocusedBorderColor = OkxBorder,
                        focusedContainerColor = OkxSurface2,
                        unfocusedContainerColor = OkxSurface2
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = portState,
                    onValueChange = { portState = it },
                    label = { Text("UDP порт", color = OkxTextSecondary) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = OkxWhite,
                        unfocusedTextColor = OkxWhite,
                        focusedBorderColor = OkxWhite,
                        unfocusedBorderColor = OkxBorder,
                        focusedContainerColor = OkxSurface2,
                        unfocusedContainerColor = OkxSurface2
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = pskState,
                    onValueChange = { pskState = it },
                    label = { Text("Секретный ключ (PSK)", color = OkxTextSecondary) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = OkxWhite,
                        unfocusedTextColor = OkxWhite,
                        focusedBorderColor = OkxWhite,
                        unfocusedBorderColor = OkxBorder,
                        focusedContainerColor = OkxSurface2,
                        unfocusedContainerColor = OkxSurface2
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Auto Deploy Card
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(OkxSurface1)
                .border(1.dp, OkxBorder, RoundedCornerShape(14.dp))
                .padding(16.dp)
        ) {
            Column {
                Text(
                    text = "АВТОДЕПЛОЙ VPS",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = OkxTextSecondary,
                    letterSpacing = 0.5.sp
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Автоматическая установка WireGuard, RTP-демультиплексора и настройка портов на чистом сервере.",
                    fontSize = 12.sp,
                    color = OkxTextSecondary
                )
                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = onOpenDeployer,
                    modifier = Modifier.fillMaxWidth().height(44.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = OkxSurface3)
                ) {
                    Icon(imageVector = Icons.Default.CloudUpload, contentDescription = null, tint = OkxWhite)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = "Развернуть сервер в 1 клик", color = OkxWhite, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Profiles & Parents Mode
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(OkxSurface1)
                .border(1.dp, OkxBorder, RoundedCornerShape(14.dp))
                .padding(16.dp)
        ) {
            Column {
                Text(
                    text = "ПРОФИЛИ И РЕЖИМЫ",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = OkxTextSecondary,
                    letterSpacing = 0.5.sp
                )
                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("home", "work", "parents").forEach { prof ->
                        val isSelected = profileState == prof
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) OkxWhite else OkxSurface2)
                                .border(1.dp, if (isSelected) OkxWhite else OkxBorder, RoundedCornerShape(8.dp))
                                .padding(horizontal = 14.dp, vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = prof.replaceFirstChar { it.uppercase() },
                                color = if (isSelected) OkxBlack else OkxTextSecondary,
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                            )
                        }
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 14.dp), color = OkxBorder)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Режим «Для родителей»",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = OkxWhite
                        )
                        Text(
                            text = "Упрощенный экран с одной кнопкой",
                            fontSize = 11.sp,
                            color = OkxTextSecondary
                        )
                    }
                    Switch(
                        checked = parentsModeState,
                        onCheckedChange = { parentsModeState = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = OkxWhite,
                            checkedTrackColor = OkxGreen
                        )
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = {
                onSaveSettings(ipState, portState, pskState, parentsModeState, profileState)
                onBack()
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = OkxGreen)
        ) {
            Text(text = "СОХРАНИТЬ ИЗМЕНЕНИЯ", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = OkxBlack)
        }
    }
}
