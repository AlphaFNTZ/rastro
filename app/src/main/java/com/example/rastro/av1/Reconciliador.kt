package com.example.rastro.av1

import com.example.rastro.InvalidReconciliacaoException
import kotlin.math.cos
import kotlin.math.sin

/**
 * Projeção local equiretangular em torno de uma origem: leste = R·Δλ·cos φ₀, norte = R·Δφ.
 * Para janelas de poucos quilômetros o erro de escala é desprezível frente aos metros
 * de incerteza do GNSS. R é o raio médio da Terra (6 371 008,8 m).
 */
class ProjecaoLocal(val latitudeOrigem: Double, val longitudeOrigem: Double) {
    private val cosLat = cos(Math.toRadians(latitudeOrigem))

    /** (leste, norte) em metros. */
    fun paraLocal(latitude: Double, longitude: Double): DoubleArray = doubleArrayOf(
        RAIO_TERRA_M * Math.toRadians(longitude - longitudeOrigem) * cosLat,
        RAIO_TERRA_M * Math.toRadians(latitude - latitudeOrigem)
    )

    /** Inversa de [paraLocal]: (latitude, longitude) em graus. */
    fun paraGeografica(lesteM: Double, norteM: Double): DoubleArray = doubleArrayOf(
        latitudeOrigem + Math.toDegrees(norteM / RAIO_TERRA_M),
        longitudeOrigem + Math.toDegrees(lesteM / (RAIO_TERRA_M * cosLat))
    )

    companion object { const val RAIO_TERRA_M = 6_371_008.8 }
}

/**
 * Medidas de uma janela, já em eixos locais. Para cada fixo k, quatro medidas, nesta ordem:
 *   y[4k]   = pE(k)  posição leste (m)
 *   y[4k+1] = pN(k)  posição norte (m)
 *   y[4k+2] = vE(k)  componente leste da velocidade (m/s)
 *   y[4k+3] = vN(k)  componente norte da velocidade (m/s)
 * [covariancia] é Σ, a covariância dessas medidas (bloco diagonal por fixo).
 * [temposS] são os instantes de cada fixo em segundos, relativos ao primeiro.
 */
class MedidasJanela internal constructor(
    val fixos: List<RegistroFix>,
    val projecao: ProjecaoLocal,
    val temposS: DoubleArray,
    val y: DoubleArray,
    val covariancia: Matriz
) {
    val quantidadeFixos: Int get() = fixos.size
}

/** Como montar Σ a partir do que o sensor informa. Nenhum valor é arbitrado. */
object ModeloIncerteza {

