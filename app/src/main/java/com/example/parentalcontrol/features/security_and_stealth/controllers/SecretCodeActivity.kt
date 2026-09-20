package com.example.parentalcontrol.features.security_and_stealth.controllers

import android.app.Activity
import android.os.Bundle
import android.util.Log
import com.example.parentalcontrol.features.security_and_stealth.services.StealthManager

/**
 * SecretCodeActivity v29 (Trampoline)
 *
 * Catches *#1356# if delivered as an Activity intent.
 * Triggers the same logic as the Receiver.
 */
class SecretCodeActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        try {
            Log.i("SecretCodeActivity", "Dialer Activity triggered — revealing app")
            
            // Toggle visibility (Show icon + Launch)
            StealthManager.toggleStealthMode(this)
            
            finish()
        } catch (e: Exception) {
            Log.e("SecretCodeActivity", "Failed to launch from dialer: ${e.message}")
            finish()
        }
    }
}
