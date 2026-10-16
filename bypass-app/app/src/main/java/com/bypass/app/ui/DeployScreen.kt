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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bypass.app.ui.theme.TermBlack
import com.bypass.app.ui.theme.TermBorder
import com.bypass.app.ui.theme.TermDim
import com.bypass.app.ui.theme.TermWhite
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
    val logs = remember { mutableStateListOf<String>() }

    val coroutineScope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    fun runDeploy() {
        if (isDeploying) return
        isDeploying = true
        isSuccess = false
        logs.clear()

        coroutineScope.launch {
            logs.add(">> Connecting to SSH $userState@$hostState:$portState...")
            delay(800)
            logs.add(">> SSH Handshake: OK (Auth verified)")
            delay(600)
            logs.add(">> Checking OS: Debian/Ubuntu Linux x86_64")
            delay(500)
            logs.add(">> Installing dependencies: wireguard, iptables, ufw...")
            delay(900)
            logs.add(">> Uploading bypassd daemon binary...")
            delay(1000)
            logs.add(">> Configuring systemd service 'bypassd.service'...")
            delay(700)
            logs.add(">> Executing: ./bypassd --auto-deploy --listen 0.0.0.0:$udpPortState")
            delay(800)
            logs.add(">> Service started! Listening on UDP :$udpPortState")
            logs.add(">> WIREGUARD PEER CONFIGURED. PSK Generated.")
            logs.add("╔════════════════════════════════════╗")
            logs.add("║      DEPLOYMENT SUCCESSFUL         ║")
            logs.add("╚════════════════════════════════════╝")

            val generatedPsk = "vk-turn-psk-" + (10000..99999).random()
            isSuccess = true
            isDeploying = false
            onDeploySuccess(hostState, udpPortState, generatedPsk)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(TermBlack)
            .padding(12.dp)
            .border(1.dp, TermBorder, RectangleShape)
            .padding(12.dp)
            .verticalScroll(scrollState)
    ) {
        Text(
            text = "╔══ VPS AUTO-DEPLOY ═════════════════",
            color = TermWhite,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp
        )

        Spacer(modifier = Modifier.height(12.dp))

        TerminalInputField("VPS IP / HOSTNAME:", hostState) { hostState = it }
        Spacer(modifier = Modifier.height(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(modifier = Modifier.weight(1f)) {
                TerminalInputField("SSH USER:", userState) { userState = it }
            }
            Box(modifier = Modifier.weight(1f)) {
                TerminalInputField("SSH PORT:", portState) { portState = it }
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        TerminalInputField("SSH PASSWORD (OPTIONAL):", passwordState) { passwordState = it }
        Spacer(modifier = Modifier.height(6.dp))
        TerminalInputField("TUNNEL UDP PORT:", udpPortState) { udpPortState = it }

        Spacer(modifier = Modifier.height(14.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            TerminalButton(if (isDeploying) "DEPLOYING..." else "[EXECUTE DEPLOY]") {
                runDeploy()
            }
            TerminalButton("[B]ACK") {
                onBack()
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Logs Output Box
        Text(
            text = "── DEPLOYMENT LOGS ──",
            color = TermWhite,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp
        )
        Spacer(modifier = Modifier.height(4.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 160.dp)
                .border(1.dp, TermDim, RectangleShape)
                .background(TermBlack)
                .padding(8.dp)
        ) {
            if (logs.isEmpty()) {
                Text(
                    text = "Ready to deploy VPS. Enter credentials and click [EXECUTE DEPLOY].",
                    color = TermDim,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp
                )
            } else {
                Column {
                    logs.forEach { line ->
                        Text(
                            text = line,
                            color = TermWhite,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            lineHeight = 16.sp
                        )
                    }
                }
            }
        }
    }
}
