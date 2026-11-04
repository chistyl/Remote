package com.chi.tvremote

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.chi.tvremote.ui.RootScreen

class MainActivity : ComponentActivity() {
    private val vm: RemoteViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Color(0xFF67D7FF),
                    secondary = Color(0xFF8C9EFF),
                    background = Color(0xFF080B12),
                    surface = Color(0xFF121927),
                    onPrimary = Color(0xFF07111A),
                    onBackground = Color(0xFFEAF2FA),
                    onSurface = Color(0xFFEAF2FA)
                )
            ) {
                Surface(Modifier.fillMaxSize(), color = Color(0xFF080B12)) {
                    RootScreen(vm)
                }
            }
        }
    }
}
