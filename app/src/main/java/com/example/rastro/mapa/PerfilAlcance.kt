package com.example.rastro.mapa

/** Cada amostra representa envio e ACK entre dois aparelhos com enlace direto. */
data class AmostraAlcance(val distanciaMetros: Double, val latenciaAckMs: Long?) {
    init {
        require(distanciaMetros.isFinite() && distanciaMetros > 0 && distanciaMetros <= 100_000)
        require(latenciaAckMs == null || latenciaAckMs >= 0)
    }
}

data class PerfilAlcance(
    val nome: String,
    val modeloA: String,
    val modeloB: String,
    val ambiente: String,
    val terreno: String,
    val obstaculos: String,
    val orientacao: String,
    val evidencia: String,
    val medidoEmEpochMs: Long,
    val timeoutAckMs: Long,
    val taxaMinima: Double,
    val repeticoesMinimas: Int,
    val amostras: List<AmostraAlcance>
) {
    init {
        require(listOf(nome, modeloA, modeloB, ambiente, terreno, obstaculos, orientacao, evidencia).all { it.isNotBlank() && it.length <= 500 })
        require(medidoEmEpochMs > 0 && timeoutAckMs in 1..60_000 && taxaMinima in 0.9..1.0 && repeticoesMinimas >= 10)
        require(amostras.size in 10..1000)
    }
    // Só aceita distâncias crescentes enquanto todos os grupos anteriores satisfazem o critério.
    val raioMetros: Double? get() = amostras.groupBy { it.distanciaMetros }.toSortedMap().entries
        .takeWhile { (_, grupo) -> grupo.size >= repeticoesMinimas &&
            grupo.count { it.latenciaAckMs != null && it.latenciaAckMs <= timeoutAckMs }.toDouble() / grupo.size >= taxaMinima }
        .lastOrNull()?.key
    val descricao: String get() = "$nome\n$modeloA ↔ $modeloB\n$ambiente; terreno: $terreno; obstáculos: $obstaculos; posição: $orientacao\n" +
        "Critério: ${(taxaMinima * 100).toInt()}% dos ACKs em até $timeoutAckMs ms; mínimo $repeticoesMinimas repetições por distância.\nEvidência: $evidencia"
}