    /**
     * Converte fixos em medidas locais e Σ.
     *
     * Posição: variância por eixo σp² = r68² / (−2·ln 0,32), igual à covariância da S2.
     * Velocidade com rumo e acurácia de rumo: (vE, vN) = s·(sen θ, cos θ), com s = módulo,
     * θ = rumo em radianos, σs = acurácia da velocidade, σθ = acurácia do rumo (rad).
     * Propagação de primeira ordem, Σv = J·diag(σs², σθ²)·Jᵀ com J = ∂(vE, vN)/∂(s, θ):
     *   Σv_EE = sen²θ·σs² + s²·cos²θ·σθ²
     *   Σv_NN = cos²θ·σs² + s²·sen²θ·σθ²
     *   Σv_EN = sen θ·cos θ·(σs² − s²·σθ²)
     * Sem rumo (o Android não informa rumo parado) ou sem acurácia de rumo: a direção é
     * desconhecida; com direção uniforme, cada componente tem média 0 e variância
     * σs² + s²/2. É a hipótese mais fraca compatível com o que foi medido.
     *
     * @param fatorCovariancia multiplica Σ inteira; 1 = acurácia como reportada. O ensaio
     *   estático mede a razão de otimismo e pode usar fator = razão² para inflar Σ.
     */
    fun medidas(fixos: List<RegistroFix>, fatorCovariancia: Double = 1.0): MedidasJanela {
        if (fixos.size < 2) throw InvalidReconciliacaoException("Janela precisa de pelo menos 2 fixos, recebeu ${fixos.size}")
        if (!(fatorCovariancia > 0.0) || !fatorCovariancia.isFinite()) throw InvalidReconciliacaoException("fatorCovariancia inválido: $fatorCovariancia")
        fixos.forEachIndexed { i, f ->
            if (!f.reconciliavel) throw InvalidReconciliacaoException("Fixo $i sem acurácia, velocidade ou acurácia de velocidade positivas")
        }
        val projecao = ProjecaoLocal(fixos.first().latitude, fixos.first().longitude)
        val t0 = fixos.first().tempoMonotonicoNs
        val tempos = DoubleArray(fixos.size) { (fixos[it].tempoMonotonicoNs - t0) / 1e9 }
        val n = 4 * fixos.size
        val y = DoubleArray(n)
        val s = Matriz.Construtor(n, n)
        fixos.forEachIndexed { k, f ->
            val p = projecao.paraLocal(f.latitude, f.longitude)
            y[4 * k] = p[0]
            y[4 * k + 1] = p[1]
            val varPos = Estatistica.varianciaPorEixo(f.acuraciaHorizontalM!!) * fatorCovariancia
            s[4 * k, 4 * k] = varPos
            s[4 * k + 1, 4 * k + 1] = varPos
            val vel = f.velocidadeMps!!
            val varVel = f.acuraciaVelocidadeMps!!.let { it * it }
            val rumo = f.rumoGraus
            val accRumo = f.acuraciaRumoGraus
            if (rumo != null && accRumo != null && accRumo > 0.0) {
                val th = Math.toRadians(rumo)
                val sTh = sin(th)
                val cTh = cos(th)
                val varTh = Math.toRadians(accRumo).let { it * it }
                y[4 * k + 2] = vel * sTh
                y[4 * k + 3] = vel * cTh
                s[4 * k + 2, 4 * k + 2] = (sTh * sTh * varVel + vel * vel * cTh * cTh * varTh) * fatorCovariancia
                s[4 * k + 3, 4 * k + 3] = (cTh * cTh * varVel + vel * vel * sTh * sTh * varTh) * fatorCovariancia
                val cov = sTh * cTh * (varVel - vel * vel * varTh) * fatorCovariancia
                s[4 * k + 2, 4 * k + 3] = cov
                s[4 * k + 3, 4 * k + 2] = cov
            } else {
                y[4 * k + 2] = 0.0
                y[4 * k + 3] = 0.0
                val v = (varVel + vel * vel / 2.0) * fatorCovariancia
                s[4 * k + 2, 4 * k + 2] = v
                s[4 * k + 3, 4 * k + 3] = v
            }
        }
        for (k in 1 until fixos.size) {
            if (tempos[k] <= tempos[k - 1]) throw InvalidReconciliacaoException("Δt não positivo entre os fixos ${k - 1} e $k")
        }
        return MedidasJanela(fixos, projecao, tempos, y, s.construir())
    }
}

/**
 * Resultado de uma reconciliação por mínimos quadrados ponderados.
 *
 * @property residuoAntes r = A·y, resíduo das restrições com as medidas brutas (m).
 * @property residuoDepois A·ŷ, resíduo com as medidas reconciliadas (≈ 0 para restrição linear).
 * @property reconciliadas ŷ = y − Σ·Aᵀ·V⁻¹·r.
 * @property ajustes a = y − ŷ.
 * @property estatisticaGlobal γ = rᵀ·V⁻¹·r, que segue χ²(m) sem erro grosseiro.
 * @property zMedidas |aⱼ| / √Wⱼⱼ, com W = Σ·Aᵀ·V⁻¹·A·Σ a covariância dos ajustes (teste de medida).
 */
