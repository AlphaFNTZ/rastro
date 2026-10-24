package com.example.rastro

import android.view.View
import android.widget.EditText
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.*
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NavegacaoPaginasTest {
    @Test fun barAndSwipesUseOneActivityAndPreserveSearch() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            lateinit var original: MainActivity
            scenario.onActivity { original=it }
            onView(withId(R.id.nav_item_devices)).perform(click())
            scenario.onActivity {
                assertSame(original,it)
                assertEquals(1,it.findViewById<PaginasPrincipais>(R.id.paginas).currentItem)
                it.findViewById<EditText>(R.id.et_buscar_dispositivos).setText("busca preservada")
            }
            onView(withId(R.id.paginas)).perform(swipeLeft())
            scenario.onActivity {
                assertEquals(2,it.findViewById<PaginasPrincipais>(R.id.paginas).currentItem)
                assertTrue(it.findViewById<View>(R.id.nav_item_history).isSelected)
            }
            onView(withId(R.id.paginas)).perform(swipeRight())
            scenario.onActivity {
                assertEquals(1,it.findViewById<PaginasPrincipais>(R.id.paginas).currentItem)
                assertEquals("busca preservada",it.findViewById<EditText>(R.id.et_buscar_dispositivos).text.toString())
            }
            onView(withId(R.id.nav_item_home)).perform(click())
            scenario.onActivity { assertEquals(0,it.findViewById<PaginasPrincipais>(R.id.paginas).currentItem) }
        }
    }
    @Test fun selectedPageRestoresAndBackReturnsToHome() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            onView(withId(R.id.nav_item_history)).perform(click())
            scenario.recreate()
            scenario.onActivity {
                assertEquals(2,it.findViewById<PaginasPrincipais>(R.id.paginas).currentItem)
                assertTrue(it.findViewById<View>(R.id.nav_item_history).isSelected)
                it.onBackPressedDispatcher.onBackPressed()
            }
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            scenario.onActivity { assertEquals(0,it.findViewById<PaginasPrincipais>(R.id.paginas).currentItem) }
        }
    }
}