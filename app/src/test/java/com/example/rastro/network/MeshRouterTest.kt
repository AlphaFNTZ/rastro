package com.example.rastro.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class MeshRouterTest {
    private val local = UUID.randomUUID().toString()
    private val remoto = UUID.randomUUID().toString()

    @Test fun `SOS e entregue uma vez recebe ACK e segue com TTL reduzido`() {
        val envios = mutableListOf<Pair<Mensagem, String?>>()
        val entregues = mutableListOf<Mensagem>()
        val router = MeshRouter(local, { mensagem, exceto ->
            envios += mensagem to exceto
            1
        }, { _, mensagem -> entregues += mensagem })
        val sos = Mensagem.sos(remoto).copy(limiteSaltos = 3)

        router.receber("endpoint-a", sos)
        router.receber("endpoint-a", sos)

        assertEquals(listOf(sos), entregues)
        assertEquals(2, envios.size)
        val ack = envios.first().first
        assertEquals(TipoMensagem.CONFIRMACAO, ack.tipo)
        assertEquals(sos.id, ack.referencia)
        assertEquals(remoto, ack.destino)
        assertEquals(null, envios.first().second)
        assertEquals(2, envios.last().first.limiteSaltos)
        assertEquals("endpoint-a", envios.last().second)
    }

    @Test fun `ACK destinado ao no local e entregue sem retransmissao`() {
        val envios = mutableListOf<Mensagem>()
        val entregues = mutableListOf<Mensagem>()
        val router = MeshRouter(local, { mensagem, _ -> envios += mensagem; 1 }, { _, mensagem -> entregues += mensagem })
        val sos = Mensagem.sos(local)
        val ack = sos.confirmar(remoto)

        router.receber("endpoint-b", ack)

        assertEquals(listOf(ack), entregues)
        assertTrue(envios.isEmpty())
    }

    @Test fun `ACK para outro no e propagado e TTL zero nao avanca`() {
        val envios = mutableListOf<Mensagem>()
        val router = MeshRouter(local, { mensagem, _ -> envios += mensagem; 1 }, { _, _ -> })
        val destino = UUID.randomUUID().toString()
        val ack = Mensagem.sos(destino).confirmar(remoto).copy(limiteSaltos = 1)

        router.receber("endpoint-b", ack)
        router.receber("endpoint-c", ack.copy(id = UUID.randomUUID().toString(), limiteSaltos = 0))

        assertEquals(1, envios.size)
        assertEquals(0, envios.single().limiteSaltos)
    }

    @Test fun `origem sem vizinhos informa falha e rejeita origem falsa`() {
        val router = MeshRouter(local, { _, _ -> 0 }, { _, _ -> })
        assertFalse(router.originar(Mensagem.sos(local)))
        org.junit.Assert.assertThrows(IllegalArgumentException::class.java) {
            router.originar(Mensagem.sos(remoto))
        }
    }

    @Test fun `presenca registra enlace de entrada e incrementa saltos ao propagar`() {
        val envios = mutableListOf<Pair<Mensagem, String?>>()
        val entregues = mutableListOf<Pair<String, Mensagem>>()
        val router = MeshRouter(local, { mensagem, exceto -> envios += mensagem to exceto; 1 },
            { endpoint, mensagem -> entregues += endpoint to mensagem })
        val presenca = Mensagem.presenca(remoto, "Equipe Alfa").copy(limiteSaltos = 3)

        router.receber("endpoint-b", presenca)

        assertEquals("endpoint-b", entregues.single().first)
        assertEquals(presenca, entregues.single().second)
        assertEquals(1, envios.single().first.saltosPercorridos)
        assertEquals(2, envios.single().first.limiteSaltos)
        assertEquals("endpoint-b", envios.single().second)
    }
}
