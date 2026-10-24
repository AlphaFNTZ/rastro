package com.example.rastro.s2

import com.example.rastro.InvalidSensorReadException
import com.example.rastro.Posicao
import org.junit.Assert.*
import org.junit.Test

class CovarianciaTest {
    private fun posicao(raio: Double = 5.0, velocidade: Double = 0.3) = Posicao(-21.0, -44.0, 1000, raio, 1.0, velocidade)

    @Test fun `converte raio 68 por cento em variancia horizontal por eixo`() {
        val matriz = CovarianciaGnss.de(posicao()).matriz()
        assertEquals(10.9703562411888, matriz[0][0], 1e-8)
        assertEquals(matriz[0][0], matriz[1][1], 0.0)
        assertEquals(0.09, matriz[2][2], 1e-12)
        assertEquals(0.0, matriz[0][1], 0.0)
    }

    @Test fun `dobrar acuracia quadruplica variancia`() {
        val a = CovarianciaGnss.de(posicao()).matriz()
        val b = CovarianciaGnss.de(posicao(10.0, 0.6)).matriz()
        repeat(3) { assertEquals(a[it][it] * 4, b[it][it], 1e-10) }
    }

    @Test fun `matriz devolvida nao expoe estado interno`() {
        val cov = CovarianciaGnss.de(posicao())
        val matriz = cov.matriz()
        matriz[0][0] = -10.0
        matriz[1] = doubleArrayOf(-1.0)
        assertTrue(cov.matriz()[0][0] > 0)
        assertEquals(3, cov.matriz()[1].size)
    }

    @Test fun `nao converte qualidade IMU em sigma`() {
        val leitura = LeituraImu(1, 0.0, 0.0, 9.8, QualidadeImu.ALTA)
        assertTrue(leitura.incerteza is IncertezaImu.Indisponivel)
    }

    @Test fun `sigma realmente reportado gera matriz defensiva`() {
        val cov = IncertezaImu.Reportada("fonte quantitativa de teste", 0.1, 0.2, 0.3)
        val matriz = cov.matriz()
        assertEquals(0.04, matriz[1][1], 1e-12)
        matriz[1][1] = 200.0
        assertEquals(0.04, cov.matriz()[1][1], 1e-12)
    }

    @Test fun `rejeita incertezas invalidas e overflow`() {
        for (valor in listOf(-1.0, Double.NaN, Double.POSITIVE_INFINITY, Double.MAX_VALUE)) {
            assertThrows(InvalidSensorReadException::class.java) { IncertezaImu.Reportada("teste", valor, 1.0, 1.0) }
        }
        assertThrows(InvalidSensorReadException::class.java) { CovarianciaGnss.de(posicao(Double.MAX_VALUE)) }
        assertThrows(InvalidSensorReadException::class.java) { IncertezaImu.Reportada("", 1.0, 1.0, 1.0) }
    }
}
