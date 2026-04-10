package com.example.parentalcontrol

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.example.parentalcontrol.databinding.ActivityLoginBinding

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private val DEFAULT_PIN = "1356365508"

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnLogin.setOnClickListener {
            val enteredPin = binding.etPin.text.toString()
            if (enteredPin == DEFAULT_PIN) {
                // حفظ أن المستخدم دخل بنجاح لهذه الجلسة
                val intent = Intent(this, MainActivity::class.java)
                startActivity(intent)
                finish()
            } else {
                binding.tilPin.error = "رمز PIN غير صحيح"
                Toast.makeText(this, "الرمز المدخل خاطئ، حاول مرة أخرى", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
