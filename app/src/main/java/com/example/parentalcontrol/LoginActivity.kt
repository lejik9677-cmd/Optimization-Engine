package com.example.parentalcontrol

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.parentalcontrol.databinding.ActivityLoginBinding

/**
 * LoginActivity v21-diagnostic
 * Ultra-simple: no splash screen, no StealthManager calls in onCreate.
 * PIN = 1356365508
 */
class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private val DEFAULT_PIN = "1356365508"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnLogin.setOnClickListener {
            val pin = binding.etPin.text?.toString()?.trim() ?: ""
            if (pin == DEFAULT_PIN) {
                startActivity(Intent(this, MainActivity::class.java))
                finish()
            } else {
                binding.tilPin.error = "رمز PIN غير صحيح"
                Toast.makeText(this, "الرمز خاطئ", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
