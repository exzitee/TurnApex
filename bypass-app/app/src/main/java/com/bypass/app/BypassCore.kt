package com.bypass.app

import android.util.Log
import org.json.JSONArray
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

data class VkHashItem(
    val raw: String,
    val username: String = "",
    val password: String = "",
    val server: String = "turn.vk.com",
    val port: Int = 3478,
    val activeAllocations: Int = 0,
    val valid: Boolean = true
) {
    val masked: String
        get() {
            val clean = raw.trim()
            return if (clean.length > 8) {
                "${clean.substring(0, 4)}...${clean.substring(clean.length - 4)}"
            } else if (clean.isNotEmpty()) {
                "****"
            } else {
                "—"
            }
        }
}

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
    private external fun nativeUpdateHashes(handle: Long, hashesStr: String): Int
    private external fun nativeGetHashStatus(handle: Long): String
    private external fun nativeSetHashMode(handle: Long, mode: String)
    private external fun nativeCheckVkHashes(hashesStr: String): String

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

    fun updateHashes(handle: Long, hashes: List<String>): Int {
        if (!isLibraryLoaded || handle == 0L) return hashes.size
        return try {
            val jsonArray = JSONArray(hashes)
            nativeUpdateHashes(handle, jsonArray.toString())
        } catch (e: Exception) {
            Log.e(TAG, "Error updating hashes", e)
            0
        }
    }

    fun getHashes(handle: Long): List<VkHashItem> {
        if (!isLibraryLoaded || handle == 0L) return emptyList()
        return try {
            val jsonStr = nativeGetHashStatus(handle)
            val array = JSONArray(jsonStr)
            val list = mutableListOf<VkHashItem>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(
                    VkHashItem(
                        raw = obj.optString("raw"),
                        username = obj.optString("username"),
                        password = obj.optString("password"),
                        server = obj.optString("server", "turn.vk.com"),
                        port = obj.optInt("port", 3478),
                        activeAllocations = obj.optInt("active_allocations", 1),
                        valid = obj.optBoolean("valid", true)
                    )
                )
            }
            list
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing hashes", e)
            emptyList()
        }
    }

    fun setHashMode(handle: Long, mode: String) {
        if (isLibraryLoaded && handle != 0L) {
            nativeSetHashMode(handle, mode)
        }
    }

    fun checkVkHashes(hashes: List<String>): Map<String, Boolean> {
        if (!isLibraryLoaded) {
            return hashes.associateWith { true }
        }
        return try {
            val jsonArray = JSONArray(hashes)
            val resultStr = nativeCheckVkHashes(jsonArray.toString())
            val resArray = JSONArray(resultStr)
            val map = mutableMapOf<String, Boolean>()
            for (i in 0 until resArray.length()) {
                val obj = resArray.getJSONObject(i)
                map[obj.optString("hash")] = obj.optBoolean("valid", false)
            }
            map
        } catch (e: Exception) {
            Log.e(TAG, "Error checking VK hashes", e)
            hashes.associateWith { false }
        }
    }
}
