package com.example.rastro

import androidx.test.core.app.ApplicationProvider
import androidx.test.core.app.ActivityScenario
import android.os.Handler
import android.os.Looper
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.rastro.mapa.MapasOffline
import org.maplibre.android.MapLibre
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.offline.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/** Integração real com o provedor: usa região mínima de teste, removida ao final. */
@RunWith(AndroidJUnit4::class)
class MapaOfflineTest {
    @Test fun regiaoCompletaReabreEstiloSemConexaoDoMapLibre() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val pronto = CountDownLatch(1)
        val carregado = CountDownLatch(1)
        val falha = AtomicReference<String?>()
        var regiao: OfflineRegion? = null
        var scenario: ActivityScenario<MainActivity>? = null
        val handler = Handler(Looper.getMainLooper())
        try {
            instrumentation.runOnMainSync {
                MapLibre.getInstance(context)
                MapLibre.setConnected(true)
                val manager = OfflineManager.getInstance(context)
                val def = OfflineTilePyramidRegionDefinition(MapasOffline.STYLE_URL,
                    LatLngBounds.from(-21.240, -44.990, -21.245, -44.995), 10.0, 12.0, 1f, false)
                manager.createOfflineRegion(def, "teste-integracao-rastro".toByteArray(), object : OfflineManager.CreateOfflineRegionCallback {
                    override fun onCreate(offlineRegion: OfflineRegion) {
                        regiao = offlineRegion
                        offlineRegion.setObserver(object : OfflineRegion.OfflineRegionObserver {
                            override fun onStatusChanged(status: OfflineRegionStatus) {
                                if (status.isComplete) { offlineRegion.setDownloadState(OfflineRegion.STATE_INACTIVE); pronto.countDown() }
                            }
                            override fun onError(error: OfflineRegionError) { falha.set(error.message); pronto.countDown() }
                            override fun mapboxTileCountLimitExceeded(limit: Long) { falha.set("Limite $limit"); pronto.countDown() }
                        })
                        offlineRegion.setDownloadState(OfflineRegion.STATE_ACTIVE)
                    }
                    override fun onError(error: String) { falha.set(error); pronto.countDown() }
                })
            }
            assertTrue("Download não concluiu em 90 s", pronto.await(90, TimeUnit.SECONDS))
            assertNull(falha.get())
            instrumentation.runOnMainSync {
                MapLibre.setConnected(false)
            }
            scenario = ActivityScenario.launch(MainActivity::class.java)
            scenario.onActivity { activity ->
                val host = activity.findViewById<android.widget.FrameLayout>(R.id.map_host)
                host.post { host.requestRectangleOnScreen(android.graphics.Rect(0, 0, host.width, host.height), true) }
                (host.getChildAt(0) as MapView).getMapAsync { m ->
                    m.moveCamera(org.maplibre.android.camera.CameraUpdateFactory.newLatLngZoom(org.maplibre.android.geometry.LatLng(-21.242, -44.992), 11.0))
                    val verificar = object : Runnable {
                        override fun run() {
                            val estilo = m.style
                            if (estilo != null && estilo.uri == MapasOffline.STYLE_URL && estilo.sources.isNotEmpty() && estilo.getLayer("sos-marcador") != null) carregado.countDown()
                            else handler.postDelayed(this, 200)
                        }
                    }
                    handler.post(verificar)
                }
            }
            assertTrue("Estilo não reabriu offline", carregado.await(30, TimeUnit.SECONDS))
            assertNull(falha.get())
        } finally {
            instrumentation.runOnMainSync { handler.removeCallbacksAndMessages(null) }
            scenario?.close()
            instrumentation.runOnMainSync {
                MapLibre.setConnected(null)
                regiao?.let {
                    it.setDownloadState(OfflineRegion.STATE_INACTIVE)
                    it.setObserver(null)
                    it.delete(object : OfflineRegion.OfflineRegionDeleteCallback {
                        override fun onDelete() = Unit
                        override fun onError(error: String) = Unit
                    })
                }
            }
        }
    }
}
