package com.bypass.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RectangleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bypass.app.ui.theme.TermBlack
import com.bypass.app.ui.theme.TermBorder
import com.bypass.app.ui.theme.TermDim
import com.bypass.app.ui.theme.TermWhite

@Composable
fun SettingsScreen(
    vpsIp: String,
    vpsPort: String,
    psk: String,
    turnMode: String = "aggregate",
    turnServers: List<String> = listOf("turn.vk.com:3478", "turn-ttl.vk.com:3478", "5.255.211.241:3478"),
    isParentsMode: Boolean,
    activeProfile: String,
    onSaveSettings: (String, String, String, String, List<String>, Boolean, String) -> Unit,
    onOpenDeployer: () -> Unit,
    onBack: () -> Unit
) {
    var ipState by remember { mutableStateOf(vpsIp) }
    var portState by remember { mutableStateOf(vpsPort) }
    var pskState by remember { mutableStateOf(psk) }
    var modeState by remember { mutableStateOf(turnMode) }
    val serversList = remember { mutableStateListOf<String>().apply { addAll(turnServers) } }
    var newServerInput by remember { mutableStateOf("") }
    var parentsModeState by remember { mutableStateOf(isParentsMode) }
    var profileState by remember { mutableStateOf(activeProfile) }

    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(TermBlack)
            .padding(12.dp)
            .border(1.dp, TermBorder, RectangleShape)
            .padding(12.dp)
            .verticalScroll(scrollState)
    ) {
        // Title
        Text(
            text = "╔══ CONFIG ══════════════════════════",
            color = TermWhite,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp
        )

        Spacer(modifier = Modifier.height(14.dp))

        // 1. Turn Mode
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "1. TURN MODE: ${modeState.uppercase()}",
                color = TermWhite,
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp
            )
            TerminalButton(if (modeState == "aggregate") "[TOGGLE: FAILOVER]" else "[TOGGLE: AGGREGATE]") {
                modeState = if (modeState == "aggregate") "failover" else "aggregate"
            }
        }

        // 2. Profile
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "2. PROFILE: ${profileState.uppercase()}",
                color = TermWhite,
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp
            )
            TerminalButton("[SWITCH]") {
                profileState = when (profileState) {
                    "home" -> "work"
                    "work" -> "stealth"
                    else -> "home"
                }
            }
        }

        // 3. Parents mode
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "3. PARENTS MODE: ${if (parentsModeState) "ENABLED" else "DISABLED"}",
                color = TermWhite,
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp
            )
            TerminalButton(if (parentsModeState) "[DISABLE]" else "[ENABLE]") {
                parentsModeState = !parentsModeState
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // VPS Settings
        Text(
            text = "── VPS SERVER ──",
            color = TermWhite,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp
        )
        Spacer(modifier = Modifier.height(6.dp))

        TerminalInputField("IP ADDRESS:", ipState) { ipState = it }
        Spacer(modifier = Modifier.height(6.dp))
        TerminalInputField("PORT:", portState) { portState = it }
        Spacer(modifier = Modifier.height(6.dp))
        TerminalInputField("PSK SECRET:", pskState) { pskState = it }

        Spacer(modifier = Modifier.height(14.dp))

        // Turn Servers list
        Text(
            text = "── TURN SERVERS (${serversList.size}) ──",
            color = TermWhite,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp
        )
        Spacer(modifier = Modifier.height(6.dp))

        serversList.forEachIndexed { idx, srv ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "[${idx + 1}] $srv",
                    color = TermWhite,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp
                )
                Text(
                    text = "[REMOVE]",
                    color = TermDim,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    modifier = Modifier.clickable {
                        if (serversList.size > 1) {
                            serversList.removeAt(idx)
                        }
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Add new server input
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .border(1.dp, TermDim, RectangleShape)
                    .background(TermBlack)
                    .padding(6.dp)
            ) {
                BasicTextField(
                    value = newServerInput,
                    onValueChange = { newServerInput = it },
                    textStyle = TextStyle(
                        color = TermWhite,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp
                    ),
                    cursorBrush = SolidColor(TermWhite),
                    modifier = Modifier.fillMaxWidth(),
                    decorationBox = { innerTextField ->
                        if (newServerInput.isEmpty()) {
                            Text(
                                text = "host:port",
                                color = TermDim,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp
                            )
                        }
                        innerTextField()
                    }
                )
            }

            TerminalButton("[ADD]") {
                if (newServerInput.isNotBlank()) {
                    serversList.add(newServerInput.trim())
                    newServerInput = ""
                }
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // Action Buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            TerminalButton("[S]AVE") {
                onSaveSettings(
                    ipState,
                    portState,
                    pskState,
                    modeState,
                    serversList.toList(),
                    parentsModeState,
                    profileState
                )
                onBack()
            }
            TerminalButton("[D]EPLOY VPS") {
                onOpenDeployer()
            }
            TerminalButton("[B]ACK") {
                onBack()
            }
        }
    }
}

@Composable
fun TerminalInputField(label: String, value: String, onValueChange: (String) -> Unit) {
    Column {
        Text(
            text = label,
            color = TermDim,
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp
        )
        Spacer(modifier = Modifier.height(2.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, TermWhite, RectangleShape)
                .background(TermBlack)
                .padding(6.dp)
        ) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                textStyle = TextStyle(
                    color = TermWhite,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp
                ),
                cursorBrush = SolidColor(TermWhite),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
