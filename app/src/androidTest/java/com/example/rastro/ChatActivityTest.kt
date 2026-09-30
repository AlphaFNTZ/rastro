package com.example.rastro

import android.graphics.Bitmap
import android.os.SystemClock
import android.view.View
import android.view.WindowManager
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.rastro.chat.ChatContact
import com.example.rastro.chat.ChatCrypto
import com.example.rastro.chat.ChatVault
import com.google.zxing.*
import com.google.zxing.common.HybridBinarizer
import com.journeyapps.barcodescanner.BarcodeEncoder
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Does not insert contacts or messages in the user's database. */
@RunWith(AndroidJUnit4::class)
class ChatActivityTest {
    @Test fun navigationQrDialogAndActivityRecreation() {
        ActivityScenario.launch(DispositivosActivity::class.java).use {
            onView(withId(R.id.btn_chat)).perform(click())
            onView(withId(R.id.chat_title)).check(matches(withText(R.string.chat_title)))
            onView(withId(R.id.chat_my_qr)).perform(click())
            waitForDialog()
            onView(withText("Fechar")).perform(click())
            onView(withId(R.id.chat_back)).perform(click())
            onView(withId(R.id.btn_chat)).check(matches(isDisplayed()))
        }
        ActivityScenario.launch(ChatActivity::class.java).use { scenario ->
            scenario.recreate()
            scenario.onActivity { activity ->
                assertTrue(activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0)
                assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.chat_scan).visibility)
                assertEquals(View.GONE, activity.findViewById<View>(R.id.chat_composer).visibility)
            }
            onView(withId(R.id.chat_my_qr)).perform(click())
            waitForDialog()
            onView(withText("Fechar")).perform(click())
        }
    }
    @Test fun generatedPublicQrCanBeDecodedWithoutPrivateKeys() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val crypto = ChatVault(context).identity()
        val contact = crypto.contact("Teste QR 🛰")
        val bitmap: Bitmap = BarcodeEncoder().encodeBitmap(contact.qr(), BarcodeFormat.QR_CODE,720,720)
        try {
            val pixels=IntArray(bitmap.width*bitmap.height)
            bitmap.getPixels(pixels,0,bitmap.width,0,0,bitmap.width,bitmap.height)
            val decoded=MultiFormatReader().decode(BinaryBitmap(HybridBinarizer(RGBLuminanceSource(bitmap.width,bitmap.height,pixels))))
            val parsed=ChatContact.parse(decoded.text)
            ChatCrypto.validate(parsed)
            assertEquals(contact.id,parsed.id)
            assertEquals(contact.name,parsed.name)
        } finally { bitmap.recycle() }
    }
    private fun waitForDialog() {
        val limit=SystemClock.elapsedRealtime()+15000
        var failure: Throwable? = null
        while(SystemClock.elapsedRealtime()<limit) {
            try { onView(withText("Fechar")).check(matches(isDisplayed())); return }
            catch(t: AssertionError) { failure=t }
            catch(t: androidx.test.espresso.NoMatchingViewException) { failure=t }
            SystemClock.sleep(100)
        }
        throw AssertionError("QR Code não foi exibido no prazo",failure)
    }
}