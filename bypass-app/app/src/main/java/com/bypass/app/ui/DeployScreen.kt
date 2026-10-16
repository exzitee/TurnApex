package com.bypass.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Terminal
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
import com.bypass.app.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun DeployScreen(
    currentIp: String,
    onDeploySuccess: (String, String, String) -> Unit,
    onBack: () -> Unit
) {
    var hostState by remember { mutableStateOf(currentIp) }
    var userState by remember { mutableStateOf("root") }
    var portState by remember { mutableStateOf("22") }
    var passwordState by remember { mutableStateOf("") }
    var udpPortState by remember { mutableStateOf("56000") }

    var isDeploying by remember { mutableStateOf(false) }
    var isSuccess by remember { mutableStateOf(false) }
    var currentStep by remember { mutableStateOf("") }
    val logs = remember { mutableStateListOf<String>() }

    val coroutineScope = rememberCoroutineScope()
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
                text = "TURNAPEX DEPLOYER",
                fontSize = 17.sp,
                fontWeight = FontWeight.Black,
                color = OkxWhite,
                letterSpacing = 0.5.sp
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Input Card
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
                    text = "SSH ПОДКЛЮЧЕНИЕ К VPS",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = OkxTextSecondary,
                    letterSpacing = 0.5.sp
                )
                Spacer(modifier = Modifier.height(14.dp))

                OutlinedTextField(
                    value = hostState,
                    onValueChange = { hostState = it },
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

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = userState,
                        onValueChange = { userState = it },
                        label = { Text("Пользователь", color = OkxTextSecondary) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = OkxWhite,
                            unfocusedTextColor = OkxWhite,
                            focusedBorderColor = OkxWhite,
                            unfocusedBorderColor = OkxBorder,
                            focusedContainerColor = OkxSurface2,
                            unfocusedContainerColor = OkxSurface2
                        ),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = portState,
                        onValueChange = { portState = it },
                        label = { Text("SSH Порт", color = OkxTextSecondary) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = OkxWhite,
                            unfocusedTextColor = OkxWhite,
                            focusedBorderColor = OkxWhite,
                            unfocusedBorderColor = OkxBorder,
                            focusedContainerColor = OkxSurface2,
                            unfocusedContainerColor = OkxSurface2
                        ),
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = passwordState,
                    onValueChange = { passwordState = it },
                    label = { Text("SSH Пароль или Приватный ключ", color = OkxTextSecondary) },
                    singleLine = false,
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

        Spacer(modifier = Modifier.height(16.dp))

        if (!isDeploying && !isSuccess) {
            Button(
                onClick = {
                    isDeploying = true
                    logs.clear()
                    coroutineScope.launch {
                        simulateAndRunDeploy(
                            host = hostState,
                            port = portState,
                            user = userState,
                            udpPort = udpPortState,
                            onLog = { logs.add(it) },
                            onStep = { currentStep = it }
                        )
                        isDeploying = false
                        isSuccess = true
                        onDeploySuccess(hostState, udpPortState, "vk-turn-bypass-secret-v1")
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = OkxGreen)
            ) {
                Icon(imageVector = Icons.Default.Terminal, contentDescription = null, tint = OkxBlack)
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = "ЗАПУСТИТЬ ДЕПЛОЙ", fontSize = 14.sp, fontWeight = FontWeight.Black, color = OkxBlack)
            }
        }

        if (isDeploying) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(OkxSurface1)
                    .border(1.dp, OkxBorder, RoundedCornerShape(14.dp))
                    .padding(20.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = OkxGreen, modifier = Modifier.size(36.dp))
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(text = currentStep, color = OkxWhite, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                }
            }
        }

        if (logs.isNotEmpty()) {
            Spacer(modifier = Modifier.height(14.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF070709))
                    .border(1.dp, OkxBorder, RoundedCornerShape(12.dp))
                    .padding(14.dp)
            ) {
                Column {
                    Text(
                        text = "SSH CONSOLE OUTPUT:",
                        color = OkxTextSecondary,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 0.5.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    logs.forEach { line ->
                        Text(
                            text = line,
                            color = if (line.contains("OK") || line.contains("✓")) OkxGreen else OkxWhite.copy(alpha = 0.85f),
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        }

        if (isSuccess) {
            Spacer(modifier = Modifier.height(14.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(OkxGreenBg)
                    .border(1.dp, OkxGreen.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
                    .padding(16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Default.CheckCircle, contentDescription = null, tint = OkxGreen)
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Деплой завершен успешно!",
                            color = OkxWhite,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                        Text(
                            text = "VPS настроен и готов принимать замаскированный трафик.",
                            color = OkxGreen,
                            fontSize = 12.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = onBack,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = OkxWhite)
            ) {
                Text(text = "ВЕРНУТЬСЯ К ДАШБОРДУ", color = OkxBlack, fontWeight = FontWeight.Black, fontSize = 13.sp)
            }
        }
    }
}
