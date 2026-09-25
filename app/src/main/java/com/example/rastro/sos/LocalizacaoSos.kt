package com.example.rastro.sos

/** Coordenadas independentes dos contratos de velocidade/covariância do GNSS. */
data class LocalizacaoSos(
    val latitude: Double,
    val longitude: Double,
    val precisaoMetros: Double?,
    val lidaEmEpochMs: Long
) {
    init {
        require(latitude.isFinite() && latitude in -90.0..90.0)
        require(longitude.isFinite() && longitude in -180.0..180.0)
        require(precisaoMetros == null || (precisaoMetros.isFinite() && precisaoMetros >= 0))
        require(lidaEmEpochMs > 0)
    }
}

enum class EstadoLocalizacao { RECENTE, ANTIGA, INDISPONIVEL }

data class LeituraLocal(val posicao: LocalizacaoSos, val tempoMonotonicoMs: Long) {
    init { require(tempoMonotonicoMs >= 0) }
    fun idade(agoraMonotonicoMs: Long): Long = (agoraMonotonicoMs - tempoMonotonicoMs).coerceAtLeast(0)
    fun estado(agoraMonotonicoMs: Long, limiteMs: Long, aquisicaoAtiva: Boolean): EstadoLocalizacao =
        if (aquisicaoAtiva && idade(agoraMonotonicoMs) <= limiteMs) EstadoLocalizacao.RECENTE else EstadoLocalizacao.ANTIGA
}

object ConfiguracaoLocalizacao {
    const val LIMITE_RECENTE_PADRAO_MS = 60_000L
    const val INTERVALO_MS = 5_000L
    fun limiteValido(ms: Long) = ms in 10_000L..600_000L
}
