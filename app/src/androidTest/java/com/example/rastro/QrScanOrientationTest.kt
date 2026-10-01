package com.example.rastro

import android.Manifest
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.SystemClock
import android.view.View
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Checks the real scan entry point; does not register contacts or change permission preferences. */
@RunWith(AndroidJUnit4::class)
class QrScanOrientationTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()

    @Test fun preservesPortrait() = verify(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT, Configuration.ORIENTATION_PORTRAIT)
    @Test fun preservesLandscape() = verify(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE, Configuration.ORIENTATION_LANDSCAPE)

    private fun verify(requested: Int, expected: Int) {
        assumeTrue(instrumentation.targetContext.checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
        ActivityScenario.launch(ChatActivity::class.java).use { scenario ->
            try {
                scenario.onActivity { it.requestedOrientation = requested }
                await {
                    val activity = resumed()
                    activity is ChatActivity && activity.resources.configuration.orientation == expected
                }
                scenario.onActivity { assertTrue(it.findViewById<View>(R.id.chat_scan).performClick()) }
                await {
                    val activity = resumed()
                    activity is QrScanActivity && activity.resources.configuration.orientation == expected &&
                        activity.findViewById<View>(R.id.qr_close).isShown
                }
                // Allow the camera surface and CaptureManager's orientation lock to settle.
                SystemClock.sleep(500)
                instrumentation.runOnMainSync {
                    val activity = resumed()
                    assertTrue(activity is QrScanActivity)
                    assertTrue(activity!!.resources.configuration.orientation == expected)
                    assertTrue(activity.findViewById<View>(R.id.qr_close).performClick())
                }
                await { resumed() is ChatActivity }
            } finally {
                instrumentation.runOnMainSync { (resumed() as? QrScanActivity)?.finish() }
            }
        }
    }
    private fun resumed() = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).singleOrNull()
    private fun await(condition: () -> Boolean) {
        val limit = SystemClock.elapsedRealtime() + 10000
        while (SystemClock.elapsedRealtime() < limit) {
            var ready = false
            instrumentation.runOnMainSync { ready = condition() }
            if (ready) return
            SystemClock.sleep(100)
        }
        throw AssertionError("A tela não preservou a orientação ou não concluiu a navegação")
    }
}
