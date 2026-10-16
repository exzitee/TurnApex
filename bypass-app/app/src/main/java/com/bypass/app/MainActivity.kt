package com.bypass.app

import android.app.Activity
import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import com.bypass.app.ui.*
import com.bypass.app.ui.theme.TurnApexTheme
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
    private var hashMode by mutableStateOf("auto")
    private var currentScreen by mutableStateOf("dashboard")

    private val turnServers = mutableStateListOf(
        "turn.vk.com:3478",
        "turn-ttl.vk.com:3478",
        "5.255.211.241:3478"
    )

    private val activeHashes = mutableStateListOf(
        VkHashItem(raw = "6f7770649d8231ac468a8e7f141f57feea8429e595007c85", valid = true),
        VkHashItem(raw = "a3f189cd20ba54e19572c8402941dfba98305c1294829b22", valid = true)
    )

    private val appRoutes = mutableStateMapOf<String, String>().apply {
        put("org.telegram.messenger", "turn")
        put("com.google.android.youtube", "turn")
        put("com.zhiliaoapp.musically", "turn")
        put("com.instagram.android", "turn")
        put("com.twitter.android", "turn")
        put("com.android.chrome", "turn")
        put("ru.sberbankmobile", "direct")
        put("ru.tinkoff.activities", "direct")
        put("ru.gosuslugi.portal", "direct")
        put("ru.vtb24.mobilebanking", "direct")
        put("ru.yandex.searchplugin", "direct")
    }

    private var showVkAuthDialog by mutableStateOf(false)

    private val screenOrder = listOf("dashboard", "config", "hashes", "apps", "parents")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            TurnApexTheme {
                MainContent()
            }
        }
    }

    @Composable
    private fun MainContent() {
        val isRunning by BypassVpnService.isRunning.collectAsState()
        val stats by BypassVpnService.statsFlow.collectAsState()
        val serviceHashes by BypassVpnService.hashesFlow.collectAsState()

        val effectiveHashes = if (serviceHashes.isNotEmpty()) serviceHashes else activeHashes.toList()
        val validCount = effectiveHashes.count { it.valid }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(currentScreen) {
                    var totalDrag = 0f
                    detectHorizontalDragGestures(
                        onDragStart = { totalDrag = 0f },
                        onDragEnd = {
                            if (totalDrag < -100) {
                                // Swiped Left -> Next screen
                                navigateNext()
                            } else if (totalDrag > 100) {
                                // Swiped Right -> Previous screen
                                navigatePrev()
                            }
                        },
                        onHorizontalDrag = { _, dragAmount ->
                            totalDrag += dragAmount
                        }
                    )
                }
        ) {
            when (currentScreen) {
                "parents" -> {
                    ParentsScreen(
                        isRunning = isRunning,
                        stats = stats,
                        onToggle = { toggleVpn(isRunning) },
                        onExitParentsMode = {
                            isParentsMode = false
                            currentScreen = "dashboard"
                        }
                    )
                }
                "config" -> {
                    SettingsScreen(
                        vpsIp = vpsIp,
                        vpsPort = vpsPort,
                        psk = psk,
                        turnMode = turnMode,
                        turnServers = turnServers.toList(),
                        isParentsMode = isParentsMode,
                        activeProfile = activeProfile,
                        onSaveSettings = { newIp, newPort, newPsk, newMode, newServers, newParents, newProfile ->
                            vpsIp = newIp
                            vpsPort = newPort
                            psk = newPsk
                            turnMode = newMode
                            BypassVpnService.currentMode = newMode
                            turnServers.clear()
                            turnServers.addAll(newServers)
                            isParentsMode = newParents
                            activeProfile = newProfile
                            if (newParents) {
                                currentScreen = "parents"
                            }
                        },
                        onOpenDeployer = { currentScreen = "deploy" },
                        onBack = { currentScreen = "dashboard" }
                    )
                }
                "hashes" -> {
                    HashesScreen(
                        currentHashMode = hashMode,
                        hashes = effectiveHashes,
                        onModeChange = { newMode ->
                            hashMode = newMode
                            if (BypassVpnService.activeHandle != 0L) {
                                BypassCore.setHashMode(BypassVpnService.activeHandle, newMode)
                            }
                        },
                        onAddHashes = { newTokens ->
                            val items = newTokens.map { VkHashItem(raw = it, valid = true) }
                            activeHashes.addAll(items)
                            if (BypassVpnService.activeHandle != 0L) {
                                BypassCore.updateHashes(BypassVpnService.activeHandle, newTokens)
                            }
                        },
                        onOpenAutoWebView = {
                            showVkAuthDialog = true
                        },
                        onBack = { currentScreen = "dashboard" }
                    )
                }
                "apps" -> {
                    AppRoutingScreen(
                        currentRoutes = appRoutes.toMap(),
                        onSaveRoutes = { updated ->
                            appRoutes.clear()
                            appRoutes.putAll(updated)
                        },
                        onBack = { currentScreen = "dashboard" }
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
                        onBack = { currentScreen = "config" }
                    )
                }
                else -> {
                    DashboardScreen(
                        isRunning = isRunning,
                        stats = stats,
                        turnMode = turnMode,
                        validHashesCount = validCount,
                        totalHashesCount = effectiveHashes.size.coerceAtLeast(3),
                        turnServers = turnServers.mapIndexed { idx, srv ->
                            val ping = if (idx == 0) 12 else if (idx == 1) 34 else 55
                            srv.substringBefore(':') to ping
                        },
                        onToggleVpn = { toggleVpn(isRunning) },
                        onOpenConfig = { currentScreen = "config" },
                        onOpenHashes = { currentScreen = "hashes" },
                        onOpenApps = { currentScreen = "apps" },
                        onOpenParents = { currentScreen = "parents" },
                        onReconnect = {
                            if (isRunning) {
                                toggleVpn(true)
                                toggleVpn(false)
                            }
                        }
                    )
                }
            }

            if (showVkAuthDialog) {
                VkAuthDialog(
                    onHashesExtracted = { hashes ->
                        hashes.forEach { h ->
                            activeHashes.add(VkHashItem(raw = h, valid = true))
                        }
                        if (BypassVpnService.activeHandle != 0L) {
                            BypassCore.updateHashes(BypassVpnService.activeHandle, hashes)
                        }
                        showVkAuthDialog = false
                    },
                    onDismiss = {
                        showVkAuthDialog = false
                    }
                )
            }
        }
    }

    private fun navigateNext() {
        val idx = screenOrder.indexOf(currentScreen)
        if (idx != -1) {
            val nextIdx = (idx + 1) % screenOrder.size
            currentScreen = screenOrder[nextIdx]
        }
    }

    private fun navigatePrev() {
        val idx = screenOrder.indexOf(currentScreen)
        if (idx != -1) {
            val prevIdx = if (idx - 1 < 0) screenOrder.size - 1 else idx - 1
            currentScreen = screenOrder[prevIdx]
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP -> {
                navigatePrev()
                return true
            }
            KeyEvent.KEYCODE_VOLUME_DOWN -> {
                navigateNext()
                return true
            }
            KeyEvent.KEYCODE_C -> {
                currentScreen = "config"
                return true
            }
            KeyEvent.KEYCODE_H -> {
                currentScreen = "hashes"
                return true
            }
            KeyEvent.KEYCODE_A -> {
                currentScreen = "apps"
                return true
            }
            KeyEvent.KEYCODE_P -> {
                currentScreen = "parents"
                return true
            }
            KeyEvent.KEYCODE_T -> {
                toggleVpn(BypassVpnService.isRunning.value)
                return true
            }
            KeyEvent.KEYCODE_Q -> {
                finish()
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
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

        val turns = JSONArray()
        turnServers.forEach { turns.put(it) }
        root.put("turn_servers", turns)
        root.put("mode", turnMode)
        root.put("rtp_masking", rtpMasking)
        root.put("hash_mode", hashMode)

        val hashesArray = JSONArray()
        activeHashes.forEach { hashesArray.put(it.raw) }
        root.put("vk_hashes", hashesArray)

        // Per-app routing table (directly routes each app either as "turn" or "direct")
        val routesObj = JSONObject()
        appRoutes.forEach { (pkg, route) ->
            routesObj.put(pkg, route)
        }
        root.put("app_routes", routesObj)

        val split = JSONObject().apply {
            put("mode", if (isParentsMode) "all" else "list")
            put("tunnel_apps", JSONArray().apply {
                appRoutes.filter { it.value == "turn" }.keys.forEach { put(it) }
            })
            put("direct_apps", JSONArray().apply {
                appRoutes.filter { it.value == "direct" }.keys.forEach { put(it) }
            })
            put("direct_domains", JSONArray().apply {
                put("sberbank.ru")
                put("gosuslugi.ru")
                put("tinkoff.ru")
                put("vtb.ru")
                put("yandex.ru")
                put("vk.com")
            })
        }
        root.put("split_tunnel", split)
        root.put("health_interval_sec", 3)
        root.put("failover_timeout_sec", 3)
        root.put("mtu", 1280)

        return root.toString()
    }
}
