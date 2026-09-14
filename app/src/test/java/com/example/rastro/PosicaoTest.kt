package com.example.rastro

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class PosicaoTest {

    private fun posicaoValida(
        latitude: Double = -21.2246,
        longitude: Double = -44.9855,
        timestampMs: Long = 1_726_000_000_000L,
        acuraciaHorizontalM: Double = 5.0,
        velocidadeMps: Double = 1.4,
        acuraciaVelocidadeMps: Double = 0.3
    ) = Posicao(latitude, longitude, timestampMs, acuraciaHorizontalM, velocidadeMps, acuraciaVelocidadeMps)

    @Test
    fun `constroi normalmente com valores fisicamente validos`() {
        val posicao = posicaoValida()
        assertEquals(-21.2246, posicao.latitude, 0.0)
        assertEquals(-44.9855, posicao.longitude, 0.0)
    }

    @Test
    fun `aceita latitude e longitude exatamente nos limites`() {
        posicaoValida(latitude = -90.0, longitude = -180.0)
        posicaoValida(latitude = 90.0, longitude = 180.0)
        // não lançar já é o teste
    }

    @Test
    fun `rejeita latitude abaixo de -90`() {
        val ex = assertThrows(InvalidSensorReadException::class.java) {
            posicaoValida(latitude = -90.0001)
        }
        assertTrue(ex.message!!.contains("latitude"))
    }

    @Test
    fun `rejeita latitude acima de 90`() {
        assertThrows(InvalidSensorReadException::class.java) {
            posicaoValida(latitude = 90.0001)
        }
    }

    @Test
    fun `rejeita longitude abaixo de -180`() {
        assertThrows(InvalidSensorReadException::class.java) {
            posicaoValida(longitude = -180.0001)
        }
    }

    @Test
    fun `rejeita longitude acima de 180`() {
        assertThrows(InvalidSensorReadException::class.java) {
            posicaoValida(longitude = 180.0001)
        }
    }

    @Test
    fun `rejeita latitude NaN mesmo passando pelas comparacoes numericas`() {
        // Double.NaN comparado com < ou > sempre devolve false: sem checagem
        // explicita de isFinite(), este caso escaparia silenciosamente da validacao.
        assertThrows(InvalidSensorReadException::class.java) {
            posicaoValida(latitude = Double.NaN)
        }
    }

    @Test
    fun `rejeita acuracia horizontal infinita`() {
        assertThrows(InvalidSensorReadException::class.java) {
            posicaoValida(acuraciaHorizontalM = Double.POSITIVE_INFINITY)
        }
    }

    @Test
    fun `rejeita acuracia horizontal negativa`() {
        assertThrows(InvalidSensorReadException::class.java) {
            posicaoValida(acuraciaHorizontalM = -0.1)
        }
    }

    @Test
    fun `rejeita velocidade negativa`() {
        assertThrows(InvalidSensorReadException::class.java) {
            posicaoValida(velocidadeMps = -0.1)
        }
    }

    @Test
    fun `rejeita acuracia de velocidade negativa`() {
        assertThrows(InvalidSensorReadException::class.java) {
            posicaoValida(acuraciaVelocidadeMps = -0.1)
        }
    }

    @Test
    fun `rejeita timestamp zero ou negativo`() {
        assertThrows(InvalidSensorReadException::class.java) { posicaoValida(timestampMs = 0L) }
        assertThrows(InvalidSensorReadException::class.java) { posicaoValida(timestampMs = -1L) }
    }

    @Test
    fun `duas posicoes com os mesmos valores sao iguais (semantica de valor)`() {
        assertEquals(posicaoValida(), posicaoValida())
    }
}
