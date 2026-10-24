package com.example.rastro.s2

import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/** Dez casos independentes aparecem nominalmente no relatório JUnit. */
@RunWith(Parameterized::class)
class CircularImuBufferConcurrencyTest(private val rodada: Int) {
    companion object {
        @JvmStatic @Parameterized.Parameters(name = "rodada {0}/10")
        fun rodadas(): Collection<Array<Int>> = (1..10).map { arrayOf(it) }
    }

    @Test fun `produtor consumidor e observador nao corrompem a regiao critica`() {
        val buffer = CircularImuBuffer(64)
        val executor = Executors.newFixedThreadPool(3)
        val inicio = CountDownLatch(1)
        val primeiroLote = CountDownLatch(1)
        val produtorTerminou = AtomicBoolean(false)
        val total = 10_000L
        try {
            val produtor = executor.submit {
                inicio.await()
                try {
                    for (id in 1L..total) {
                        buffer.adicionar(leitura(id))
                        if (id == 32L) assertTrue(primeiroLote.await(5, TimeUnit.SECONDS))
                        if (id % 32 == 0L) Thread.yield()
                    }
                } finally { produtorTerminou.set(true) }
            }
            val consumidor = executor.submit<List<Long>> {
                inicio.await()
                val recebidos = ArrayList<Long>()
                do {
                    val lote = buffer.drenar()
                    recebidos.addAll(lote.map { it.timestampMonotonicoNs })
                    if (lote.isNotEmpty()) primeiroLote.countDown()
                    (lote as MutableList<LeituraImu>).clear()
                    Thread.yield()
                } while (!produtorTerminou.get() || buffer.tamanho > 0)
                recebidos
            }
            val observador = executor.submit {
                inicio.await()
                repeat(1000) {
                    val snapshot = buffer.snapshot()
                    assertTrue(snapshot.size <= 64)
                    assertTrue(snapshot.zipWithNext().all { (a, b) -> a.timestampMonotonicoNs < b.timestampMonotonicoNs })
                    assertTrue(snapshot.all { it.acelX == it.timestampMonotonicoNs.toDouble() })
                    (snapshot as MutableList<LeituraImu>).clear()
                }
            }
            inicio.countDown()
            produtor.get(10, TimeUnit.SECONDS)
            val ids = consumidor.get(10, TimeUnit.SECONDS)
            observador.get(10, TimeUnit.SECONDS)
            assertTrue("rodada $rodada", ids.zipWithNext().all { (a, b) -> a < b })
            assertEquals(ids.size, ids.toSet().size)
            assertEquals(total, ids.size.toLong() + buffer.descartadas)
            assertEquals(0, buffer.tamanho)
        } finally {
            executor.shutdownNow()
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS))
        }
    }
}
