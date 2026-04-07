package com.example.parentalcontrol

import android.app.Application

/**
 * Custom Application class for initialization.
 */
class OptimizationApp : Application() {

    override fun onCreate() {
        super.onCreate()
        
        // Initialize the global crash handler first
        GlobalExceptionHandler.initialize(this)

        // Start watchdogs for maximum persistence
        ServiceWatchdogJobService.schedule(this)
        ServiceWatchdogWorker.schedule(this)
    }
}
