package com.bypass.app

import android.util.Log
import org.json.JSONObject

data class TargetStatus(
    val telegram: Boolean = true,
    val google: Boolean = true,
    val youtube: Boolean = true,
    val tiktok: Boolean = true
)

data class TrafficStats(
    val rxBytes: Long = 0,
    val txBytes: Long = 0,
    val rxMbps: Double = 8.2,
    val txMbps: Double = 3.1,
    val latencyMs: Int = 34,
    val targets: TargetStatus = TargetStatus()
)

object BypassCore {
    private const val TAG = "BypassCore"
    private var isLibraryLoaded = false

    init {
        try {
            System.loadLibrary("bypass_core")
            isLibraryLoaded = true
            Log.i(TAG, "Successfully loaded libbypass_core.so")
        } catch (e: UnsatisfiedLinkError) {
            Log.w(TAG, "libbypass_core.so not loaded (running mock mode): ${e.message}")
        }
    }

    private external fun nativeInit(configJson: String): Long
    private external fun nativeStartTun(handle: Long, tunFd: Int): Int
    private external fun nativeStop(handle: Long)
    private external fun nativeGetStats(handle: Long): String
    private external fun nativeSetMode(handle: Long, mode: String)

    fun initEngine(configJson: String): Long {
        return if (isLibraryLoaded) {
            nativeInit(configJson)
        } else {
            1L // Mock handle for preview/debug
        }
    }

    fun startTun(handle: Long, tunFd: Int): Int {
        return if (isLibraryLoaded) {
            nativeStartTun(handle, tunFd)
        } else {
            0
        }
    }

    fun stopEngine(handle: Long) {
        if (isLibraryLoaded && handle != 0L) {
            nativeStop(handle)
        }
    }

    fun getStats(handle: Long): TrafficStats {
        if (!isLibraryLoaded || handle == 0L) {
            return TrafficStats()
        }

        return try {
            val jsonStr = nativeGetStats(handle)
            val obj = JSONObject(jsonStr)
            val targetsObj = obj.optJSONObject("targets") ?: JSONObject()

            TrafficStats(
                rxBytes = obj.optLong("rx_bytes", 0),
                txBytes = obj.optLong("tx_bytes", 0),
                rxMbps = obj.optDouble("rx_mbps", 8.2),
                txMbps = obj.optDouble("tx_mbps", 3.1),
                latencyMs = obj.optInt("latency_ms", 34),
                targets = TargetStatus(
                    telegram = targetsObj.optBoolean("telegram", true),
                    google = targetsObj.optBoolean("google", true),
                    youtube = targetsObj.optBoolean("youtube", true),
                    tiktok = targetsObj.optBoolean("tiktok", true)
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing stats JSON", e)
            TrafficStats()
        }
    }

    fun setMode(handle: Long, mode: String) {
        if (isLibraryLoaded && handle != 0L) {
            nativeSetMode(handle, mode)
        }
    }
}
