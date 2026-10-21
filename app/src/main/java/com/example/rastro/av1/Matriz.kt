package com.example.rastro.av1

import com.example.rastro.InvalidReconciliacaoException
import kotlin.math.abs

/**
 * Matriz densa de dimensões fixas, com as três operações de que a reconciliação
 * precisa: produto, transposta e inversa por Gauss-Jordan com pivotamento parcial.
 *
 * As janelas de reconciliação têm no máximo algumas dezenas de linhas (2 restrições
 * por par de fixos), então O(n³) da inversão é irrelevante frente ao período do GNSS.
 * Os dados ficam num único DoubleArray em ordem de linha; nenhum método expõe esse
 * array — [paraArray] devolve cópia.
 */
class Matriz private constructor(val linhas: Int, val colunas: Int, private val dados: DoubleArray) {

    operator fun get(i: Int, j: Int): Double = dados[i * colunas + j]

    operator fun times(outra: Matriz): Matriz {
        if (colunas != outra.linhas) {
            throw InvalidReconciliacaoException("Dimensões incompatíveis: ${linhas}x$colunas · ${outra.linhas}x${outra.colunas}")
        }
        val r = DoubleArray(linhas * outra.colunas)
        for (i in 0 until linhas) {
            for (k in 0 until colunas) {
                val a = dados[i * colunas + k]
                if (a == 0.0) continue
                for (j in 0 until outra.colunas) r[i * outra.colunas + j] += a * outra.dados[k * outra.colunas + j]
            }
        }
        return Matriz(linhas, outra.colunas, r)
    }

    /** Produto matriz-vetor, sem montar matriz coluna. */
    operator fun times(v: DoubleArray): DoubleArray {
        if (colunas != v.size) throw InvalidReconciliacaoException("Vetor de tamanho ${v.size} para matriz ${linhas}x$colunas")
        return DoubleArray(linhas) { i ->
            var s = 0.0
            for (j in 0 until colunas) s += dados[i * colunas + j] * v[j]
            s
        }
    }

    fun transposta(): Matriz {
        val r = DoubleArray(dados.size)
        for (i in 0 until linhas) for (j in 0 until colunas) r[j * linhas + i] = dados[i * colunas + j]
        return Matriz(colunas, linhas, r)
    }

    /**
     * Inversa por Gauss-Jordan com pivotamento parcial. Lança exceção específica se a
     * matriz não for quadrada ou se o pivô ficar abaixo de [tolerancia] relativa à
     * maior entrada (matriz numericamente singular).
     */
    fun inversa(tolerancia: Double = 1e-12): Matriz {
        if (linhas != colunas) throw InvalidReconciliacaoException("Inversa de matriz não quadrada ${linhas}x$colunas")
        val n = linhas
        val a = dados.copyOf()
        val inv = DoubleArray(n * n).also { for (i in 0 until n) it[i * n + i] = 1.0 }
        val escala = a.maxOfOrNull { abs(it) }?.takeIf { it > 0.0 }
            ?: throw InvalidReconciliacaoException("Matriz nula não é inversível")
        for (col in 0 until n) {
            var piv = col
            for (i in col + 1 until n) if (abs(a[i * n + col]) > abs(a[piv * n + col])) piv = i
            if (abs(a[piv * n + col]) < tolerancia * escala) {
                throw InvalidReconciliacaoException("Matriz singular na coluna $col (pivô ${a[piv * n + col]})")
            }
            if (piv != col) { trocarLinhas(a, n, piv, col); trocarLinhas(inv, n, piv, col) }
            val p = a[col * n + col]
            for (j in 0 until n) { a[col * n + j] /= p; inv[col * n + j] /= p }
            for (i in 0 until n) {
                if (i == col) continue
                val f = a[i * n + col]
                if (f == 0.0) continue
                for (j in 0 until n) {
                    a[i * n + j] -= f * a[col * n + j]
                    inv[i * n + j] -= f * inv[col * n + j]
                }
            }
        }
        return Matriz(n, n, inv)
    }

    fun diagonal(): DoubleArray = DoubleArray(minOf(linhas, colunas)) { this[it, it] }

    fun paraArray(): Array<DoubleArray> = Array(linhas) { i -> DoubleArray(colunas) { j -> this[i, j] } }

    private fun trocarLinhas(m: DoubleArray, n: Int, a: Int, b: Int) {
        for (j in 0 until n) { val t = m[a * n + j]; m[a * n + j] = m[b * n + j]; m[b * n + j] = t }
    }

    companion object {
        fun zeros(linhas: Int, colunas: Int): Matriz {
            if (linhas <= 0 || colunas <= 0) throw InvalidReconciliacaoException("Dimensão inválida ${linhas}x$colunas")
            return Matriz(linhas, colunas, DoubleArray(linhas * colunas))
        }

        fun identidade(n: Int): Matriz {
            if (n <= 0) throw InvalidReconciliacaoException("Dimensão inválida ${n}x$n")
            return Matriz(n, n, DoubleArray(n * n).also { d -> for (i in 0 until n) d[i * n + i] = 1.0 })
        }

        fun de(valores: Array<DoubleArray>): Matriz {
            val l = valores.size
            val c = valores.firstOrNull()?.size ?: 0
            if (l == 0 || c == 0 || valores.any { it.size != c }) throw InvalidReconciliacaoException("Linhas de tamanhos diferentes")
            return Matriz(l, c, DoubleArray(l * c) { valores[it / c][it % c] })
        }
    }

    /** Construtor incremental usado para montar A e Σ sem expor o array interno. */
    class Construtor(val linhas: Int, val colunas: Int) {
        private val d = DoubleArray(linhas * colunas)
        operator fun set(i: Int, j: Int, v: Double) { d[i * colunas + j] = v }
        operator fun get(i: Int, j: Int): Double = d[i * colunas + j]
        fun construir(): Matriz = Matriz(linhas, colunas, d.copyOf())
    }
}
