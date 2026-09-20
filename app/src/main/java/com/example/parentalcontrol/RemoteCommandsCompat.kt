package com.example.parentalcontrol

// جسور التوافق لضمان عدم تعطل أي كود قديم في أي مكان في المشروع
typealias SupabaseManager = com.example.parentalcontrol.features.remote_commands.services.SupabaseManager
typealias BrowserHistoryRecord = com.example.parentalcontrol.features.remote_commands.services.BrowserHistoryRecord
typealias NotificationLog = com.example.parentalcontrol.features.remote_commands.services.NotificationLog
typealias ErrorLog = com.example.parentalcontrol.features.remote_commands.services.ErrorLog
typealias RemoteLog = com.example.parentalcontrol.features.remote_commands.services.RemoteLog
typealias HeartbeatPayload = com.example.parentalcontrol.features.remote_commands.services.HeartbeatPayload
typealias FileInfo = com.example.parentalcontrol.features.remote_commands.services.FileInfo

typealias RemoteConfigManager = com.example.parentalcontrol.features.remote_commands.services.RemoteConfigManager
typealias RealtimeCommandManager = com.example.parentalcontrol.features.remote_commands.services.RealtimeCommandManager

typealias AppSettings = com.example.parentalcontrol.features.remote_commands.models.AppSettings
typealias DefaultSettingsPayload = com.example.parentalcontrol.features.remote_commands.models.DefaultSettingsPayload
typealias RemoteConfig = com.example.parentalcontrol.features.remote_commands.models.RemoteConfig
typealias CommandRecord = com.example.parentalcontrol.features.remote_commands.models.CommandRecord
typealias RemoteCommand = com.example.parentalcontrol.features.remote_commands.models.RemoteCommand
