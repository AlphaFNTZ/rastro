package com.example.rastro

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class TrajetoTest {

    private fun posicao(timestampMs: Long, velocidadeMps: Double = 1.0) = Posicao(
        latitude = -21.2246,
        longitude = -44.9855,
        timestampMs = timestampMs,
        acuraciaHorizontalM = 5.0,
        velocidadeMps = velocidadeMps,
        acuraciaVelocidadeMps = 0.3
    )

    @Test
    fun `rejeita capacidade menor que 1`() {
        assertThrows(InvalidTrajetoException::class.java) { Trajeto.vazio(0) }
        assertThrows(InvalidTrajetoException::class.java) { Trajeto.vazio(-3) }
    }

    @Test
    fun `trajeto vazio tem tamanho zero`() {
        val trajeto = Trajeto.vazio(capacidade = 10)
        assertEquals(0, trajeto.tamanho)
        assertTrue(trajeto.estaVazio())
    }

    @Test
    fun `comNovoPonto nao muta a instancia original`() {
        val original = Trajeto.vazio(capacidade = 10)
        val atualizado = original.comNovoPonto(posicao(timestampMs = 1_000L))

        assertEquals(0, original.tamanho) // original intocado
        assertEquals(1, atualizado.tamanho) // nova instância com o ponto
    }

    @Test
    fun `capacidade fixa descarta o ponto mais antigo (memoria constante)`() {
        var trajeto = Trajeto.vazio(capacidade = 3)
        for (i in 1..5) {
            trajeto = trajeto.comNovoPonto(posicao(timestampMs = i * 1_000L))
        }
        assertEquals(3, trajeto.tamanho) // nunca excede a capacidade

        val timestamps = trajeto.pontosOrdenados().map { it.timestampMs }
        assertEquals(listOf(3_000L, 4_000L, 5_000L), timestamps) // os 2 mais antigos foram descartados
    }

    @Test
    fun `rejeita ponto fora de ordem cronologica`() {
        val trajeto = Trajeto.vazio(capacidade = 5).comNovoPonto(posicao(timestampMs = 2_000L))
        assertThrows(InvalidTrajetoException::class.java) {
            trajeto.comNovoPonto(posicao(timestampMs = 1_000L))
        }
    }

    @Test
    fun `recorte temporal e inclusivo nas duas pontas e roda em tempo linear`() {
        var trajeto = Trajeto.vazio(capacidade = 100)
        for (i in 1..10) {
            trajeto = trajeto.comNovoPonto(posicao(timestampMs = i * 1_000L))
        }
        val recorte = trajeto.recorteTemporal(3_000L, 6_000L)
        assertEquals(listOf(3_000L, 4_000L, 5_000L, 6_000L), recorte.map { it.timestampMs })
    }

    @Test
    fun `recorte temporal em trajeto vazio devolve lista vazia`() {
        val trajeto = Trajeto.vazio(capacidade = 5)
        assertTrue(trajeto.recorteTemporal(0L, 10_000L).isEmpty())
    }

    @Test
    fun `recorte temporal com intervalo invertido lanca excecao`() {
        val trajeto = Trajeto.vazio(capacidade = 5).comNovoPonto(posicao(timestampMs = 1_000L))
        assertThrows(InvalidTrajetoException::class.java) {
            trajeto.recorteTemporal(desdeMs = 5_000L, ateMs = 1_000L)
        }
    }

    @Test
    fun `velocidade media calcula a media das velocidades reportadas pelo sensor`() {
        var trajeto = Trajeto.vazio(capacidade = 10)
        trajeto = trajeto.comNovoPonto(posicao(timestampMs = 1_000L, velocidadeMps = 1.0))
        trajeto = trajeto.comNovoPonto(posicao(timestampMs = 2_000L, velocidadeMps = 2.0))
        trajeto = trajeto.comNovoPonto(posicao(timestampMs = 3_000L, velocidadeMps = 3.0))
        assertEquals(2.0, trajeto.velocidadeMediaMps(), 1e-9)
    }

    @Test
    fun `velocidade media de trajeto vazio e zero`() {
        assertEquals(0.0, Trajeto.vazio(capacidade = 5).velocidadeMediaMps(), 0.0)
    }

    @Test
    fun `pontosOrdenados devolve copia defensiva - mutar a lista devolvida nao afeta o trajeto`() {
        val trajeto = Trajeto.vazio(capacidade = 5)
            .comNovoPonto(posicao(timestampMs = 1_000L))
            .comNovoPonto(posicao(timestampMs = 2_000L))
        val copia = trajeto.pontosOrdenados() as MutableList<Posicao>
        copia.clear()

        assertEquals(2, trajeto.tamanho)
        assertEquals(listOf(1_000L, 2_000L), trajeto.pontosOrdenados().map { it.timestampMs })
    }

    /**
     * Usa exclusivamente primitivas de java.util.concurrent (ExecutorService,
     * CountDownLatch) — sem corrotinas — por exigência da disciplina para a
     * futura análise de escalonabilidade Rate Monotonic (Semana 6). O ponto do
     * teste: como Trajeto é imutável, N threads podem chamar comNovoPonto sobre
     * a MESMA instância compartilhada ao mesmo tempo sem qualquer sincronização
     * externa, porque nenhuma delas jamais escreve no estado da instância original.
     */
    @Test
    fun `trajeto original permanece inalteravel sob escrita concorrente`() {
        val original = Trajeto.vazio(capacidade = 100)
        val numeroDeThreads = 20
        val executor: ExecutorService = Executors.newFixedThreadPool(numeroDeThreads)
        val latch = CountDownLatch(numeroDeThreads)
        val resultados = Collections.synchronizedList(ArrayList<Trajeto>())

        try {
            for (i in 0 until numeroDeThreads) {
                executor.submit {
                    try {
                        resultados.add(original.comNovoPonto(posicao(timestampMs = 1_000L + i)))
                    } finally {
                        latch.countDown()
                    }
                }
            }
            val terminouATempo = latch.await(5, TimeUnit.SECONDS)
            assertTrue("as threads não terminaram dentro do tempo limite", terminouATempo)
        } finally {
            executor.shutdown()
        }

        assertEquals(0, original.tamanho) // a instância compartilhada nunca foi mutada
        assertEquals(numeroDeThreads, resultados.size)
        resultados.forEach { assertEquals(1, it.tamanho) }
    }
}
