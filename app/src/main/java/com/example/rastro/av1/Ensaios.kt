package com.example.rastro.av1

import kotlin.math.sqrt

/** Uma linha da tabela de resíduo antes/depois (capacidade 2). Unidades: metros. */
data class LinhaResiduo(
    val janela: Int,
    val inicioUtcMs: Long,
    val fixos: Int,
    val duracaoS: Double,
    val rmsAntesM: Double,
    val maxAntesM: Double,
    val rmsDepoisM: Double,
    val estatisticaGlobal: Double,
    val limiarGlobal: Double,
    val ajusteMedioPosicaoM: Double,
    val eliminados: List<FixoEliminado>
)

object TabelaResiduos {
    fun calcular(janelas: List<List<RegistroFix>>, detector: DetectorErroGrosseiro): List<LinhaResiduo> =
        janelas.mapIndexed { i, j ->
            val rec = Reconciliador.reconciliar(j, detector.fatorCovariancia)
            val det = detector.analisar(j)
            val ajustePos = (0 until rec.medidas.quantidadeFixos).map { k ->
                InjetorFalhas.distancia(rec.posicaoMedida(k), rec.posicaoReconciliada(k))
            }.average()
            LinhaResiduo(i + 1, j.first().tempoUtcMs, j.size, rec.medidas.temposS.last(), rec.rmsResiduoAntesM,
                rec.maxResiduoAntesM, rec.rmsResiduoDepoisM, rec.estatisticaGlobal,
                Estatistica.quantilQuiQuadrado(1 - detector.alfa, rec.grausLiberdade), ajustePos, det.eliminados)
        }
}

/**
 * Demonstração pedida no Anexo A: sem o detector, a reconciliação piora o resultado.
 *
 * Referência = reconciliação da janela limpa (não há verdade de campo; é a melhor
 * estimativa disponível). Injeta-se um salto de [saltoM] metros no fixo central e mede-se
 * o quanto os fixos VIZINHOS, que não foram corrompidos, se afastam da referência:
 *  - sem detector: o ajuste espalha o salto pelas medidas vizinhas;
 *  - com detector: o fixo corrompido é eliminado e os vizinhos voltam para perto da referência.
 *
 * @property rmsSemDetectorM RMS da distância à referência dos fixos não corrompidos, sem detector.
 * @property rmsComDetectorM idem, com o detector.
 * @property erroNoFixoSemDetectorM distância do fixo corrompido reconciliado à referência (sem detector).
 */
data class LinhaDemonstracao(
    val janela: Int,
    val indiceSalto: Int,
    val saltoM: Double,
    val rmsSemDetectorM: Double,
    val rmsComDetectorM: Double,
    val erroNoFixoSemDetectorM: Double,
    val detectou: Boolean,
    val localizouCorretamente: Boolean
)

object DemonstracaoDetector {
    fun calcular(janelas: List<List<RegistroFix>>, detector: DetectorErroGrosseiro, injetor: InjetorFalhas, saltoM: Double = 30.0): List<LinhaDemonstracao> =
        janelas.mapIndexed { w, j ->
            val idx = j.size / 2
            val ref = Reconciliador.reconciliar(j, detector.fatorCovariancia)
            val (sujo, _) = injetor.salto(j, idx, saltoM, "demo/$w")
            val sem = Reconciliador.reconciliar(sujo, detector.fatorCovariancia)
            val com = detector.analisar(sujo)
            // Tudo comparado no referencial local da referência (a origem muda se o 1º fixo for eliminado).
            val proj = ref.medidas.projecao
            val vizinhos = j.indices.filter { it != idx }
            val dSem = vizinhos.map { InjetorFalhas.distancia(sem.posicaoReconciliadaEm(it, proj), ref.posicaoReconciliada(it)) }
            val dCom = vizinhos.mapNotNull { k ->
                val local = com.indicesMantidos.indexOf(k)
                if (local < 0) null else InjetorFalhas.distancia(com.final.posicaoReconciliadaEm(local, proj), ref.posicaoReconciliada(k))
            }
            LinhaDemonstracao(w + 1, idx, saltoM, rms(dSem), rms(dCom),
                InjetorFalhas.distancia(sem.posicaoReconciliadaEm(idx, proj), ref.posicaoReconciliada(idx)),
                com.detectou, com.eliminados.firstOrNull()?.indiceOriginal == idx)
        }

    private fun rms(v: List<Double>) = if (v.isEmpty()) Double.NaN else sqrt(v.sumOf { it * it } / v.size)
}

/** Uma linha da tabela de sensibilidade. Taxas em [0, 1]. */
data class LinhaSensibilidade(
    val degradacao: String,
    val parametro: Double,
    val saltoM: Double,
    val ensaios: Int,
    val inviaveis: Int,
    val taxaDeteccao: Double,
    val taxaLocalizacaoCorreta: Double,
    val taxaFalsoAlarme: Double
)

