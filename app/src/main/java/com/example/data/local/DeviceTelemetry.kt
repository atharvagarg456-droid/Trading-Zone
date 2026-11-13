package com.example.data.local

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.provider.Settings
import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.UUID

object DeviceTelemetry {

    @SuppressLint("HardwareIds")
    fun getPermanentDeviceId(context: Context): String {
        val androidId = try {
            Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
        } catch (_: Exception) {
            null
        }
        if (!androidId.isNullOrBlank() && androidId != "9774d56d682e549c") {
            return "AND_${androidId.uppercase()}"
        }
        val prefs = context.getSharedPreferences("trading_device_id_prefs", Context.MODE_PRIVATE)
        var fallback = prefs.getString("fallback_device_id", null)
        if (fallback.isNullOrBlank()) {
            fallback = "DEV_${UUID.randomUUID().toString().replace("-", "").take(16).uppercase()}"
            prefs.edit().putString("fallback_device_id", fallback).apply()
        }
        return fallback
    }

    fun getDeviceModelLabel(): String {
        val manufacturer = Build.MANUFACTURER?.trim().orEmpty()
        val model = Build.MODEL?.trim().orEmpty()
        val combined = if (model.startsWith(manufacturer, ignoreCase = true)) {
            model
        } else {
            "$manufacturer $model".trim()
        }
        return combined.ifBlank { "Android Device" }
    }

    fun getDeviceIpAddress(): String {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces() ?: return "0.0.0.0"
            for (intf in interfaces) {
                if (!intf.isUp || intf.isLoopback) continue
                val addrs = intf.inetAddresses
                for (addr in addrs) {
                    if (!addr.isLoopbackAddress && addr is Inet4Address) {
                        val host = addr.hostAddress
                        if (!host.isNullOrBlank()) return host
                    }
                }
            }
        } catch (_: Exception) {
        }
        return "0.0.0.0"
    }
}
