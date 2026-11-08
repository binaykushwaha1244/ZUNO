package com.binay.vscroll

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.binay.vscroll.data.preferences.ThemePreferencesRepository
import com.binay.vscroll.ui.theme.VScrollTheme
import com.binay.vscroll.ui.theme.VScrollThemeMode

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val themeRepo = remember { ThemePreferencesRepository(applicationContext) }
            val currentTheme by themeRepo.themeMode.collectAsStateWithLifecycle(initialValue = VScrollThemeMode.ZUNO)

            VScrollTheme(themeMode = currentTheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainNavigation()
                }
            }
        }
    }
}
