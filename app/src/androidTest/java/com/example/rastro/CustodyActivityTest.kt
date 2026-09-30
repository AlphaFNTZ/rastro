package com.example.rastro

import android.view.WindowManager
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Read-only UI smoke test; preserves the user's carrier preference and packets. */
@RunWith(AndroidJUnit4::class)
class CustodyActivityTest {
    @Test fun navigationAndRecreationKeepCarrierControlsAvailable() {
        ActivityScenario.launch(ChatActivity::class.java).use {
            onView(withId(R.id.chat_carrier)).perform(click())
            onView(withId(R.id.custody_mode)).check(matches(withText(R.string.custody_mode)))
            onView(withId(R.id.custody_back)).perform(click())
            onView(withId(R.id.chat_carrier)).check(matches(isDisplayed()))
        }
        ActivityScenario.launch(CustodyActivity::class.java).use { scenario ->
            scenario.recreate()
            scenario.onActivity { activity ->
                assertTrue(activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0)
            }
            onView(withId(R.id.custody_mode)).check(matches(isDisplayed()))
        }
    }
}