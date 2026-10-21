package com.example.rastro.av1

import java.util.Random
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** O que foi injetado, para o ensaio comparar com o que o detector encontrou. */
data class Injecao(val tipo: String, val indice: Int?, val magnitude: Double, val detalhe: String)

/**
 * Injeção determinística de falhas sobre um registro gravado.
 *
 * Usa java.util.Random, cujo algoritmo (gerador congruencial linear de 48 bits) é
 * especificado na documentação do Java: a mesma semente produz a mesma sequência em
 * qualquer JVM ou Android. Cada chamada cria o próprio Random a partir de
 * (semente, rótulo), então a ordem em que os ensaios rodam não altera o resultado.
 *
 * As três degradações pedidas no Anexo A:
 *  - ruído: soma ruído gaussiano de desvio [sigmaM] metros em leste e norte a todos os fixos,
 *    SEM alterar a acurácia declarada (o sensor passa a ser otimista);
 *  - perda: retira cada fixo com probabilidade [probabilidade], preservando os índices protegidos;
 *  - salto: desloca UM fixo de [magnitudeM] metros numa direção sorteada (erro grosseiro).
 */
class InjetorFalhas(val semente: Long) {

    private fun gerador(rotulo: String): Random = Random(semente * 31 + rotulo.hashCode())

    fun ruido(fixos: List<RegistroFix>, sigmaM: Double, rotulo: String = "ruido"): List<RegistroFix> {
        require(sigmaM >= 0)
        if (sigmaM == 0.0 || fixos.isEmpty()) return fixos
        val rnd = gerador("$rotulo/$sigmaM")
        val proj = ProjecaoLocal(fixos.first().latitude, fixos.first().longitude)
        return fixos.map { f ->
            val p = proj.paraLocal(f.latitude, f.longitude)
            val g = proj.paraGeografica(p[0] + rnd.nextGaussian() * sigmaM, p[1] + rnd.nextGaussian() * sigmaM)
            f.copy(latitude = g[0], longitude = g[1])
        }
    }

    fun perda(fixos: List<RegistroFix>, probabilidade: Double, protegidos: Set<Int> = emptySet(), rotulo: String = "perda"): List<RegistroFix> {
        require(probabilidade in 0.0..1.0)
        if (probabilidade == 0.0) return fixos
        val rnd = gerador("$rotulo/$probabilidade")
        return fixos.filterIndexed { i, _ -> val sorteio = rnd.nextDouble(); i in protegidos || sorteio >= probabilidade }
    }

    /** Retorna a janela com o fixo [indice] deslocado e a descrição da injeção. */
    fun salto(fixos: List<RegistroFix>, indice: Int, magnitudeM: Double, rotulo: String = "salto"): Pair<List<RegistroFix>, Injecao> {
        require(indice in fixos.indices && magnitudeM >= 0)
        val rnd = gerador("$rotulo/$indice/$magnitudeM")
        val angulo = rnd.nextDouble() * 2 * Math.PI
        val proj = ProjecaoLocal(fixos.first().latitude, fixos.first().longitude)
        val f = fixos[indice]
        val p = proj.paraLocal(f.latitude, f.longitude)
        val g = proj.paraGeografica(p[0] + magnitudeM * sin(angulo), p[1] + magnitudeM * cos(angulo))
        val nova = fixos.toMutableList().also { it[indice] = f.copy(latitude = g[0], longitude = g[1]) }
        return nova to Injecao("salto", indice, magnitudeM, "direção %.0f°".format(java.util.Locale.ROOT, Math.toDegrees(angulo)))
    }

    /** Sorteia um índice interior (1 … n−2) de forma reprodutível. */
    fun indiceInterior(tamanho: Int, rotulo: String): Int {
        require(tamanho >= 3)
        return 1 + gerador("indice/$rotulo").nextInt(tamanho - 2)
    }

    companion object {
        /** Distância em metros entre dois pontos locais. */
        fun distancia(a: DoubleArray, b: DoubleArray): Double = sqrt((a[0] - b[0]) * (a[0] - b[0]) + (a[1] - b[1]) * (a[1] - b[1]))
    }
}
