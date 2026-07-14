package com.shanqijie.fitnessapp

import android.content.Context
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MainActivityInstrumentedTest {
    @Test
    fun realLauncherActivityCreatesComposeRootAndReachesResumedState() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.deleteDatabase("fitness.db")

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.moveToState(Lifecycle.State.RESUMED)
            assertEqualsResumed(scenario.state)
            scenario.onActivity { activity ->
                assertFalse(activity.isFinishing)
                assertTrue(activity.window.decorView.isAttachedToWindow)
                assertTrue(activity.window.decorView.rootView.width > 0)
            }
        }
    }

    private fun assertEqualsResumed(state: Lifecycle.State) {
        assertTrue(state == Lifecycle.State.RESUMED)
    }
}
