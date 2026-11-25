package com.aiza.app

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aiza.app.ring.PhoneRing
import com.aiza.app.ui.ring.PhoneRingScreen
import com.aiza.app.ui.theme.AizaTheme
import com.aiza.data.tags.TagIcon
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
class PhoneRingScreenTest {
    @get:Rule val rule = createComposeRule()

    private fun shot(name: String, dark: Boolean) {
        rule.setContent {
            AizaTheme(darkTheme = dark) {
                PhoneRingScreen(PhoneRing("A", "Keys", TagIcon.KEYS, startedAtMs = 0), onStop = {}, nowMs = { 8_000 })
            }
        }
        rule.mainClock.advanceTimeBy(1_000)
        rule.onRoot().captureRoboImage("build/outputs/roborazzi/$name.png")
    }

    @Test fun light() = shot("r1_phone_ring", dark = false)
    @Test fun dark() = shot("r2_phone_ring_dark", dark = true)
}
