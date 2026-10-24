package com.example.rastro.s2

import com.example.rastro.InvalidSensorReadException
import com.example.rastro.Posicao
import kotlin.math.ln

/** Matriz de observação em eixos locais métricos [leste, norte, velocidade escalar].
 * Hipóteses: erro horizontal gaussiano isotrópico; correlações desconhecidas tomadas
 * como zero. Não aplicar diretamente a latitude/longitude em graus.
 */
class CovarianciaGnss private constructor(val varianciaHorizontalM2: Double, val varianciaVelocidadeM2s2: Double) {
    fun matriz(): Array<DoubleArray> = arrayOf(
        doubleArrayOf(varianciaHorizontalM2, 0.0, 0.0),
        doubleArrayOf(0.0, varianciaHorizontalM2, 0.0),
        doubleArrayOf(0.0, 0.0, varianciaVelocidadeM2s2)
    )

    companion object {
        fun de(posicao: Posicao): CovarianciaGnss {
            // Location.accuracy é raio de cobertura 68%, não sigma de cada eixo.
            val horizontal = posicao.acuraciaHorizontalM * posicao.acuraciaHorizontalM / (-2.0 * ln(0.32))
            // Aproximação convencional de 68% escalar por um desvio padrão.
            val velocidade = posicao.acuraciaVelocidadeMps * posicao.acuraciaVelocidadeMps
            if (!horizontal.isFinite() || !velocidade.isFinite()) {
                throw InvalidSensorReadException("Acurácia excede a faixa numérica da covariância")
            }
            return CovarianciaGnss(horizontal, velocidade)
        }
    }
}

/** A qualidade categórica da API Android não é uma variância física. */
enum class QualidadeImu { SEM_CONTATO, NAO_CONFIAVEL, BAIXA, MEDIA, ALTA, DESCONHECIDA }

sealed class IncertezaImu {
    data class Indisponivel(val motivo: String) : IncertezaImu()

    /** Somente para uma fonte que realmente reporte sigmas por eixo, em m/s².
     * SensorEvent.accuracy NÃO pode ser passado como sigma.
     */
    class Reportada(val fonte: String, sigmaX: Double, sigmaY: Double, sigmaZ: Double) : IncertezaImu() {
        private val variancias: DoubleArray
        init {
            if (fonte.isBlank()) throw InvalidSensorReadException("Informe a fonte da incerteza IMU")
            val sigmas = doubleArrayOf(sigmaX, sigmaY, sigmaZ)
            if (sigmas.any { !it.isFinite() || it < 0 || !(it * it).isFinite() }) {
                throw InvalidSensorReadException("Sigmas IMU inválidos")
            }
            variancias = sigmas.map { it * it }.toDoubleArray()
        }
        /** Modelo diagonal: ausência de correlações é uma hipótese explícita. */
        fun matriz(): Array<DoubleArray> = Array(3) { linha ->
            DoubleArray(3) { coluna -> if (linha == coluna) variancias[linha] else 0.0 }
        }
    }
}

/** Leitura bruta imutável. Inclinação não é inventada a partir de uma aceleração
 * dinâmica; IMURead da S1 permanece intacta para a futura fusão de atitude.
 */
data class LeituraImu(
    val timestampMonotonicoNs: Long,
    val acelX: Double,
    val acelY: Double,
    val acelZ: Double,
    val qualidade: QualidadeImu,
    val incerteza: IncertezaImu = IncertezaImu.Indisponivel("SensorEvent não reporta sigma por eixo")
) {
    init {
        if (timestampMonotonicoNs <= 0 || listOf(acelX, acelY, acelZ).any { !it.isFinite() }) {
            throw InvalidSensorReadException("Leitura IMU não finita ou timestamp inválido")
        }
    }
}
