package com.example.rastro.av1

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max

/** Parâmetros compartilhados entre a tela de ensaio e o teste que gera as evidências. */
object EvidenciasPadrao {
    /** Semente declarada de todos os ensaios da AV1 (data da entrega). */
    const val SEMENTE = 20261020L
    const val TAMANHO_JANELA = 10
}

/**
 * Formata os resultados dos ensaios como Markdown (para o relatório), JSON (para os
 * slides) e SVG (gráfico de dispersão). Nenhum número é escrito à mão: tudo sai daqui.
 */
object SaidaEvidencias {
    private val hora = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss").withZone(ZoneId.of("America/Sao_Paulo"))
    fun f(v: Double?, casas: Int = 2): String = when {
        v == null -> "—"
        v.isNaN() -> "—"
        else -> String.format(Locale.ROOT, "%.${casas}f", v)
    }
    fun pct(v: Double): String = if (v.isNaN()) "—" else String.format(Locale.ROOT, "%.0f%%", v * 100)
    fun data(utcMs: Long?): String = utcMs?.let { hora.format(Instant.ofEpochMilli(it)) } ?: "—"

    fun tabelaResiduos(l: List<LinhaResiduo>): String = buildString {
        appendLine("| Janela | Início (BRT) | Fixos | Duração (s) | RMS r antes (m) | máx \\|r\\| antes (m) | RMS r depois (m) | γ | χ² 95% | Ajuste médio posição (m) | Eliminados pelo detector |")
        appendLine("|---|---|---|---|---|---|---|---|---|---|---|")
        l.forEach {
            appendLine("| ${it.janela} | ${data(it.inicioUtcMs)} | ${it.fixos} | ${f(it.duracaoS, 1)} | ${f(it.rmsAntesM, 3)} | ${f(it.maxAntesM, 3)} | " +
                "${String.format(Locale.ROOT, "%.1e", it.rmsDepoisM)} | ${f(it.estatisticaGlobal, 1)} | ${f(it.limiarGlobal, 1)} | ${f(it.ajusteMedioPosicaoM, 2)} | " +
                "${if (it.eliminados.isEmpty()) "nenhum" else it.eliminados.joinToString("; ") { e -> "fixo ${e.indiceOriginal} (${e.medida}, z=${f(e.z, 1)})" }} |")
        }
    }

    fun tabelaDemonstracao(l: List<LinhaDemonstracao>): String = buildString {
        appendLine("| Janela | Fixo com salto | Salto (m) | RMS vizinhos SEM detector (m) | RMS vizinhos COM detector (m) | Erro no fixo sem detector (m) | Detectou | Localizou |")
        appendLine("|---|---|---|---|---|---|---|---|")
        l.forEach {
            appendLine("| ${it.janela} | ${it.indiceSalto} | ${f(it.saltoM, 0)} | ${f(it.rmsSemDetectorM, 2)} | ${f(it.rmsComDetectorM, 2)} | ${f(it.erroNoFixoSemDetectorM, 2)} | " +
                "${if (it.detectou) "sim" else "não"} | ${if (it.localizouCorretamente) "sim" else "não"} |")
        }
    }

    fun tabelaSensibilidade(l: List<LinhaSensibilidade>): String = buildString {
        appendLine("| Degradação | Parâmetro | Salto (m) | Ensaios | Inviáveis | Detecção | Localização correta | Falso alarme |")
        appendLine("|---|---|---|---|---|---|---|---|")
        l.forEach {
            appendLine("| ${it.degradacao} | ${f(it.parametro, 2)} | ${f(it.saltoM, 0)} | ${it.ensaios} | ${it.inviaveis} | ${pct(it.taxaDeteccao)} | ${pct(it.taxaLocalizacaoCorreta)} | ${pct(it.taxaFalsoAlarme)} |")
        }
    }

