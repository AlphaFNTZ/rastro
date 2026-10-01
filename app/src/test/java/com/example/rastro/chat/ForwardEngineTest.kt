package com.example.rastro.chat
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28],manifest=Config.NONE)
class ForwardEngineTest: ForwardContract() {
    override fun context(): android.content.Context=RuntimeEnvironment.getApplication()
}
