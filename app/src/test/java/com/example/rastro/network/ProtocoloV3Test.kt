package com.example.rastro.network

import com.example.rastro.sos.*
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class ProtocoloV3Test {
    private val a = UUID.randomUUID().toString()
    private val b = UUID.randomUUID().toString()
    private fun completo() = Mensagem.sos(a, "Equipe Ç 🛰", 1_750_000_000_000,
        LocalizacaoSos(-21.2, -44.9, 12.5, 1_749_999_996_000), 4000, EstadoLocalizacao.RECENTE)

    @Test fun `v3 preserva unicode posicao precisao e dois horarios`() {
        val m = completo()
        assertEquals(m, Mensagem.decodificar(m.codificar().toByteArray()))
        assertTrue(m.codificar().toByteArray().size <= Mensagem.MAX_BYTES)
    }
    @Test fun `sem posicao nao fabrica coordenadas e zero zero e valido`() {
        val m = Mensagem.sos(a, "Base", 1000)
        assertNull(Mensagem.decodificar(m.codificar()).localizacao)
        val zero = completo().copy(localizacao = LocalizacaoSos(0.0, 0.0, null, 1000))
        assertEquals(zero, Mensagem.decodificar(zero.codificar()))
    }
    @Test fun `posicao antiga conserva idade na emissao`() {
        val antiga = completo().copy(estadoLocalizacao = EstadoLocalizacao.ANTIGA, idadeLocalizacaoNaEmissaoMs = 300_000)
        assertEquals(antiga, Mensagem.decodificar(antiga.codificar()))
    }
    @Test fun `v1 e v2 sao lidos sem inventar nome hora ou posicao`() {
        for (texto in listOf("1|$a|$b|SOS_MANUAL|4||", "2|$a|$b|SOS_MANUAL|4|||0|")) {
            val m = Mensagem.decodificar(texto)
            assertNull(m.nomeOrigem); assertNull(m.emitidoEmEpochMs); assertNull(m.localizacao)
            assertEquals(m, Mensagem.decodificar(m.codificar()))
        }
    }
    @Test fun `rejeita coordenada parcial nao finita e fora de faixa`() {
        for ((campo, valor) in listOf(10 to "", 10 to "NaN", 11 to "Infinity", 10 to "91", 11 to "181", 12 to "-1", 13 to "0", 14 to "-1", 15 to "INDISPONIVEL")) {
            val c = completo().codificar().split('|').toMutableList(); c[campo] = valor
            assertThrows(IllegalArgumentException::class.java) { Mensagem.decodificar(c.joinToString("|")) }
        }
    }
    @Test fun `limita bytes e rejeita utf8 malformado nomes longos e campos indevidos`() {
        assertThrows(IllegalArgumentException::class.java) { Mensagem.decodificar(ByteArray(1025)) }
        assertThrows(IllegalArgumentException::class.java) { Mensagem.decodificar(byteArrayOf(0xc3.toByte(), 0x28)) }
        assertThrows(IllegalArgumentException::class.java) { completo().copy(nomeOrigem = "😀".repeat(9)) }
        assertThrows(IllegalArgumentException::class.java) { completo().copy(tipo = TipoMensagem.PRESENCA) }
        assertThrows(IllegalArgumentException::class.java) { completo().copy(limiteSaltos = 16, saltosPercorridos = 1) }
    }
    @Test fun `intermediarios preservam sos original e identidade do ack`() {
        val original = completo()
        val enviados = mutableListOf<Mensagem>()
        val router = MeshRouter(b, { m, _ -> enviados.add(m); 1 }, { _, _ -> }, nomeLocal = { "Bravo" })
        router.receber("a", original)
        router.receber("c", original)
        assertEquals(2, enviados.size)
        assertEquals("Bravo", enviados.first().nomeOrigem)
        assertEquals(original.copy(limiteSaltos = 3, saltosPercorridos = 1), enviados.last())
        assertEquals(original, enviados.last().copy(limiteSaltos = 4, saltosPercorridos = 0))
    }
}
