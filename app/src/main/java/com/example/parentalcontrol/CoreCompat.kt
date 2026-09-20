package com.example.parentalcontrol

/**
 * جسر التوافق التراجعي للنواة المشتركة (Core)
 * ─────────────────────────────────────────────────────────────
 * يوفر أسماء مستعارة (typealiases) للمكونات المنقولة لضمان استقرار
 * أي استدعاءات قديمة أو خارجية.
 */
typealias OptimizationApp = com.example.parentalcontrol.core.base.OptimizationApp
typealias MainActivity = com.example.parentalcontrol.core.ui.MainActivity
typealias AppConstants = com.example.parentalcontrol.core.config.AppConstants
typealias NetworkConnectivityHelper = com.example.parentalcontrol.core.network.NetworkConnectivityHelper
