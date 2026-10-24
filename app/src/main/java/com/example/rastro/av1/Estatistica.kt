package com.example.rastro.av1

import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.sqrt

/** Funções estatísticas mínimas usadas pelos testes de erro grosseiro e pelos ensaios. */
object Estatistica {

    /**
     * Raio de 68% informado pelo Android (Location.accuracy) convertido em variância
     * por eixo, sob a hipótese gaussiana isotrópica de D-07:
     *   P(|e| ≤ r68) = 1 − exp(−r68² / (2σ²)) = 0,68  ⇒  σ² = r68² / (−2·ln 0,32).
     * Mesma conversão de [com.example.rastro.s2.CovarianciaGnss]; o teste
     * ReconciliadorTest garante que as duas continuem iguais.
     */
    fun varianciaPorEixo(raio68M: Double): Double = raio68M * raio68M / (-2.0 * ln(0.32))

    /** σ por eixo correspondente ao raio de 68% (≈ r68 / 1,5096). */
    fun sigmaPorEixo(raio68M: Double): Double = sqrt(varianciaPorEixo(raio68M))

    fun media(v: DoubleArray): Double = if (v.isEmpty()) Double.NaN else v.sum() / v.size

    /** Desvio padrão amostral (divisor n − 1). */
    fun desvioPadrao(v: DoubleArray): Double {
        if (v.size < 2) return Double.NaN
        val m = media(v)
        return sqrt(v.sumOf { (it - m) * (it - m) } / (v.size - 1))
    }

    fun rms(v: DoubleArray): Double = if (v.isEmpty()) Double.NaN else sqrt(v.sumOf { it * it } / v.size)

    /**
     * Quantil da normal padrão, Φ⁻¹(p), pela aproximação racional de Acklam
     * (erro relativo < 1,15·10⁻⁹), suficiente para limiares de teste.
     */
    fun quantilNormal(p: Double): Double {
        require(p > 0.0 && p < 1.0) { "p deve estar em (0, 1): $p" }
        val a = doubleArrayOf(-3.969683028665376e+01, 2.209460984245205e+02, -2.759285104469687e+02,
            1.383577518672690e+02, -3.066479806614716e+01, 2.506628277459239e+00)
        val b = doubleArrayOf(-5.447609879822406e+01, 1.615858368580409e+02, -1.556989798598866e+02,
            6.680131188771972e+01, -1.328068155288572e+01)
        val c = doubleArrayOf(-7.784894002430293e-03, -3.223964580411365e-01, -2.400758277161838e+00,
            -2.549732539343734e+00, 4.374664141464968e+00, 2.938163982698783e+00)
        val d = doubleArrayOf(7.784695709041462e-03, 3.224671290700398e-01, 2.445134137142996e+00,
            3.754408661907416e+00)
        val pBaixo = 0.02425
        return when {
            p < pBaixo -> {
                val q = sqrt(-2 * ln(p))
                (((((c[0] * q + c[1]) * q + c[2]) * q + c[3]) * q + c[4]) * q + c[5]) /
                    ((((d[0] * q + d[1]) * q + d[2]) * q + d[3]) * q + 1)
            }
            p <= 1 - pBaixo -> {
                val q = p - 0.5
                val r = q * q
                (((((a[0] * r + a[1]) * r + a[2]) * r + a[3]) * r + a[4]) * r + a[5]) * q /
                    (((((b[0] * r + b[1]) * r + b[2]) * r + b[3]) * r + b[4]) * r + 1)
            }
            else -> {
                val q = sqrt(-2 * ln(1 - p))
                -(((((c[0] * q + c[1]) * q + c[2]) * q + c[3]) * q + c[4]) * q + c[5]) /
                    ((((d[0] * q + d[1]) * q + d[2]) * q + d[3]) * q + 1)
            }
        }
    }

    /**
     * Quantil χ²(gl) pela aproximação de Wilson–Hilferty:
     *   χ²ₚ(gl) ≈ gl·(1 − 2/(9·gl) + zₚ·√(2/(9·gl)))³, com zₚ = Φ⁻¹(p).
     * Erro abaixo de 1% para gl ≥ 3, que é o caso das janelas usadas.
     */
    fun quantilQuiQuadrado(p: Double, grausLiberdade: Int): Double {
        require(grausLiberdade > 0) { "graus de liberdade deve ser positivo" }
        val k = grausLiberdade.toDouble()
        val z = quantilNormal(p)
        val t = 1.0 - 2.0 / (9.0 * k) + z * sqrt(2.0 / (9.0 * k))
        return k * t.pow(3)
    }

    /**
     * Limiar bicaudal do teste de medida com correção de Šidák para [quantidade]
     * comparações simultâneas: β = 1 − (1 − α)^(1/quantidade); z = Φ⁻¹(1 − β/2).
     * Sem a correção, uma janela com 40 medidas teria ~87% de chance de falso alarme a α = 5%.
     */
    fun limiarSidak(alfa: Double, quantidade: Int): Double {
        require(alfa > 0 && alfa < 1 && quantidade > 0)
        val beta = 1.0 - (1.0 - alfa).pow(1.0 / quantidade)
        return quantilNormal(1.0 - beta / 2.0)
    }

    fun quaseIgual(a: Double, b: Double, tol: Double = 1e-9): Boolean = abs(a - b) <= tol * maxOf(1.0, abs(a), abs(b))
}
