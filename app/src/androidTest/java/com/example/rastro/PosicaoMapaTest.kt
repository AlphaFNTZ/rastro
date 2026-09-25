package com.example.rastro

import android.location.Location
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.rastro.sensors.PosicaoMapper
import com.example.rastro.sensors.LocationMapper
import com.example.rastro.sensors.ResultadoGnss
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PosicaoMapaTest {
    @Test fun posicaoSemVelocidadeServeAoSosMasNaoAlteraContratoGnss() {
        val fix = Location("gps").apply { latitude = -21.0; longitude = -44.0; time = 1000; elapsedRealtimeNanos = 1_000_000; accuracy = 15f }
        assertNotNull(PosicaoMapper.converter(fix))
        assertTrue(LocationMapper.converter(fix) is ResultadoGnss.Indisponivel)
        fix.removeAccuracy()
        assertNull(PosicaoMapper.converter(fix)?.posicao?.precisaoMetros)
        fix.latitude = Double.NaN
        assertNull(PosicaoMapper.converter(fix))
    }
}
