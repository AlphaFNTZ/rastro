package com.example.rastro.s2

import com.example.rastro.InvalidBufferException

/** Uma região crítica protege índices, slots e contadores. As leituras são
 * imutáveis; cada snapshot/lote tem sua própria coleção. Inserção O(1).
 * Cheio: descarta a mais antiga, contabilizando a perda. Um único consumidor
 * drena e distribui o mesmo lote para futuros persistidor e agente, fora do lock.
 */
class CircularImuBuffer(val capacidade: Int) {
    init { if (capacidade <= 0) throw InvalidBufferException("Capacidade deve ser positiva") }
    private val lock = Any()
    private val slots = arrayOfNulls<LeituraImu>(capacidade)
    private var inicio = 0
    private var quantidade = 0
    private var perdas = 0L

    val tamanho: Int get() = synchronized(lock) { quantidade }
    val descartadas: Long get() = synchronized(lock) { perdas }

    fun adicionar(leitura: LeituraImu) = synchronized(lock) {
        if (quantidade == capacidade) {
            slots[inicio] = leitura
            inicio = (inicio + 1) % capacidade
            perdas++
        } else {
            slots[(inicio + quantidade) % capacidade] = leitura
            quantidade++
        }
    }

    fun snapshot(): List<LeituraImu> = synchronized(lock) { copiar() }

    fun drenar(): List<LeituraImu> = synchronized(lock) {
        val lote = copiar()
        slots.fill(null)
        inicio = 0
        quantidade = 0
        lote
    }

    private fun copiar(): List<LeituraImu> = ArrayList<LeituraImu>(quantidade).apply {
        repeat(quantidade) { add(checkNotNull(slots[(inicio + it) % capacidade])) }
    }
}
