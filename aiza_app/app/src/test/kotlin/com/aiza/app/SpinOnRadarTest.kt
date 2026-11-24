package com.aiza.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aiza.app.ui.search.DirectionHint
import com.aiza.app.ui.search.Radar
import com.aiza.app.ui.search.SearchState
import com.aiza.app.ui.search.SpinUi
import com.aiza.app.ui.search.heatColor
import com.aiza.app.ui.search.hintMode
import com.aiza.app.ui.theme.AizaTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.cos

/** The spin needs live sensors, which Robolectric lacks, so render the radar and hint from made-up states. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h600dp-xxhdpi")
class SpinOnRadarTest {
    @get:Rule val rule = createComposeRule()

    private fun bins(covered: Int) = List(72) { i ->
        if (i >= covered) Float.NaN else (-72f + 6f * cos(Math.toRadians(i * 5.0 - 60.0)).toFloat())
    }

    private fun shot(name: String, s: SearchState, dark: Boolean = false) {
        rule.setContent {
            AizaTheme(darkTheme = dark) {
                Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(20.dp)) {
                    Radar(s, 0.3f, heatColor(0.3f))
                    DirectionHint(hintMode(s), s.spin?.coverage ?: 0f)
                }
            }
        }
        rule.mainClock.advanceTimeBy(2_000)
        rule.onRoot().captureRoboImage("build/outputs/roborazzi/$name.png")
    }

    @Test fun spinning() = shot("s1_radar_spinning", SearchState(headingDeg = 200f, spin = SpinUi(0.55f, bins(40))))
    @Test fun turnToArea() = shot("s2_radar_turn_to_area", SearchState(headingDeg = 250f, startAreaDeg = 60f), dark = true)
    @Test fun facingArea() = shot("s3_radar_facing_area", SearchState(headingDeg = 75f, startAreaDeg = 60f))
}
