package com.example.rastro.av1

/**
 * Estado após processar um fixo na reprodução.
 *
 * @property indice índice do fixo no registro reproduzido.
 * @property tempoS tempo do fixo desde o início do registro, em segundos.
 * @property janelaInicio índice do primeiro fixo da janela deslizante.
 * @property rmsAntesM, rmsDepoisM resíduo das restrições antes e depois da reconciliação (m).
 * @property estatisticaGlobal γ da janela; [limiarGlobal] é o χ² de 95%.
 * @property eliminados índices ABSOLUTOS no registro dos fixos eliminados pelo detector nesta janela.
 * @property saltoInjetadoEm índice absoluto onde o salto foi injetado (null = sem injeção).
 */
data class PassoReproducao(
    val indice: Int,
    val total: Int,
    val tempoS: Double,
    val janelaInicio: Int,
    val rmsAntesM: Double?,
    val rmsDepoisM: Double?,
    val estatisticaGlobal: Double?,
    val limiarGlobal: Double?,
    val eliminados: List<FixoEliminado>,
    val saltoInjetadoEm: Int?,
    val erro: String?
)

/**
 * Reprodução determinística de um registro gravado, com janela deslizante.
 *
 * Não depende de relógio: [passo] processa o fixo i usando os [tamanhoJanela] fixos
 * que terminam nele. A tela de ensaio chama [passo] com espera de Δt/aceleração entre
 * fixos (reprodução acelerada); os testes chamam em sequência, sem espera, e obtêm
 * sempre o mesmo resultado para o mesmo registro e a mesma semente.
 */
class ReprodutorRegistro(
    registroBruto: List<RegistroFix>,
    val tamanhoJanela: Int = 10,
    val detector: DetectorErroGrosseiro = DetectorErroGrosseiro(),
    semente: Long = 20261020L,
    saltoM: Double = 0.0,
    ruidoM: Double = 0.0,
    perdaProb: Double = 0.0
) {
    val registro: List<RegistroFix>
    val saltoEm: Int?

    init {
        require(tamanhoJanela >= detector.minimoFixos + 1)
        val injetor = InjetorFalhas(semente)
        var r = Janelas.filtrar(registroBruto)
        r = injetor.ruido(r, ruidoM, "reproducao")
        r = injetor.perda(r, perdaProb, emptySet(), "reproducao")
        if (saltoM > 0 && r.size >= 3) {
            val idx = r.size / 2
            r = injetor.salto(r, idx, saltoM, "reproducao").first
            saltoEm = idx
        } else saltoEm = null
        registro = r
    }

    val tamanho: Int get() = registro.size

    /** Intervalo real até o próximo fixo, em segundos (0 no último). */
    fun intervaloAteProximoS(i: Int): Double =
        if (i + 1 >= registro.size) 0.0 else (registro[i + 1].tempoMonotonicoNs - registro[i].tempoMonotonicoNs) / 1e9

    fun passo(i: Int): PassoReproducao {
        require(i in registro.indices)
        val ini = maxOf(0, i - tamanhoJanela + 1)
        val janela = registro.subList(ini, i + 1)
        val t = (registro[i].tempoMonotonicoNs - registro.first().tempoMonotonicoNs) / 1e9
        if (janela.size < tamanhoJanela) return PassoReproducao(i, registro.size, t, ini, null, null, null, null, emptyList(), saltoEm, null)
        // Janela com lacuna grande não satisfaz a hipótese de Δt curto: informa em vez de reconciliar.
        val maiorDt = janela.zipWithNext().maxOf { (a, b) -> (b.tempoMonotonicoNs - a.tempoMonotonicoNs) / 1e9 }
        if (maiorDt > 3.0) return PassoReproducao(i, registro.size, t, ini, null, null, null, null, emptyList(), saltoEm,
            "lacuna de %.1f s na janela".format(java.util.Locale.ROOT, maiorDt))
        return try {
            val det = detector.analisar(janela)
            val bruto = Reconciliador.reconciliar(janela, detector.fatorCovariancia)
            PassoReproducao(i, registro.size, t, ini, bruto.rmsResiduoAntesM, bruto.rmsResiduoDepoisM, bruto.estatisticaGlobal,
                det.limiarGlobalInicial, det.eliminados.map { it.copy(indiceOriginal = it.indiceOriginal + ini) }, saltoEm, null)
        } catch (e: com.example.rastro.RastroDomainException) {
            PassoReproducao(i, registro.size, t, ini, null, null, null, null, emptyList(), saltoEm, e.message)
        }
    }
}
