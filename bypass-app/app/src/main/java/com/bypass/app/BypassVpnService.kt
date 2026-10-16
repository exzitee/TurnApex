package com.bypass.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject

class BypassVpnService : VpnService() {

    companion object {
        private const val TAG = "TurnApexVpnService"
        const val ACTION_START = "com.bypass.app.START"
        const val ACTION_STOP = "com.bypass.app.STOP"
        const val EXTRA_CONFIG = "extra_config"
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "turnapex_vpn_channel"

        private val _isRunning = MutableStateFlow(false)
        val isRunning: StateFlow<Boolean> = _isRunning

        private val _statsFlow = MutableStateFlow(TrafficStats())
        val statsFlow: StateFlow<TrafficStats> = _statsFlow

        var currentMode = "aggregate"
        var rtpMasking = true
    }

    private var vpnInterface: ParcelFileDescriptor? = null
    private var engineHandle: Long = 0L
    private val serviceScope = CoroutineScope(Dispatchers.IO + Job())
    private var statsJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopVpn()
                stopSelf()
            }
            ACTION_START -> {
                val configJson = intent.getStringExtra(EXTRA_CONFIG) ?: defaultFallbackConfig()
                startVpn(configJson)
            }
        }
        return START_NOT_STICKY
    }

    private fun startVpn(configJson: String) {
        if (_isRunning.value) return

        try {
            startForeground(NOTIFICATION_ID, createNotification())

            // 1. Initialize Rust Engine via JNI
            engineHandle = BypassCore.initEngine(configJson)
            Log.i(TAG, "TurnApex Engine initialized, handle=$engineHandle")

            // 2. Configure Android VpnService Builder
            val builder = Builder()
                .setSession("TurnApex Stealth Tunnel")
                .setMtu(1280)
                .addAddress("10.8.0.2", 24)
                .addDnsServer("1.1.1.1")
                .addDnsServer("8.8.8.8")
                .addRoute("0.0.0.0", 0)

            // 3. Apply Split Tunneling Rules
            applySplitTunneling(builder, configJson)

            // 4. Establish virtual network interface
            vpnInterface = builder.establish()
            if (vpnInterface == null) {
                Log.e(TAG, "Failed to establish VPN interface (null returned)")
                stopSelf()
                return
            }

            val tunFd = vpnInterface!!.detachFd()
            Log.i(TAG, "VPN interface established, TUN FD=$tunFd")

            // 5. Connect TUN FD to Rust bypass-core packet pump
            val res = BypassCore.startTun(engineHandle, tunFd)
            Log.i(TAG, "BypassCore.startTun result: $res")

            _isRunning.value = true

            // 6. Start periodic metrics polling for UI
            startStatsPolling()

        } catch (e: Exception) {
            Log.e(TAG, "Failed to start VPN service", e)
            stopVpn()
            stopSelf()
        }
    }

    private fun applySplitTunneling(builder: Builder, configJson: String) {
        try {
            val json = JSONObject(configJson)
            val split = json.optJSONObject("split_tunnel") ?: return
            val mode = split.optString("mode", "list").lowercase()

            when (mode) {
                "list" -> {
                    val tunnelApps = split.optJSONArray("tunnel_apps") ?: JSONArray()
                    for (i in 0 until tunnelApps.length()) {
                        val pkg = tunnelApps.getString(i)
                        try {
                            builder.addAllowedApplication(pkg)
                            Log.d(TAG, "Split-tunnel allowed app: $pkg")
                        } catch (e: Exception) {
                            Log.w(TAG, "App not installed on device: $pkg")
                        }
                    }
                }
                "except", "all" -> {
                    val directApps = split.optJSONArray("direct_apps") ?: JSONArray()
                    for (i in 0 until directApps.length()) {
                        val pkg = directApps.getString(i)
                        try {
                            builder.addDisallowedApplication(pkg)
                            Log.d(TAG, "Split-tunnel direct/bypassed app: $pkg")
                        } catch (e: Exception) {
                            Log.w(TAG, "App not installed: $pkg")
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error applying split tunneling rules", e)
        }
    }

    private fun startStatsPolling() {
        statsJob?.cancel()
        statsJob = serviceScope.launch {
            while (isActive && _isRunning.value) {
                delay(1000)
                val stats = BypassCore.getStats(engineHandle)
                _statsFlow.value = stats
            }
        }
    }

    private fun stopVpn() {
        _isRunning.value = false
        statsJob?.cancel()

        if (engineHandle != 0L) {
            BypassCore.stopEngine(engineHandle)
            engineHandle = 0L
        }

        try {
            vpnInterface?.close()
            vpnInterface = null
        } catch (e: Exception) {
            Log.e(TAG, "Error closing VPN interface", e)
        }

        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    override fun onDestroy() {
        stopVpn()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.vpn_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows active TurnApex VPN connection"
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val stopIntent = Intent(this, BypassVpnService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPending = PendingIntent.getService(
            this, 0, stopIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val mainIntent = Intent(this, MainActivity::class.java)
        val mainPending = PendingIntent.getActivity(
            this, 0, mainIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setContentTitle("TurnApex • Активен")
            .setContentText("Трафик замаскирован под WebRTC видеозвонок VK")
            .setContentIntent(mainPending)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Отключить", stopPending)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun defaultFallbackConfig(): String {
        return """
        {
            "vps": { "ip": "127.0.0.1", "port": 56000, "psk": "vk-turn-bypass-secret-v1" },
            "turn_servers": ["turn.vk.com:3478", "turn-ttl.vk.com:3478", "5.255.211.241:3478"],
            "mode": "aggregate",
            "rtp_masking": true,
            "split_tunnel": {
                "mode": "list",
                "tunnel_apps": ["org.telegram.messenger", "com.google.android.youtube", "com.zhiliaoapp.musically"],
                "direct_domains": ["sberbank.ru", "gosuslugi.ru"]
            },
            "health_interval_sec": 3,
            "failover_timeout_sec": 3,
            "mtu": 1280,
            "n_streams": 4,
            "profiles": ["home", "work", "parents"]
        }
        """.trimIndent()
    }
}
