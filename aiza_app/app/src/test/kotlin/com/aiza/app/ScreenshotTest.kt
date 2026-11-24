package com.aiza.app

import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.robolectric.shadows.ShadowLooper
import java.util.concurrent.TimeUnit
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.RobolectricTestRunner

/**
 * Renders the real app (demo mode) on the JVM and saves PNGs to app/build/outputs/roborazzi.
 * Run: ./gradlew :app:recordRoborazziDebug
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
abstract class ScreenshotFlow {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    abstract val suffix: String

    private fun shot(name: String) = rule.onRoot().captureRoboImage("build/outputs/roborazzi/${name}_$suffix.png", RoborazziOptions())

    private fun has(text: String) = rule.onAllNodes(hasText(text)).fetchSemanticsNodes().isNotEmpty()

    /** performScrollTo needs the auto-advancing clock; switch it on just for the scroll. */
    private fun scrollTo(text: String) {
        rule.mainClock.autoAdvance = true
        rule.onNodeWithText(text, useUnmergedTree = true).performScrollTo()
        rule.mainClock.autoAdvance = false
    }

    private fun tap(text: String) = rule.onNodeWithText(text, useUnmergedTree = true).performClick()

    /** Settings are saved on a background thread in real time; poll while pumping the main looper. */
    private fun waitFor(condition: () -> Boolean) {
        val end = System.currentTimeMillis() + 5_000
        while (!condition()) {
            check(System.currentTimeMillis() < end) { "Timed out" }
            Thread.sleep(20); settle(20)
        }
    }

    private fun settle(ms: Long) {
        // Advance Compose's clock and the main looper (coroutine delays) together, in small steps.
        rule.mainClock.autoAdvance = false
        var left = ms
        while (left > 0) {
            val step = minOf(left, 50L)
            rule.mainClock.advanceTimeBy(step)
            ShadowLooper.idleMainLooper(step, TimeUnit.MILLISECONDS)
            left -= step
        }
        rule.waitForIdle()
    }

    @Test
    fun flow() {
        settle(300); shot("01_splash")
        settle(1_100); shot("01b_splash_wordmark")
        settle(3_000)
        waitFor { has("Welcome to AIZA") || has("Your tags") }
        if (has("Welcome to AIZA")) {
            settle(600); shot("01c_onboarding_welcome")
            tap("Next"); settle(700); shot("01d_onboarding_search")
            tap("Next"); settle(700); shot("01e_onboarding_privacy")
            tap("Next"); settle(700); shot("01f_onboarding_permissions")
            tap("Not now")
        }
        waitFor { has("Your tags") }
        settle(800); shot("02_home")
        rule.onNodeWithContentDescription("Card view").performClick()
        waitFor { rule.onAllNodes(hasText("Nearby")).fetchSemanticsNodes().isNotEmpty() }
        settle(800); shot("02b_home_cards")
        rule.onNodeWithContentDescription("List view").performClick()
        waitFor { rule.onAllNodes(hasText("Nearby")).fetchSemanticsNodes().isEmpty() }
        settle(800)

        tap("Add tag"); settle(900); shot("03_pairing_intro")
        tap("It's beeping — continue"); settle(4_000); shot("04_pairing_found")
        tap("AIZA Tag 1"); settle(900); shot("05_pairing_name")
        rule.onNodeWithContentDescription("Back").performClick(); settle(300)
        rule.onNodeWithContentDescription("Back").performClick(); settle(300)
        tap("Not now"); settle(900)

        tap("Keys"); settle(900); shot("06_tag")
        tap("Start active search"); settle(2_500); shot("07_search")
        settle(12_000); shot("08_search_wedge")
        settle(25_000); shot("09_search_hot")
    }

    @Test
    fun researcher() {
        settle(3_000)
        waitFor { has("Welcome to AIZA") || has("Your tags") }
        if (has("Welcome to AIZA")) { tap("Skip"); settle(700); tap("Not now") }
        waitFor { has("Your tags") }
        settle(500)
        rule.onNodeWithContentDescription("Settings").performClick(); settle(700)
        scrollTo("Researcher mode"); tap("Researcher mode"); settle(700)
        shot("r1_researcher_empty")
        tap("New trial"); settle(700)
        rule.onNode(hasText("Place (e.g. Home 1, Office)") and hasSetTextAction()).performTextInput("Home 1")
        rule.onNode(hasText("Room") and hasSetTextAction()).performTextInput("Living room")
        settle(300); shot("r2_new_trial")
        tap("Continue to consent"); settle(700); shot("r3_consent")
        scrollTo("I have read this and agree to take part."); tap("I have read this and agree to take part.")
        tap("I agree — start the trial"); settle(3_000); shot("r4_trial_running")
        tap("Checkpoint"); settle(1_000)
        tap("Found it")
        waitFor { has("Time to find") }
        settle(700); shot("r5_trial_detail")
        rule.onNodeWithContentDescription("Back").performClick(); settle(700); shot("r6_researcher_list")
    }
}

class LightScreens : ScreenshotFlow() { override val suffix = "light" }

@Config(qualifiers = "+night")
class DarkScreens : ScreenshotFlow() { override val suffix = "dark" }
