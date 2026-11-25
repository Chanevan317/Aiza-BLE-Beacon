package com.aiza.app

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import com.aiza.app.ui.AizaRoot
import com.aiza.app.ui.theme.AizaTheme
import com.aiza.app.ui.theme.isDark
import com.aiza.data.tags.ThemeMode

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        val container = (application as AizaApplication).container
        // Hold the system splash only until settings (theme) are loaded, so the first frame has the right theme.
        installSplashScreen().setKeepOnScreenCondition { container.settings.value == null }
        super.onCreate(savedInstanceState)
        handleIntent(intent)
        // While the tag rings the phone, show the ring screen over the lock screen and wake the display (FR-RNG-02).
        lifecycleScope.launch {
            container.phoneRinger.ringing.collect { ring ->
                setShowWhenLocked(ring != null)
                setTurnScreenOn(ring != null)
            }
        }
        setContent {
            val settings by container.settings.collectAsStateWithLifecycle()
            val dark = (settings?.themeMode ?: ThemeMode.SYSTEM).isDark()
            DisposableEffect(dark) {
                val style = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark }
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                onDispose {}
            }
            AizaTheme(darkTheme = dark) {
                AizaRoot(container)
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    /** A left-behind notification opens its tag. */
    private fun handleIntent(intent: android.content.Intent?) {
        val address = intent?.getStringExtra(EXTRA_OPEN_TAG) ?: return
        intent.removeExtra(EXTRA_OPEN_TAG)
        (application as AizaApplication).container.openTagRequest.value = address
    }

    companion object {
        const val EXTRA_OPEN_TAG = "open_tag"
    }
}
