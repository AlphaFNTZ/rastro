package com.example.rastro
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.rastro.chat.ForwardContract
import org.junit.runner.RunWith
@RunWith(AndroidJUnit4::class)
class ForwardDeviceTest: ForwardContract() {
    override fun context(): android.content.Context=InstrumentationRegistry.getInstrumentation().targetContext
}
