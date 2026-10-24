package com.example.rastro.av1

/** Um fixo eliminado pelo detector, com o índice na janela ORIGINAL e a estatística que o condenou. */
data class FixoEliminado(val indiceOriginal: Int, val medida: String, val z: Double, val limiar: Double)

/**
 * @property eliminados fixos retirados, na ordem em que foram retirados.
 * @property final reconciliação da janela já sem os eliminados.
 * @property indicesMantidos índices (na janela original) dos fixos usados em [final].
 * @property globalInicial γ da janela completa e o limiar χ² correspondente: o teste global
 *   diz SE há erro grosseiro; o teste de medida diz ONDE.
 */
data class ResultadoDeteccao(
    val eliminados: List<FixoEliminado>,
    val final: ResultadoReconciliacao,
    val indicesMantidos: List<Int>,
    val globalInicial: Double,
    val limiarGlobalInicial: Double
) {
    val detectou: Boolean get() = eliminados.isNotEmpty()
}

/**
 * Detecção de erro grosseiro por teste de medida (resíduo normalizado do ajuste) com
 * eliminação serial, como em Narasimhan & Jordache (2000), cap. 7 e 8.
 *
 * Para cada medida j, zⱼ = |aⱼ| / √Wⱼⱼ segue N(0, 1) se não houver erro grosseiro.
 * O critério quantitativo de corte é o limiar de Šidák para n = 4N comparações:
 * β = 1 − (1 − α)^(1/n), z_crítico = Φ⁻¹(1 − β/2). Com α = 5% e N = 10 fixos, z_crítico ≈ 3,2.
 * Se max zⱼ > z_crítico, o fixo que contém a medida j é retirado e a janela é
 * reconciliada de novo, até não haver mais violação ou atingir [maxEliminacoes].
 *
 * Limitação conhecida: nos fixos das pontas da janela só há uma restrição por eixo,
 * e todas as medidas dela têm o mesmo z — o erro é detectado, mas não localizado entre
 * os dois fixos. Por isso o ensaio injeta saltos no interior e a reprodução usa janela
 * deslizante (todo fixo passa pelo interior de alguma janela).
 */
class DetectorErroGrosseiro(
    val alfa: Double = 0.05,
    val maxEliminacoes: Int = 3,
    val minimoFixos: Int = 4,
    val fatorCovariancia: Double = 1.0
) {
    init {
        require(alfa > 0 && alfa < 0.5) { "alfa deve estar em (0, 0,5)" }
        require(maxEliminacoes >= 0 && minimoFixos >= 3)
    }

    fun analisar(janela: List<RegistroFix>): ResultadoDeteccao {
        var indices = janela.indices.toList()
        var atual = Reconciliador.reconciliar(janela, fatorCovariancia)
        val gamaInicial = atual.estatisticaGlobal
        val limiarGlobal = Estatistica.quantilQuiQuadrado(1 - alfa, atual.grausLiberdade)
        val eliminados = ArrayList<FixoEliminado>()
        while (eliminados.size < maxEliminacoes && indices.size > minimoFixos) {
            val limiar = Estatistica.limiarSidak(alfa, atual.zMedidas.size)
            var jMax = 0
            for (j in atual.zMedidas.indices) if (atual.zMedidas[j] > atual.zMedidas[jMax]) jMax = j
            val zMax = atual.zMedidas[jMax]
            if (zMax <= limiar) break
            val kLocal = jMax / 4
            eliminados.add(FixoEliminado(indices[kLocal], NOMES[jMax % 4], zMax, limiar))
            indices = indices.filterIndexed { i, _ -> i != kLocal }
            atual = Reconciliador.reconciliar(indices.map { janela[it] }, fatorCovariancia)
        }
        return ResultadoDeteccao(eliminados, atual, indices, gamaInicial, limiarGlobal)
    }

    companion object { val NOMES = listOf("posição leste", "posição norte", "velocidade leste", "velocidade norte") }
}
