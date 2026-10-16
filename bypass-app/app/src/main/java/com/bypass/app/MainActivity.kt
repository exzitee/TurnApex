package com.bypass.app

import android.app.Activity
import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import com.bypass.app.ui.DashboardScreen
import com.bypass.app.ui.DeployScreen
import com.bypass.app.ui.ParentsScreen
import com.bypass.app.ui.SettingsScreen
import com.bypass.app.ui.theme.BypassTheme
import org.json.JSONArray
import org.json.JSONObject

class MainActivity : ComponentActivity() {

    private val vpnPrepareLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            launchVpnService()
        }
    }

    private var vpsIp by mutableStateOf("127.0.0.1")
    private var vpsPort by mutableStateOf("56000")
    private var psk by mutableStateOf("vk-turn-bypass-secret-v1")
    private var turnMode by mutableStateOf("aggregate")
    private var rtpMasking by mutableStateOf(true)
    private var isParentsMode by mutableStateOf(false)
    private var activeProfile by mutableStateOf("home")
    private var currentScreen by mutableStateOf("dashboard")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            TurnApexTheme {
                val isRunning by BypassVpnService.isRunning.collectAsState()
                val stats by BypassVpnService.statsFlow.collectAsState()

                when (currentScreen) {
                    "parents" -> {
                        ParentsScreen(
                            isRunning = isRunning,
                            onToggle = { toggleVpn(isRunning) },
                            onExitParentsMode = {
                                isParentsMode = false
                                currentScreen = "dashboard"
                            }
                        )
                    }
                    "settings" -> {
                        SettingsScreen(
                            vpsIp = vpsIp,
                            vpsPort = vpsPort,
                            psk = psk,
                            isParentsMode = isParentsMode,
                            activeProfile = activeProfile,
                            onSaveSettings = { newIp, newPort, newPsk, newParents, newProfile ->
                                vpsIp = newIp
                                vpsPort = newPort
                                psk = newPsk
                                isParentsMode = newParents
                                activeProfile = newProfile
                                if (newParents) {
                                    currentScreen = "parents"
                                }
                            },
                            onOpenDeployer = { currentScreen = "deploy" },
                            onBack = {
                                currentScreen = if (isParentsMode) "parents" else "dashboard"
                            }
                        )
                    }
                    "deploy" -> {
                        DeployScreen(
                            currentIp = vpsIp,
                            onDeploySuccess = { deployedIp, deployedPort, deployedPsk ->
                                vpsIp = deployedIp
                                vpsPort = deployedPort
                                psk = deployedPsk
                            },
                            onBack = { currentScreen = "settings" }
                        )
                    }
                    else -> {
                        if (isParentsMode) {
                            ParentsScreen(
                                isRunning = isRunning,
                                onToggle = { toggleVpn(isRunning) },
                                onExitParentsMode = {
                                    isParentsMode = false
                                    currentScreen = "dashboard"
                                }
                            )
                        } else {
                            DashboardScreen(
                                isRunning = isRunning,
                                stats = stats,
                                turnMode = turnMode,
                                rtpMasking = rtpMasking,
                                onToggleVpn = { toggleVpn(isRunning) },
                                onModeChange = { mode ->
                                    turnMode = mode
                                    BypassVpnService.currentMode = mode
                                },
                                onRtpToggle = { enabled ->
                                    rtpMasking = enabled
                                    BypassVpnService.rtpMasking = enabled
                                },
                                onOpenSettings = { currentScreen = "settings" }
                            )
                        }
                    }
                }
            }
        }
    }

    private fun toggleVpn(currentlyRunning: Boolean) {
        if (currentlyRunning) {
            val stopIntent = Intent(this, BypassVpnService::class.java).apply {
                action = BypassVpnService.ACTION_STOP
            }
            startService(stopIntent)
        } else {
            val prepareIntent = VpnService.prepare(this)
            if (prepareIntent != null) {
                vpnPrepareLauncher.launch(prepareIntent)
            } else {
                launchVpnService()
            }
        }
    }

    private fun launchVpnService() {
        val configJson = buildConfigJson()
        val startIntent = Intent(this, BypassVpnService::class.java).apply {
            action = BypassVpnService.ACTION_START
            putExtra(BypassVpnService.EXTRA_CONFIG, configJson)
        }
        startForegroundService(startIntent)
    }

    private fun buildConfigJson(): String {
        val root = JSONObject()
        val vps = JSONObject().apply {
            put("ip", vpsIp)
            put("port", vpsPort.toIntOrNull() ?: 56000)
            put("psk", psk)
        }
        root.put("vps", vps)

        val turns = JSONArray().apply {
            put("turn.vk.com:3478")
            put("turn-ttl.vk.com:3478")
            put("5.255.211.241:3478")
        }
        root.put("turn_servers", turns)
        root.put("mode", turnMode)
        root.put("rtp_masking", rtpMasking)

        val split = JSONObject().apply {
            put("mode", if (isParentsMode) "all" else "list")
            put("tunnel_apps", JSONArray().apply {
                put("org.telegram.messenger")
                put("com.google.android.youtube")
                put("com.zhiliaoapp.musically")
            })
            put("direct_domains", JSONArray().apply {
                put("sberbank.ru")
                put("gosuslugi.ru")
            })
        }
        root.put("split_tunnel", split)
        root.put("health_interval_sec", 3)
        root.put("failover_timeout_sec", 3)
        root.put("mtu", 1280)

        return root.toString()
    }
}
