package com.example.rastro.av1

import java.util.Random
import kotlin.math.cos
import kotlin.math.sin

/**
 * Dados SINTÉTICOS para os testes de unidade — nunca usados como evidência física.
 * Gera uma caminhada cuja verdade satisfaz exatamente a restrição trapezoidal e mede-a
 * com ruído gaussiano coerente com a acurácia declarada (sensor calibrado), ou com
 * ruído [fatorRuido] vezes maior que o declarado (sensor otimista).
 *
 * @param raio68M raio de 68% declarado em cada fixo (m).
 * @param velocidadeMps módulo da velocidade verdadeira (m/s).
 * @param sigmaVelMps σ verdadeiro e declarado da velocidade (m/s).
 * @param sigmaRumoGraus σ verdadeiro e declarado do rumo (graus).
 * @param dtS intervalo entre fixos (s).
 */
object Sintetico {
    const val LAT0 = -21.2266
    const val LON0 = -44.9789

    fun caminhada(
        n: Int, semente: Long, raio68M: Double = 4.0, velocidadeMps: Double = 1.4,
        sigmaVelMps: Double = 0.2, sigmaRumoGraus: Double = 5.0, dtS: Double = 1.0, fatorRuido: Double = 1.0,
        curvaGrausPorS: Double = 3.0
    ): List<RegistroFix> {
        val rnd = Random(semente)
        val proj = ProjecaoLocal(LAT0, LON0)
        val sigmaPos = Estatistica.sigmaPorEixo(raio68M)
        var e = 0.0; var nn = 0.0
        var rumo = rnd.nextDouble() * 360
        var vE = velocidadeMps * sin(Math.toRadians(rumo)); var vN = velocidadeMps * cos(Math.toRadians(rumo))
        val saida = ArrayList<RegistroFix>()
        for (k in 0 until n) {
            if (k > 0) {
                val novoRumo = (rumo + curvaGrausPorS * dtS) % 360
                val nvE = velocidadeMps * sin(Math.toRadians(novoRumo)); val nvN = velocidadeMps * cos(Math.toRadians(novoRumo))
                e += (vE + nvE) / 2 * dtS; nn += (vN + nvN) / 2 * dtS
                vE = nvE; vN = nvN; rumo = novoRumo
            }
            val me = e + rnd.nextGaussian() * sigmaPos * fatorRuido
            val mn = nn + rnd.nextGaussian() * sigmaPos * fatorRuido
            val g = proj.paraGeografica(me, mn)
            val s = (velocidadeMps + rnd.nextGaussian() * sigmaVelMps * fatorRuido).coerceAtLeast(0.0)
            val th = ((rumo + rnd.nextGaussian() * sigmaRumoGraus * fatorRuido) % 360 + 360) % 360
            saida.add(RegistroFix(1_000_000_000L + (k * dtS * 1e9).toLong(), 1_790_000_000_000L + (k * dtS * 1000).toLong(),
                g[0], g[1], raio68M, s, sigmaVelMps, th, sigmaRumoGraus, "gps"))
        }
        return saida
    }

    /** Aparelho parado: posições com σ verdadeiro = fatorRuido × σ declarado; sem rumo. */
    fun estatico(n: Int, semente: Long, raio68M: Double = 5.0, fatorRuido: Double = 1.0): List<RegistroFix> {
        val rnd = Random(semente)
        val proj = ProjecaoLocal(LAT0, LON0)
        val sigma = Estatistica.sigmaPorEixo(raio68M) * fatorRuido
        return (0 until n).map { k ->
            val g = proj.paraGeografica(rnd.nextGaussian() * sigma, rnd.nextGaussian() * sigma)
            RegistroFix(1_000_000_000L + k * 1_000_000_000L, 1_790_000_000_000L + k * 1000L, g[0], g[1], raio68M,
                kotlin.math.abs(rnd.nextGaussian() * 0.1), 0.3, null, null, "gps")
        }
    }
}
