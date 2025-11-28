package com.example.pocketbudget

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.FirebaseApp // Import added

class SplashActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)


        // Explicitly initialize Firebase to prevent the "Default FirebaseApp is not initialized" crash
        if (FirebaseApp.getApps(this).isEmpty()) {
            FirebaseApp.initializeApp(this)
        }


        // Wait for 2 seconds
        Handler(Looper.getMainLooper()).postDelayed({
            checkUserStatus()
        }, 2000)
    }

    private fun checkUserStatus() {
        val currentUser = FirebaseAuth.getInstance().currentUser
        if (currentUser != null) {
            // User is already logged in, go to Main Dashboard
            startActivity(Intent(this, MainActivity::class.java))
        } else {
            // No user found, go to Login
            startActivity(Intent(this, LoginActivity::class.java))
        }
        finish() // Close SplashActivity so user can't go back to it
    }
}