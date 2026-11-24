package com.aiza.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aiza.app.ui.components.CompassDial
import com.aiza.app.ui.theme.AizaTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The dial only draws with a live heading, which Robolectric lacks, so render it at fixed headings. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
class CompassDialTest {
    @get:Rule val rule = createComposeRule()

    @Test
    fun dial() {
        rule.setContent {
            AizaTheme(darkTheme = false) {
                Row(Modifier.background(MaterialTheme.colorScheme.background).padding(24.dp)) {
                    CompassDial(0f, Modifier.size(180.dp), labelSize = 15.sp, frontSize = 16.dp)
                    CompassDial(30f, Modifier.size(180.dp), labelSize = 15.sp, frontSize = 16.dp)
                }
            }
        }
        rule.onRoot().captureRoboImage("build/outputs/roborazzi/compass_dial.png")
    }
}