class ResultadoReconciliacao(
    val medidas: MedidasJanela,
    val restricoes: Matriz,
    val residuoAntes: DoubleArray,
    val residuoDepois: DoubleArray,
    val reconciliadas: DoubleArray,
    val ajustes: DoubleArray,
    val estatisticaGlobal: Double,
    val zMedidas: DoubleArray
) {
    val grausLiberdade: Int get() = residuoAntes.size
    val rmsResiduoAntesM: Double get() = Estatistica.rms(residuoAntes)
    val rmsResiduoDepoisM: Double get() = Estatistica.rms(residuoDepois)
    val maxResiduoAntesM: Double get() = residuoAntes.maxOf { kotlin.math.abs(it) }

    /** Posição reconciliada do fixo k em (leste, norte), metros. */
    fun posicaoReconciliada(k: Int): DoubleArray = doubleArrayOf(reconciliadas[4 * k], reconciliadas[4 * k + 1])
    fun posicaoMedida(k: Int): DoubleArray = doubleArrayOf(medidas.y[4 * k], medidas.y[4 * k + 1])

    /** Posição reconciliada do fixo k expressa no referencial local de [outra] projeção. */
    fun posicaoReconciliadaEm(k: Int, outra: ProjecaoLocal): DoubleArray {
        val p = posicaoReconciliada(k)
        val g = medidas.projecao.paraGeografica(p[0], p[1])
        return outra.paraLocal(g[0], g[1])
    }
}

/**
 * Reconciliação de dados sob a restrição cinemática entre fixos consecutivos.
 *
 * Restrição física (por eixo a ∈ {leste, norte}, para k = 0 … N−2):
 *   pₐ(k+1) − pₐ(k) − ½·(vₐ(k) + vₐ(k+1))·Δtₖ = 0
 * onde pₐ é a posição medida no eixo, vₐ a componente da velocidade medida no eixo e
 * Δtₖ = tₖ₊₁ − tₖ em SEGUNDOS (relógio monotônico). É a integração trapezoidal da
 * velocidade: o deslocamento entre dois fixos tem que bater com a velocidade média.
 * Como Δt é conhecido com precisão de nanossegundos, a restrição é LINEAR nas medidas:
 * A·y = 0, com A de dimensão 2(N−1) × 4N.
 *
 * Solução de mínimos quadrados ponderados com restrição de igualdade:
 *   min (ŷ − y)ᵀ·Σ⁻¹·(ŷ − y)  sujeito a A·ŷ = 0
 *   ⇒ ŷ = y − Σ·Aᵀ·(A·Σ·Aᵀ)⁻¹·A·y
 * A correção de cada medida é proporcional à sua variância: a mais incerta é a que mais
 * se move. Não é preciso inverter Σ, só V = A·Σ·Aᵀ (2(N−1) × 2(N−1)).
 */
object Reconciliador {

    fun restricoes(m: MedidasJanela): Matriz {
        val nFix = m.quantidadeFixos
        val a = Matriz.Construtor(2 * (nFix - 1), 4 * nFix)
        for (k in 0 until nFix - 1) {
            val dt = m.temposS[k + 1] - m.temposS[k]
            for (eixo in 0..1) {
                val i = 2 * k + eixo
                a[i, 4 * (k + 1) + eixo] = 1.0
                a[i, 4 * k + eixo] = -1.0
                a[i, 4 * k + 2 + eixo] = -dt / 2.0
                a[i, 4 * (k + 1) + 2 + eixo] = -dt / 2.0
            }
        }
        return a.construir()
    }

    fun reconciliar(fixos: List<RegistroFix>, fatorCovariancia: Double = 1.0): ResultadoReconciliacao =
        reconciliar(ModeloIncerteza.medidas(fixos, fatorCovariancia))

    fun reconciliar(m: MedidasJanela): ResultadoReconciliacao {
        val a = restricoes(m)
        val at = a.transposta()
        val sigma = m.covariancia
        val r = a * m.y
        val sigmaAt = sigma * at
        val v = a * sigmaAt
        val vInv = v.inversa()
        val lambda = vInv * r
        val ajuste = sigmaAt * lambda
        val yHat = DoubleArray(m.y.size) { m.y[it] - ajuste[it] }
        val rDepois = a * yHat
        val gama = r.indices.sumOf { r[it] * lambda[it] }
        // W = Σ·Aᵀ·V⁻¹·A·Σ ; só a diagonal é usada no teste de medida.
        val w = sigmaAt * (vInv * sigmaAt.transposta())
        val z = DoubleArray(m.y.size) { j ->
            val wjj = w[j, j]
            if (wjj > 1e-18) kotlin.math.abs(ajuste[j]) / kotlin.math.sqrt(wjj) else 0.0
        }
        return ResultadoReconciliacao(m, a, r, rDepois, yHat, ajuste, gama, z)
    }
}
