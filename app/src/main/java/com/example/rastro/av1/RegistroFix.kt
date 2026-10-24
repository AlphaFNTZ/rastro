package com.example.rastro.av1

import com.example.rastro.InvalidRegistroException
import com.example.rastro.InvalidSensorReadException
import java.util.Locale

/**
 * Um fixo GNSS exatamente como o Android o entregou, para gravação e reprodução.
 *
 * Diferente de [com.example.rastro.Posicao], aqui os campos opcionais continuam
 * opcionais: o registro preserva a ausência (campo vazio no CSV) em vez de exigir
 * velocidade ou inventar um valor. Quem reconcilia decide o que fazer com a falta.
 *
 * @property tempoMonotonicoNs Location.elapsedRealtimeNanos — relógio monotônico, usado para Δt.
 * @property tempoUtcMs Location.time — hora UTC do fixo, só para identificação.
 * @property latitude graus WGS84.
 * @property longitude graus WGS84.
 * @property acuraciaHorizontalM raio de 68% em metros (Location.accuracy).
 * @property velocidadeMps módulo da velocidade em m/s (Location.speed).
 * @property acuraciaVelocidadeMps acurácia de 68% da velocidade em m/s.
 * @property rumoGraus direção do movimento, graus a partir do norte no sentido horário.
 * @property acuraciaRumoGraus acurácia de 68% do rumo, em graus.
 * @property provedor "gps", "network", "fused"…
 */
data class RegistroFix(
    val tempoMonotonicoNs: Long,
    val tempoUtcMs: Long,
    val latitude: Double,
    val longitude: Double,
    val acuraciaHorizontalM: Double?,
    val velocidadeMps: Double?,
    val acuraciaVelocidadeMps: Double?,
    val rumoGraus: Double?,
    val acuraciaRumoGraus: Double?,
    val provedor: String
) {
    init {
        if (tempoMonotonicoNs <= 0) throw InvalidSensorReadException("tempoMonotonicoNs deve ser positivo: $tempoMonotonicoNs")
        if (tempoUtcMs <= 0) throw InvalidSensorReadException("tempoUtcMs deve ser positivo: $tempoUtcMs")
        if (!latitude.isFinite() || latitude !in -90.0..90.0) throw InvalidSensorReadException("latitude inválida: $latitude")
        if (!longitude.isFinite() || longitude !in -180.0..180.0) throw InvalidSensorReadException("longitude inválida: $longitude")
        naoNegativo(acuraciaHorizontalM, "acuraciaHorizontalM")
        naoNegativo(velocidadeMps, "velocidadeMps")
        naoNegativo(acuraciaVelocidadeMps, "acuraciaVelocidadeMps")
        naoNegativo(acuraciaRumoGraus, "acuraciaRumoGraus")
        if (rumoGraus != null && (!rumoGraus.isFinite() || rumoGraus < 0.0 || rumoGraus >= 360.0)) {
            throw InvalidSensorReadException("rumoGraus fora de [0, 360): $rumoGraus")
        }
        if (provedor.isBlank() || provedor.contains(',')) throw InvalidSensorReadException("provedor inválido: '$provedor'")
    }

    private fun naoNegativo(v: Double?, nome: String) {
        if (v != null && (!v.isFinite() || v < 0.0)) throw InvalidSensorReadException("$nome deve ser finito e ≥ 0: $v")
    }

    /** Usável na restrição cinemática: precisa de acurácia e velocidade com acurácia, todas > 0. */
    val reconciliavel: Boolean
        get() = (acuraciaHorizontalM ?: 0.0) > 0.0 && velocidadeMps != null && (acuraciaVelocidadeMps ?: 0.0) > 0.0
}

/**
 * Formato CSV dos fixos gravados. Uma linha por fixo, ponto como separador decimal,
 * campo vazio para valor ausente, linhas iniciadas por '#' são comentários.
 */
object RegistroFixCsv {
    const val CABECALHO = "tempo_monotonico_ns,tempo_utc_ms,latitude,longitude,acuracia_horizontal_m," +
        "velocidade_mps,acuracia_velocidade_mps,rumo_graus,acuracia_rumo_graus,provedor"
    private const val CAMPOS = 10

    fun linha(f: RegistroFix): String = listOf(
        f.tempoMonotonicoNs.toString(), f.tempoUtcMs.toString(),
        fmt(f.latitude, 8), fmt(f.longitude, 8),
        opt(f.acuraciaHorizontalM, 3), opt(f.velocidadeMps, 3), opt(f.acuraciaVelocidadeMps, 3),
        opt(f.rumoGraus, 2), opt(f.acuraciaRumoGraus, 2), f.provedor
    ).joinToString(",")

    /**
     * Lê o texto inteiro de um CSV. Linha inválida lança [InvalidRegistroException] com o número dela.
     * Com [tolerarUltimaLinha], uma última linha incompleta (arquivo ainda sendo gravado) é ignorada.
     */
    fun ler(texto: String, tolerarUltimaLinha: Boolean = false): List<RegistroFix> {
        val saida = ArrayList<RegistroFix>()
        val linhas = texto.lines()
        val ultimaComDado = linhas.indexOfLast { it.isNotBlank() }
        linhas.forEachIndexed { i, bruta ->
            val l = bruta.trim()
            if (l.isEmpty() || l.startsWith("#") || l.startsWith("tempo_monotonico_ns")) return@forEachIndexed
            try {
                saida.add(lerLinha(l, i + 1))
            } catch (e: InvalidRegistroException) {
                if (!(tolerarUltimaLinha && i == ultimaComDado)) throw e
            }
        }
        return saida
    }