    fun tabelaEstatico(r: ResultadoEstatico): String = buildString {
        appendLine("| Grandeza | Valor |")
        appendLine("|---|---|")
        appendLine("| Fixos GPS usados | ${r.n} |")
        appendLine("| Duração (min) | ${f(r.duracaoS / 60, 1)} |")
        appendLine("| r68 médio declarado (m) | ${f(r.raio68MedioM)} |")
        appendLine("| σ por eixo declarado (m) | ${f(r.sigmaDeclaradoM)} |")
        appendLine("| σ leste medido (m) | ${f(r.sigmaLesteMedidoM)} |")
        appendLine("| σ norte medido (m) | ${f(r.sigmaNorteMedidoM)} |")
        appendLine("| Razão de otimismo (medido/declarado) | ${f(r.razaoOtimismo)} (leste ${f(r.razaoLeste)}, norte ${f(r.razaoNorte)}) |")
        appendLine("| Fração dentro do próprio r68 (esperado 68%) | ${pct(r.fracaoDentroRaio68)} |")
        appendLine("| RMS da velocidade parado (m/s) | ${f(r.rmsVelocidadeMps, 3)} |")
        appendLine("| RMS da acurácia de velocidade declarada (m/s) | ${f(r.rmsAcuraciaVelocidadeMps, 3)} |")
    }

    fun tabelaAquisicao(r: ResultadoAquisicao): String = buildString {
        appendLine("| Grandeza | Valor |")
        appendLine("|---|---|")
        appendLine("| Linhas do registro (1 Hz) | ${r.linhas} |")
        appendLine("| Duração total (min) | ${f(r.duracaoTotalMin, 1)} |")
        appendLine("| Maior trecho contínuo com tela bloqueada (min) | ${f(r.bloqueadoMin, 1)} |")
        appendLine("| Início → fim do trecho (BRT) | ${data(r.bloqueadoInicioUtcMs)} → ${data(r.bloqueadoFimUtcMs)} |")
        appendLine("| Maior lacuna entre linhas no trecho (s) | ${f(r.maiorLacunaRegistroS, 2)} |")
        appendLine("| IMU: taxa média / mínima (Hz) | ${f(r.imuHzMedio, 1)} / ${f(r.imuHzMinimo, 1)} |")
        appendLine("| IMU: maior intervalo entre amostras (ms) | ${f(r.imuDtMaxMs, 1)} |")
        appendLine("| IMU: leituras / descartadas no trecho | ${r.imuLeiturasNoTrecho} / ${r.descartadasNoTrecho} |")
        appendLine("| GNSS: fixos no trecho | ${r.fixosNoTrecho} |")
        appendLine("| GNSS: maior intervalo entre fixos (s) | ${f(r.maiorIntervaloFixosS, 1)} |")
        appendLine("| Bateria início → fim (%) | ${r.bateriaInicio ?: "—"} → ${r.bateriaFim ?: "—"} |")
        appendLine("| Consumo (%/h) | ${f(r.consumoPctHora, 1)} |")
        appendLine("| Temperatura máxima da bateria (°C) | ${f(r.temperaturaMaxC, 1)} |")
        appendLine("| Critério (≥ 30 min bloqueado, IMU e GNSS chegando) | ${if (r.atendeCriterio) "ATENDIDO" else "NÃO ATENDIDO"} |")
    }

