package com.example.parentalcontrol.features.network_scanner.models

import kotlinx.serialization.Serializable

/**
 * DiscoveredDevice — يمثل جهازاً مكتشفاً على الشبكة المحلية (IP, MAC, Vendor)
 */
@Serializable
data class DiscoveredDevice(
    val ip: String,
    val mac: String,
    val vendor: String? = null,
    val hostname: String? = null,
    val isReachable: Boolean = true
)

/**
 * DeviceAsset — نموذج مزامنة الأجهزة المكتشفة مع جدول device_assets في Supabase
 */
@Serializable
data class DeviceAsset(
    val gateway_device_id: String,
    val ssid_name: String?,
    val client_name: String?,
    val ip_address: String?,
    val mac_address: String?,
    val is_verified: Boolean = true
)
