package com.example.parentalcontrol

import android.content.Context
import android.net.wifi.WifiManager
import android.text.format.Formatter
import android.util.Log
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.FileReader
import java.net.InetAddress
import kotlinx.serialization.Serializable

@Serializable
data class DeviceAsset(
    val gateway_device_id: String,
    val ssid_name: String?,
    val client_name: String?,
    val ip_address: String?,
    val mac_address: String?,
    val is_verified: Boolean = true
)

class LocalNetworkScanner(private val context: Context, private val deviceId: String) {

    suspend fun scanAndUploadAssets() = withContext(Dispatchers.IO) {
        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val connectionInfo = wifiManager.connectionInfo
        val rawSsid = connectionInfo.ssid ?: "Unknown Wi-Fi"
        val ssid = if (rawSsid.startsWith("\"") && rawSsid.endsWith("\"")) {
            rawSsid.substring(1, rawSsid.length - 1)
        } else rawSsid

        val ipAddressInt = connectionInfo.ipAddress
        if (ipAddressInt == 0) return@withContext // Not connected to Wi-Fi

        val ipString = Formatter.formatIpAddress(ipAddressInt)
        val prefix = ipString.substring(0, ipString.lastIndexOf(".") + 1)

        Log.d("NetworkScanner", "Scanning subnet: ${prefix}0/24")

        // 1. Send quick pings to update local ARP cache
        for (i in 1..254) {
            val testIp = "$prefix$i"
            if (testIp == ipString) continue
            try {
                val address = InetAddress.getByName(testIp)
                address.isReachable(100)
            } catch (e: Exception) {
                // Ignore
            }
        }

        // 2. Read ARP table /proc/net/arp
        val assetsList = mutableListOf<DeviceAsset>()
        try {
            val br = BufferedReader(FileReader("/proc/net/arp"))
            var line: String?
            br.readLine() // Skip header
            while (br.readLine().also { line = it } != null) {
                val parts = line!!.split("\\s+".toRegex()).filter { it.isNotBlank() }
                if (parts.size >= 4) {
                    val ip = parts[0]
                    val mac = parts[3]
                    if (mac != "00:00:00:00:00:00" && mac.contains(":")) {
                        var hostname = "Local Network Device"
                        try {
                            hostname = InetAddress.getByName(ip).canonicalHostName
                            if (hostname == ip) hostname = "Local Client"
                        } catch (e: Exception) {}

                        assetsList.add(
                            DeviceAsset(
                                gateway_device_id = deviceId,
                                ssid_name = ssid,
                                client_name = hostname,
                                ip_address = ip,
                                mac_address = mac,
                                is_verified = true
                            )
                        )
                    }
                }
            }
            br.close()
        } catch (e: Exception) {
            Log.e("NetworkScanner", "Error reading ARP table: ${e.message}")
        }

        // 3. Upload to Supabase
        if (assetsList.isNotEmpty()) {
            try {
                val client = SupabaseManager.getInstance().getClient()
                if (client != null) {
                    client.postgrest.from("device_assets").upsert(assetsList)
                    Log.i("NetworkScanner", "Uploaded ${assetsList.size} devices successfully.")
                }
            } catch (e: Exception) {
                Log.e("NetworkScanner", "Failed to upload assets: ${e.message}")
            }
        }
    }
}
