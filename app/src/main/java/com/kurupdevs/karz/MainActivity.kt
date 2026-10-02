package com.kurupdevs.karz

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.kurupdevs.karz.di.ServiceLocator
import com.kurupdevs.karz.nav.AppNavHost
import com.kurupdevs.karz.ui.theme.KarzTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Firebase phone auth needs an Activity for the reCAPTCHA fallback.
        ServiceLocator.auth.bindActivity(this)
        enableEdgeToEdge()
        setContent {
            KarzTheme {
                AppNavHost()
            }
        }
    }
}
