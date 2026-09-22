package com.example.rastro.s2

import com.example.rastro.InvalidBufferException

import org.junit.Assert.*
import org.junit.Test

internal fun leitura(id: Long) = LeituraImu(id, id.toDouble(), 0.0, 9.8, QualidadeImu.ALTA)

class CircularImuBufferTest {
    @Test fun `capacidade precisa ser positiva`() {
        assertThrows(InvalidBufferException::class.java) { CircularImuBuffer(0) }
        assertThrows(InvalidBufferException::class.java) { CircularImuBuffer(-1) }
    }

    @Test fun `retorno circular preserva ordem e conta descartes`() {
        val buffer = CircularImuBuffer(3)
        (1L..7L).forEach { buffer.adicionar(leitura(it)) }
        assertEquals(listOf(5L, 6L, 7L), buffer.snapshot().map { it.timestampMonotonicoNs })
        assertEquals(4L, buffer.descartadas)
        assertEquals(3, buffer.tamanho)
    }

    @Test fun `snapshot e lote drenado sao copias defensivas diretas`() {
        val buffer = CircularImuBuffer(3)
        buffer.adicionar(leitura(1))
        val snapshot = buffer.snapshot() as MutableList<LeituraImu>
        snapshot.clear() // Sem toMutableList: modifica a coleção que a API entregou.
        assertEquals(1, buffer.tamanho)
        val lote = buffer.drenar() as MutableList<LeituraImu>
        assertEquals(0, buffer.tamanho)
        buffer.adicionar(leitura(2))
        assertEquals(listOf(1L), lote.map { it.timestampMonotonicoNs })
        lote.add(leitura(3))
        assertEquals(listOf(2L), buffer.snapshot().map { it.timestampMonotonicoNs })
    }

    @Test fun `snapshot permanece estavel apos sobrescrita e buffer pode ser reutilizado`() {
        val buffer = CircularImuBuffer(1)
        assertTrue(buffer.drenar().isEmpty())
        buffer.adicionar(leitura(1))
        val antes = buffer.snapshot()
        buffer.adicionar(leitura(2))
        assertEquals(1L, antes.single().timestampMonotonicoNs)
        assertEquals(2L, buffer.drenar().single().timestampMonotonicoNs)
        buffer.adicionar(leitura(3))
        assertEquals(3L, buffer.drenar().single().timestampMonotonicoNs)
    }
}
