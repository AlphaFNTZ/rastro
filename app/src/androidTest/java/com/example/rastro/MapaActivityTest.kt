package com.example.rastro

import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MapaActivityTest {
    @Test fun mapaESosContinuamPresentesDepoisDeRecriarTelaSemPermissaoGps() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity {
                assertNotNull(it.findViewById<android.view.View>(R.id.map_host))
                assertNotNull(it.findViewById<android.view.View>(R.id.btn_enviar_sos))
                assertEquals(android.view.View.VISIBLE, it.findViewById<android.view.View>(R.id.btn_ativar_rastro).visibility)
            }
            scenario.recreate()
            scenario.onActivity {
                assertNotNull(it.findViewById<android.view.View>(R.id.map_host))
                assertTrue(it.findViewById<android.view.View>(R.id.nav_item_home).isSelected)
                assertEquals(android.view.View.GONE, it.findViewById<android.view.View>(R.id.layout_acoes_ativado).visibility)
            }
        }
    }
}