    fun lerLinha(l: String, numero: Int): RegistroFix {
        val c = l.split(',')
        if (c.size != CAMPOS) throw InvalidRegistroException("linha $numero: esperados $CAMPOS campos, encontrados ${c.size}")
        try {
            return RegistroFix(
                c[0].trim().toLong(), c[1].trim().toLong(), c[2].trim().toDouble(), c[3].trim().toDouble(),
                c[4].opcional(), c[5].opcional(), c[6].opcional(), c[7].opcional(), c[8].opcional(), c[9].trim()
            )
        } catch (e: NumberFormatException) {
            throw InvalidRegistroException("linha $numero: número inválido (${e.message})")
        } catch (e: InvalidSensorReadException) {
            throw InvalidRegistroException("linha $numero: ${e.message}")
        }
    }

    private fun String.opcional(): Double? = trim().takeIf { it.isNotEmpty() }?.toDouble()
    private fun fmt(v: Double, casas: Int) = String.format(Locale.ROOT, "%.${casas}f", v)
    private fun opt(v: Double?, casas: Int) = v?.let { fmt(it, casas) } ?: ""
}

/**
 * Uma linha do registro de aquisição (1 Hz), gravada pelo consumidor do buffer IMU.
 * É a evidência da capacidade 4: mostra, segundo a segundo, quantas leituras
 * chegaram, a taxa efetiva, se a tela estava ligada e a bateria.
 *
 * @property tempoMonotonicoNs instante da linha (SystemClock.elapsedRealtimeNanos).
 * @property tempoUtcMs hora do relógio de parede, só para identificação.
 * @property imuTotal leituras IMU acumuladas desde o início.
 * @property imuJanela leituras drenadas neste ciclo do consumidor.
 * @property imuHz taxa efetiva no ciclo: (leituras − 1) / (último − primeiro timestamp).
 * @property imuDtMedioMs intervalo médio entre amostras IMU no ciclo, em ms.
 * @property imuDtMaxMs maior intervalo entre amostras IMU no ciclo, em ms (inclui a fronteira com o ciclo anterior).
 * @property imuDescartadas descartes acumulados do buffer circular.
 * @property fixosTotal fixos GNSS recebidos desde o início.
 * @property telaInterativa PowerManager.isInteractive: 1 = tela ligada, 0 = tela apagada.
 * @property bloqueado KeyguardManager.isKeyguardLocked: 1 = aparelho bloqueado (tela de bloqueio).
 * @property bateriaPct carga da bateria em %.
 * @property temperaturaBateriaC temperatura da bateria em °C, ou null se indisponível.
 */
data class RegistroAquisicao(
    val tempoMonotonicoNs: Long,
    val tempoUtcMs: Long,
    val imuTotal: Long,
    val imuJanela: Int,
    val imuHz: Double,
    val imuDtMedioMs: Double?,
    val imuDtMaxMs: Double?,
    val imuDescartadas: Long,
    val fixosTotal: Long,
    val telaInterativa: Boolean,
    val bloqueado: Boolean,
    val bateriaPct: Int?,
    val temperaturaBateriaC: Double?
) {
    /** Aparelho em repouso para o critério da capacidade 4: bloqueado ou com a tela apagada. */
    val emRepouso: Boolean get() = bloqueado || !telaInterativa
}

object RegistroAquisicaoCsv {
    const val CABECALHO = "tempo_monotonico_ns,tempo_utc_ms,imu_total,imu_janela,imu_hz,imu_dt_medio_ms," +
        "imu_dt_max_ms,imu_descartadas,fixos_total,tela_interativa,bloqueado,bateria_pct,temperatura_bateria_c"
    private const val CAMPOS = 13

    fun linha(r: RegistroAquisicao): String = listOf(
        r.tempoMonotonicoNs.toString(), r.tempoUtcMs.toString(), r.imuTotal.toString(), r.imuJanela.toString(),
        String.format(Locale.ROOT, "%.2f", r.imuHz),
        r.imuDtMedioMs?.let { String.format(Locale.ROOT, "%.3f", it) } ?: "",
        r.imuDtMaxMs?.let { String.format(Locale.ROOT, "%.3f", it) } ?: "",
        r.imuDescartadas.toString(), r.fixosTotal.toString(), if (r.telaInterativa) "1" else "0", if (r.bloqueado) "1" else "0",
        r.bateriaPct?.toString() ?: "", r.temperaturaBateriaC?.let { String.format(Locale.ROOT, "%.1f", it) } ?: ""
    ).joinToString(",")

    fun ler(texto: String): List<RegistroAquisicao> {
        val saida = ArrayList<RegistroAquisicao>()
        texto.lineSequence().forEachIndexed { i, bruta ->
            val l = bruta.trim()
            if (l.isEmpty() || l.startsWith("#") || l.startsWith("tempo_monotonico_ns")) return@forEachIndexed
            val c = l.split(',')
            if (c.size != CAMPOS) throw InvalidRegistroException("linha ${i + 1}: esperados $CAMPOS campos, encontrados ${c.size}")
            try {
                saida.add(RegistroAquisicao(c[0].toLong(), c[1].toLong(), c[2].toLong(), c[3].toInt(), c[4].toDouble(),
                    c[5].ifBlank { null }?.toDouble(), c[6].ifBlank { null }?.toDouble(), c[7].toLong(), c[8].toLong(),
                    c[9].trim() == "1", c[10].trim() == "1", c[11].ifBlank { null }?.toInt(), c[12].ifBlank { null }?.toDouble()))
            } catch (e: NumberFormatException) {
                throw InvalidRegistroException("linha ${i + 1}: número inválido (${e.message})")
            }
        }
        return saida
    }
}