/**
 * Ensaio de sensibilidade da detecção à degradação injetada (capacidade 5).
 *
 * Cada ensaio: uma janela real × uma repetição, com índice interior e direção do salto
 * sorteados pela semente. Três varreduras:
 *  1. salto de magnitude crescente (0 = controle, mede só falso alarme);
 *  2. ruído gaussiano extra (σ em metros, acurácia declarada inalterada) + salto fixo;
 *  3. perda de fixos (probabilidade) + salto fixo; a janela encurta e Δt cresce.
 * Definições: detecção = o fixo corrompido foi eliminado; localização correta = foi o
 * PRIMEIRO eliminado; falso alarme = algum fixo não corrompido foi eliminado.
 */
class EnsaioSensibilidade(
    val semente: Long,
    val detector: DetectorErroGrosseiro = DetectorErroGrosseiro(),
    val repeticoes: Int = 20,
    val saltos: List<Double> = listOf(0.0, 2.0, 5.0, 10.0, 15.0, 20.0, 30.0, 50.0, 100.0),
    val ruidos: List<Double> = listOf(0.0, 1.0, 2.0, 5.0, 10.0),
    val perdas: List<Double> = listOf(0.0, 0.1, 0.2, 0.3, 0.5),
    val saltoReferenciaM: Double = 20.0
) {
    private val injetor = InjetorFalhas(semente)

    fun executar(janelas: List<List<RegistroFix>>): List<LinhaSensibilidade> {
        require(janelas.isNotEmpty()) { "Sem janelas para o ensaio" }
        val saida = ArrayList<LinhaSensibilidade>()
        for (s in saltos) saida.add(varrer("salto", s, s, janelas) { j, rot -> j to rot })
        for (sigma in ruidos) saida.add(varrer("ruído σ (m)", sigma, saltoReferenciaM, janelas) { j, rot -> injetor.ruido(j, sigma, rot) to rot })
        for (p in perdas) saida.add(varrer("perda (prob.)", p, saltoReferenciaM, janelas, perda = p) { j, rot -> j to rot })
        return saida
    }

    private fun varrer(
        nome: String, parametro: Double, saltoM: Double, janelas: List<List<RegistroFix>>, perda: Double = 0.0,
        degradar: (List<RegistroFix>, String) -> Pair<List<RegistroFix>, String>
    ): LinhaSensibilidade {
        var total = 0; var inviaveis = 0; var detectou = 0; var localizou = 0; var falso = 0
        janelas.forEachIndexed { w, base ->
            repeat(repeticoes) { r ->
                val rotulo = "j$w/r$r"
                val (degradada, rot) = degradar(base, rotulo)
                val idx = injetor.indiceInterior(degradada.size, rotulo)
                var janela = degradada
                if (saltoM > 0) janela = injetor.salto(degradada, idx, saltoM, rot).first
                var alvo = idx
                if (perda > 0) {
                    val antes = janela
                    janela = injetor.perda(janela, perda, setOf(idx), "$rot/perda")
                    alvo = antes.subList(0, idx).count { it in janela }.let { cont -> if (janela.contains(antes[idx])) cont else -1 }
                }
                total++
                if (janela.size < detector.minimoFixos + 1 || alvo <= 0 || alvo >= janela.size - 1) { inviaveis++; return@repeat }
                val det = runCatching { detector.analisar(janela) }.getOrNull()
                if (det == null) { inviaveis++; return@repeat }
                val elim = det.eliminados.map { it.indiceOriginal }
                if (saltoM > 0 && alvo in elim) detectou++
                if (saltoM > 0 && elim.firstOrNull() == alvo) localizou++
                if (elim.any { saltoM == 0.0 || it != alvo }) falso++
            }
        }
        val validos = (total - inviaveis).coerceAtLeast(1)
        return LinhaSensibilidade(nome, parametro, saltoM, total, inviaveis,
            if (saltoM > 0) detectou.toDouble() / validos else Double.NaN,
            if (saltoM > 0) localizou.toDouble() / validos else Double.NaN,
            falso.toDouble() / validos)
    }

    companion object {
        /** Menor salto com detecção ≥ [limiar]; abaixo dele a detecção "começa a falhar". */
        fun saltoMinimoDetectavel(linhas: List<LinhaSensibilidade>, limiar: Double = 0.9): Double? =
            linhas.filter { it.degradacao == "salto" && it.saltoM > 0 }.sortedBy { it.saltoM }
                .firstOrNull { it.taxaDeteccao >= limiar }?.saltoM
    }
}
