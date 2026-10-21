package com.example.rastro.av1

/**
 * Corta um registro em janelas de reconciliação.
 *
 * Usa só fixos do provedor "gps" quando houver algum (a localização por rede não é
 * medida GNSS e tem acurácia de outra natureza), só fixos [RegistroFix.reconciliavel],
 * e quebra a sequência quando o intervalo entre fixos passa de [maxIntervaloS] — a
 * integração trapezoidal da restrição cinemática só vale para Δt curto.
 */
object Janelas {

    fun filtrar(fixos: List<RegistroFix>): List<RegistroFix> {
        val ordenados = fixos.sortedBy { it.tempoMonotonicoNs }
        val gps = ordenados.filter { it.provedor == "gps" }
        val base = if (gps.isNotEmpty()) gps else ordenados
        return base.filter { it.reconciliavel }
    }

    /** Trechos contínuos (sem lacuna maior que [maxIntervaloS]). */
    fun trechos(fixos: List<RegistroFix>, maxIntervaloS: Double = 3.0): List<List<RegistroFix>> {
        val f = filtrar(fixos)
        val saida = ArrayList<List<RegistroFix>>()
        var atual = ArrayList<RegistroFix>()
        for (x in f) {
            val ultimo = atual.lastOrNull()
            if (ultimo != null) {
                val dt = (x.tempoMonotonicoNs - ultimo.tempoMonotonicoNs) / 1e9
                if (dt <= 0.0) continue
                if (dt > maxIntervaloS) { if (atual.size >= 2) saida.add(atual); atual = ArrayList() }
            }
            atual.add(x)
        }
        if (atual.size >= 2) saida.add(atual)
        return saida
    }

    /** Janelas disjuntas de exatamente [tamanho] fixos, na ordem do registro. */
    fun disjuntas(fixos: List<RegistroFix>, tamanho: Int = 10, maxIntervaloS: Double = 3.0): List<List<RegistroFix>> {
        require(tamanho >= 3)
        return trechos(fixos, maxIntervaloS).flatMap { trecho -> trecho.chunked(tamanho).filter { it.size == tamanho } }
    }
}
