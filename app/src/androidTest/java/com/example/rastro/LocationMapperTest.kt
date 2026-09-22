package com.example.rastro

import android.location.Location
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.rastro.sensors.LocationMapper
import com.example.rastro.sensors.ResultadoGnss
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LocationMapperTest {
    private fun fix() = Location("gps").apply {
        latitude = -21.0; longitude = -44.0; time = 1000L
        accuracy = 5f; speed = 1f; speedAccuracyMetersPerSecond = 0.3f
    }
    @Test fun acuraciasAusentesNaoSaoSubstituidasPorZero() {
        val casos = listOf(fix().apply { removeAccuracy() }, fix().apply { removeSpeed() }, fix().apply { removeSpeedAccuracy() })
        casos.forEach { assertTrue(LocationMapper.converter(it) is ResultadoGnss.Indisponivel) }
    }
    @Test fun preservaAcuraciasReportadasPelaApi() {
        val resultado = LocationMapper.converter(fix()) as ResultadoGnss.Disponivel
        assertEquals(5.0, resultado.posicao.acuraciaHorizontalM, 0.0)
        assertEquals(0.3, resultado.posicao.acuraciaVelocidadeMps, 1e-6)
        assertEquals(0.09, resultado.covariancia.varianciaVelocidadeM2s2, 1e-6)
    }
    @Test fun leituraInvalidaFicaIndisponivel() {
        assertTrue(LocationMapper.converter(fix().apply { latitude = Double.NaN }) is ResultadoGnss.Indisponivel)
        assertTrue(LocationMapper.converter(fix().apply { time = 0L }) is ResultadoGnss.Indisponivel)
    }
}
