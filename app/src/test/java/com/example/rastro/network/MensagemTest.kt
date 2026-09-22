package com.example.rastro.network

import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class MensagemTest {
    private fun no() = UUID.randomUUID().toString()
    @Test fun `SOS e confirmacao preservam identidade na codificacao`() {
        val sos = Mensagem.sos(no())
        val ack = sos.confirmar(no())
        assertEquals(sos, Mensagem.decodificar(sos.codificar()))
        assertEquals(ack, Mensagem.decodificar(ack.codificar()))
        assertEquals(sos.id, ack.referencia)
        assertEquals(sos.origem, ack.destino)
    }
    @Test fun `rejeita versoes campos e comprimentos invalidos`() {
        val sos = Mensagem.sos(no())
        for (texto in listOf("", "x".repeat(513), sos.codificar().replaceFirst("1|", "2|"), "1|invalido|x|SOS_MANUAL|4||")) {
            assertThrows(IllegalArgumentException::class.java) { Mensagem.decodificar(texto) }
        }
        assertThrows(IllegalArgumentException::class.java) { sos.copy(limiteSaltos = -1) }
        assertThrows(IllegalArgumentException::class.java) { sos.copy(tipo = TipoMensagem.CONFIRMACAO) }
    }
    @Test fun `cache suprime duplicatas e tem capacidade limitada`() {
        val vistas = MensagensVistas(2)
        assertTrue(vistas.primeiraVez("a"))
        assertFalse(vistas.primeiraVez("a"))
        assertTrue(vistas.primeiraVez("b"))
        assertTrue(vistas.primeiraVez("c"))
        assertTrue(vistas.primeiraVez("a"))
    }
}
