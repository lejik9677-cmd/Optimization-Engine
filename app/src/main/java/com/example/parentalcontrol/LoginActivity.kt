package com.example.parentalcontrol

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.inputmethod.EditorInfo
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.parentalcontrol.databinding.ActivityLoginBinding

/**
 * LoginActivity v21
 *
 * Entry point shown after secret-code reveals the app.
 * PIN = same as secret code digits.
 *
 * Hardening:
 *  • No installSplashScreen() — avoids crashes on Samsung low-memory kill
 *  • Direct theme (Theme.SyncService) — no splash flicker
 *  • Auto-launch MainActivity after correct PIN (no need to tap button)
 */
class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private val DEFAULT_PIN = "1356365508"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Safety: if somehow the app is opened but the alias is in wrong state, fix it
        StealthManager.showAppIcon(this)

        try {
            binding = ActivityLoginBinding.inflate(layoutInflater)
            setContentView(binding.root)
        } catch (e: Exception) {
            // Fallback: launch MainActivity directly if binding fails
            startActivity(Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            })
            finish()
            return
        }

        setupLoginUI()
    }

    private fun setupLoginUI() {
        // Login on button tap
        binding.btnLogin.setOnClickListener { attemptLogin() }

        // Login on keyboard "Done" / Enter
        binding.etPin.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE ||
                actionId == EditorInfo.IME_ACTION_GO) {
                attemptLogin()
                true
            } else false
        }
    }

    private fun attemptLogin() {
        val enteredPin = binding.etPin.text?.toString()?.trim() ?: ""
        if (enteredPin == DEFAULT_PIN) {
            binding.tilPin.error = null
            binding.btnLogin.isEnabled = false
            // Brief delay to dismiss keyboard cleanly
            Handler(Looper.getMainLooper()).postDelayed({
                val intent = Intent(this, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                }
                startActivity(intent)
                finish()
            }, 150)
        } else {
            binding.tilPin.error = "رمز PIN غير صحيح"
            binding.etPin.selectAll()
            Toast.makeText(this, "الرمز المدخل خاطئ، حاول مرة أخرى", Toast.LENGTH_SHORT).show()
        }
    }
}
