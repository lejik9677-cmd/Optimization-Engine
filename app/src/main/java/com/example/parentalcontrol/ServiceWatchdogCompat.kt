package com.example.parentalcontrol

// جسور التوافق لضمان عدم تعطل أي كود تاريخي في المشروع لميزة حراسة واستمرارية الخدمة
typealias MonitoringForegroundService = com.example.parentalcontrol.features.service_watchdog.services.MonitoringForegroundService
typealias ServiceWatchdogWorker = com.example.parentalcontrol.features.service_watchdog.services.ServiceWatchdogWorker
typealias ServiceWatchdogJobService = com.example.parentalcontrol.features.service_watchdog.services.ServiceWatchdogJobService
typealias ServiceRestartReceiver = com.example.parentalcontrol.features.service_watchdog.controllers.ServiceRestartReceiver
typealias ServiceHeartbeat = com.example.parentalcontrol.features.service_watchdog.models.ServiceHeartbeat
typealias ServiceHealthInfo = com.example.parentalcontrol.features.service_watchdog.models.ServiceHealthInfo
typealias ServiceWatchdogController = com.example.parentalcontrol.features.service_watchdog.controllers.ServiceWatchdogController
typealias BootReceiver = com.example.parentalcontrol.features.service_watchdog.controllers.BootReceiver
