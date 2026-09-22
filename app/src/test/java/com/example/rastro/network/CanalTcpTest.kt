package com.example.rastro.network

import org.junit.Assert.*
import org.junit.Test
import java.net.ServerSocket
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

class CanalTcpTest {
    @Test fun `dois canais trocam SOS e confirmacao em loopback`() {
        val porta = ServerSocket(0).use { it.localPort }
        val prontos = CountDownLatch(2)
        val recebidasServidor = LinkedBlockingQueue<Mensagem>()
        val recebidasCliente = LinkedBlockingQueue<Mensagem>()
        val servidor = CanalTcp(true, "127.0.0.1", porta, { pronto, _ -> if (pronto) prontos.countDown() }, { recebidasServidor.add(it) })
        val cliente = CanalTcp(false, "127.0.0.1", porta, { pronto, _ -> if (pronto) prontos.countDown() }, { recebidasCliente.add(it) })
        try {
            assertFalse(cliente.enviar(Mensagem.sos(UUID.randomUUID().toString())))
            servidor.iniciar()
            cliente.iniciar()
            assertTrue(prontos.await(10, TimeUnit.SECONDS))
            val sos = Mensagem.sos(UUID.randomUUID().toString())
            assertTrue(cliente.enviar(sos))
            assertEquals(sos, recebidasServidor.poll(5, TimeUnit.SECONDS))
            val ack = sos.confirmar(UUID.randomUUID().toString())
            assertTrue(servidor.enviar(ack))
            assertEquals(ack, recebidasCliente.poll(5, TimeUnit.SECONDS))
        } finally {
            cliente.close()
            servidor.close()
        }
        assertFalse(cliente.enviar(Mensagem.sos(UUID.randomUUID().toString())))
    }
}
