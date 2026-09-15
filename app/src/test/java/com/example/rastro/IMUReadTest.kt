package com.example.rastro

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class IMUReadTest {

    private fun imuValida(
        timestampMs: Long = 1_726_000_000_000L,
        acelXMps2: Double = 0.2,
        acelYMps2: Double = 0.1,
        acelZMps2: Double = 9.8,
        inclinacaoGraus: Double = 3.0
    ) = IMURead(timestampMs, acelXMps2, acelYMps2, acelZMps2, inclinacaoGraus)

    @Test
    fun `constroi normalmente em repouso proximo da vertical`() {
        val imu = imuValida()
        assertEquals(9.8, imu.acelZMps2, 0.0)
    }

    @Test
    fun `aceita inclinacao exatamente nos limites 0 e 180`() {
        imuValida(inclinacaoGraus = 0.0)
        imuValida(inclinacaoGraus = 180.0)
    }

    @Test
    fun `rejeita timestamp zero ou negativo`() {
        assertThrows(InvalidSensorReadException::class.java) { imuValida(timestampMs = 0L) }
        assertThrows(InvalidSensorReadException::class.java) { imuValida(timestampMs = -5L) }
    }

    @Test
    fun `rejeita aceleracao NaN`() {
        assertThrows(InvalidSensorReadException::class.java) { imuValida(acelXMps2 = Double.NaN) }
    }

    @Test
    fun `rejeita aceleracao infinita`() {
        assertThrows(InvalidSensorReadException::class.java) {
            imuValida(acelYMps2 = Double.NEGATIVE_INFINITY)
        }
    }

    @Test
    fun `rejeita aceleracao acima da magnitude fisicamente plausivel`() {
        assertThrows(InvalidSensorReadException::class.java) {
            imuValida(acelZMps2 = IMURead.ACELERACAO_MAX_MPS2 + 0.1)
        }
        assertThrows(InvalidSensorReadException::class.java) {
            imuValida(acelXMps2 = -(IMURead.ACELERACAO_MAX_MPS2 + 0.1))
        }
    }

    @Test
    fun `aceita aceleracao exatamente no limite de magnitude`() {
        imuValida(acelZMps2 = IMURead.ACELERACAO_MAX_MPS2)
    }

    @Test
    fun `rejeita inclinacao fora do intervalo 0 a 180`() {
        assertThrows(InvalidSensorReadException::class.java) { imuValida(inclinacaoGraus = -0.1) }
        assertThrows(InvalidSensorReadException::class.java) { imuValida(inclinacaoGraus = 180.1) }
    }

    @Test
    fun `magnitude calcula o modulo do vetor de aceleracao`() {
        val imu = imuValida(acelXMps2 = 3.0, acelYMps2 = 4.0, acelZMps2 = 0.0)
        assertEquals(5.0, imu.magnitude(), 1e-9)
    }
}
