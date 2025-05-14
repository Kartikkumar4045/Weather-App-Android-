package com.example.wheatherapp

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity

class SplashActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Access shared preferences
        val prefs = getSharedPreferences("MyPrefs", MODE_PRIVATE)
        val isFirstLaunch = prefs.getBoolean("isFirstLaunch", true)

        if (isFirstLaunch) {
            setContentView(R.layout.activity_splash)

            // Mark that splash has been shown
            prefs.edit().putBoolean("isFirstLaunch", false).apply()

            Handler(Looper.getMainLooper()).postDelayed({
                startActivity(Intent(this, MainActivity::class.java))
                finish()
            }, 2000)
        } else {
            // Skip splash and go straight to MainActivity
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }
    }
}