    /** Gráfico de dispersão das posições estáticas (m), com o círculo do r68 médio declarado e do 68% medido. */
    fun svgDispersao(r: ResultadoEstatico, titulo: String): String {
        val lim = max(r.pontos.maxOf { max(abs(it[0]), abs(it[1])) }, r.raio68MedioM) * 1.15
        val escalaMax = ceil(lim)
        val w = 560.0; val h = 600.0; val m = 60.0; val tam = w - 2 * m
        fun x(e: Double) = m + (e + escalaMax) / (2 * escalaMax) * tam
        fun y(n: Double) = 70 + (escalaMax - n) / (2 * escalaMax) * tam
        val raioMedido68 = r.pontos.map { kotlin.math.sqrt(it[0] * it[0] + it[1] * it[1]) }.sorted().let { it[((it.size - 1) * 0.68).toInt()] }
        val s = StringBuilder()
        s.append("""<svg xmlns="http://www.w3.org/2000/svg" width="${w.toInt()}" height="${h.toInt()}" viewBox="0 0 $w $h" font-family="Helvetica, Arial, sans-serif">""")
        s.append("""<rect width="100%" height="100%" fill="white"/>""")
        s.append("""<text x="${w / 2}" y="28" text-anchor="middle" font-size="16" font-weight="bold">${esc(titulo)}</text>""")
        s.append("""<text x="${w / 2}" y="48" text-anchor="middle" font-size="12" fill="#444">${r.n} fixos GPS, ${f(r.duracaoS / 60, 1)} min · razão de otimismo ${f(r.razaoOtimismo)}</text>""")
        s.append("""<rect x="$m" y="70" width="$tam" height="$tam" fill="none" stroke="#999"/>""")
        val passo = listOf(1.0, 2.0, 5.0, 10.0, 20.0, 50.0).first { escalaMax / it <= 5 }
        var t = -kotlin.math.floor(escalaMax / passo) * passo
        while (t <= escalaMax + 1e-9) {
            s.append("""<line x1="${x(t)}" y1="70" x2="${x(t)}" y2="${70 + tam}" stroke="#eee"/><line x1="$m" y1="${y(t)}" x2="${m + tam}" y2="${y(t)}" stroke="#eee"/>""")
            s.append("""<text x="${x(t)}" y="${70 + tam + 16}" text-anchor="middle" font-size="11">${f(t, 0)}</text><text x="${m - 6}" y="${y(t) + 4}" text-anchor="end" font-size="11">${f(t, 0)}</text>""")
            t += passo
        }
        s.append("""<text x="${w / 2}" y="${70 + tam + 36}" text-anchor="middle" font-size="12">leste em relação à média (m)</text>""")
        s.append("""<text x="16" y="${70 + tam / 2}" text-anchor="middle" font-size="12" transform="rotate(-90 16 ${70 + tam / 2})">norte em relação à média (m)</text>""")
        val pxPorM = tam / (2 * escalaMax)
        s.append("""<circle cx="${x(0.0)}" cy="${y(0.0)}" r="${r.raio68MedioM * pxPorM}" fill="none" stroke="#d62728" stroke-width="2" stroke-dasharray="6 4"/>""")
        s.append("""<circle cx="${x(0.0)}" cy="${y(0.0)}" r="${raioMedido68 * pxPorM}" fill="none" stroke="#1f77b4" stroke-width="2"/>""")
        r.pontos.forEach { s.append("""<circle cx="${f(x(it[0]), 1)}" cy="${f(y(it[1]), 1)}" r="2.2" fill="#333" fill-opacity="0.55"/>""") }
        s.append("""<text x="${m + 8}" y="88" font-size="11" fill="#d62728">- - r68 médio declarado: ${f(r.raio68MedioM)} m</text>""")
        s.append("""<text x="${m + 8}" y="104" font-size="11" fill="#1f77b4">— raio com 68% dos fixos (medido): ${f(raioMedido68)} m</text>""")
        s.append("</svg>")
        return s.toString()
    }

    private fun esc(t: String) = t.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

    /** JSON plano com os números que os slides usam. Escrito à mão para não acrescentar dependência. */
    fun json(campos: Map<String, Any?>): String = campos.entries.joinToString(",\n  ", "{\n  ", "\n}\n") { (k, v) ->
        "\"$k\": " + when (v) {
            null -> "null"
            is Number -> if (v is Double && (v.isNaN() || v.isInfinite())) "null" else v.toString()
            is Boolean -> v.toString()
            else -> "\"" + v.toString().replace("\\", "\\\\").replace("\"", "\\\"") + "\""
        }
    }
}
