package com.example.rastro.sos

import com.example.rastro.network.*
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class HistoricoSessaoTest {
    private val a = UUID.randomUUID().toString()
    private val b = UUID.randomUUID().toString()
    @Test fun `ultimo sos segue ordem de entrega e nao relogio remoto`() {
        val h = HistoricoSessao()
        val primeiro = Mensagem.sos(a, "Alfa", 999_000)
        val segundo = Mensagem.sos(b, "Bravo", 1000)
        h.receber(primeiro, 10_000, null)
        h.receber(segundo, 11_000, null)
        assertEquals(segundo, h.ultimoSos?.mensagem)
        assertFalse(h.receber(primeiro, 12_000, null))
        assertEquals(segundo, h.ultimoSos?.mensagem)
        assertEquals(2, h.eventos.size)
    }
    @Test fun `novo sos sem posicao remove referencia ao marcador anterior`() {
        val h = HistoricoSessao()
        h.receber(Mensagem.sos(a, "Alfa", 1000, LocalizacaoSos(0.0, 0.0, 5.0, 900), 100, EstadoLocalizacao.RECENTE), 1100, null)
        assertNotNull(h.ultimoSos?.mensagem?.localizacao)
        h.receber(Mensagem.sos(b, "Bravo", 1200), 1300, null)
        assertNull(h.ultimoSos?.mensagem?.localizacao)
    }
    @Test fun `historico limitado preserva nome de emissao e separa intermediario`() {
        val h = HistoricoSessao(2)
        val via = IdentidadeNo(b, "Intermediário")
        h.receber(Mensagem.sos(a, "Nome antigo", 1000), 1100, via)
        val registro = h.eventos.single()
        h.receber(Mensagem.sos(a, "Nome novo", 1200), 1300, via)
        assertEquals("Nome antigo", registro.aparelho.nome)
        assertEquals(via, registro.intermediario)
        assertEquals(a, registro.aparelho.id)
        h.receber(Mensagem.sos(a), 1400, via)
        assertEquals(2, h.eventos.size)
        assertEquals(a, h.eventos.last().aparelho.id)
    }
    @Test fun `posicao envelhece com relogio local e parada torna historica`() {
        val leitura = LeituraLocal(LocalizacaoSos(0.0, 0.0, null, 999_999_999), 1000)
        assertEquals(EstadoLocalizacao.RECENTE, leitura.estado(61_000, 60_000, true))
        assertEquals(EstadoLocalizacao.ANTIGA, leitura.estado(61_001, 60_000, true))
        assertEquals(EstadoLocalizacao.ANTIGA, leitura.estado(1001, 60_000, false))
        assertEquals(60_001L, leitura.idade(61_001))
    }
    @Test fun `ack e timeout nao usam horario absoluto de emissao`() {
        var monotono = 1000L
        val p = ConfirmacoesPendentes { monotono }
        val futuro = Mensagem.sos(a, "Alfa", 9_000_000_000)
        p.registrar(futuro.id)
        monotono = 1400
        assertEquals(400L, p.confirmar(futuro.id))
        assertNull(p.confirmar(futuro.id))
        assertFalse(p.expirar(futuro.id))
        p.registrar("outro")
        assertTrue(p.expirar("outro"))
        assertNull(p.confirmar("outro"))
    }
}
