package com.example.rastro

import kotlin.math.abs
import kotlin.math.sqrt

/**
 * IMURead — leitura da unidade inercial: aceleração nos três eixos e atitude.
 *
 * Corresponde à grandeza "Aceleração (3 eixos, m/s²) e Atitude (inclinação)" da
 * proposta do Grupo Rastro (docs/PROPOSAL.md, seção 2), usada tanto para a
 * detecção determinística de queda (pico de aceleração seguido de atitude
 * horizontal) quanto para a degradação graciosa do RuleEngine na capacidade 8.
 *
 * Estritamente imutável, mesma disciplina de [Posicao]: validação única em [init].
 *
 * @property timestampMs instante da leitura, em milissegundos desde a época Unix. Deve ser positivo.
 * @property acelXMps2 aceleração no eixo X, em m/s².
 * @property acelYMps2 aceleração no eixo Y, em m/s².
 * @property acelZMps2 aceleração no eixo Z, em m/s².
 * @property inclinacaoGraus ângulo entre o eixo principal do aparelho e a vertical (gravidade),
 *   em graus, no intervalo [0, 180]. 0° = aparelho na vertical; 90° = aparelho na
 *   horizontal — o sinal que o ThresholdDetector usa para suspeitar de imobilidade
 *   após uma queda; 180° = invertido.
 *
 *   Isto é uma simplificação deliberada: atitude completa (pitch/roll/yaw, ou um
 *   quatérnio) carregaria mais informação, mas o que a detecção de queda por
 *   limiar de fato consome é "o aparelho ficou plano depois do pico de aceleração
 *   ou não". Se a dupla decidir usar atitude completa mais adiante, registrar em
 *   docs/DECISIONS.md — essa troca muda o contrato de IMURead, não só a lógica
 *   do ThresholdDetector.
 *
 * @throws InvalidSensorReadException se qualquer invariante física for violada.
 */
data class IMURead(
    val timestampMs: Long,
    val acelXMps2: Double,
    val acelYMps2: Double,
    val acelZMps2: Double,
    val inclinacaoGraus: Double
) {
    init {
        exigirFinito(acelXMps2, "acelXMps2")
        exigirFinito(acelYMps2, "acelYMps2")
        exigirFinito(acelZMps2, "acelZMps2")
        exigirFinito(inclinacaoGraus, "inclinacaoGraus")

        if (timestampMs <= 0L) {
            throw InvalidSensorReadException("timestampMs deve ser positivo, recebido: $timestampMs")
        }
        exigirMagnitudePlausivel(acelXMps2, "acelXMps2")
        exigirMagnitudePlausivel(acelYMps2, "acelYMps2")
        exigirMagnitudePlausivel(acelZMps2, "acelZMps2")

        if (inclinacaoGraus < INCLINACAO_MIN_GRAUS || inclinacaoGraus > INCLINACAO_MAX_GRAUS) {
            throw InvalidSensorReadException(
                "inclinacaoGraus fora do intervalo [$INCLINACAO_MIN_GRAUS, $INCLINACAO_MAX_GRAUS]: $inclinacaoGraus"
            )
        }
    }

    private fun exigirFinito(valor: Double, nomeDoCampo: String) {
        if (!valor.isFinite()) {
            throw InvalidSensorReadException(
                "$nomeDoCampo precisa ser finito (não pode ser NaN ou infinito): $valor"
            )
        }
    }

    private fun exigirMagnitudePlausivel(valor: Double, nomeDoCampo: String) {
        if (abs(valor) > ACELERACAO_MAX_MPS2) {
            throw InvalidSensorReadException(
                "$nomeDoCampo excede a magnitude fisicamente plausível de " +
                    "±$ACELERACAO_MAX_MPS2 m/s²: $valor"
            )
        }
    }

    /** Módulo do vetor de aceleração nos três eixos, em m/s². Insumo direto do ThresholdDetector. */
    fun magnitude(): Double = sqrt(acelXMps2 * acelXMps2 + acelYMps2 * acelYMps2 + acelZMps2 * acelZMps2)

    companion object {
        /**
         * ~20,4 g — limite superior de sanidade para acelerômetros de smartphone.
         * É um valor de bom-senso, não uma especificação de datasheet: ajuste
         * conforme a calibração real dos aparelhos da dupla e registre a mudança
         * em docs/DECISIONS.md.
         */
        const val ACELERACAO_MAX_MPS2 = 200.0
        const val INCLINACAO_MIN_GRAUS = 0.0
        const val INCLINACAO_MAX_GRAUS = 180.0
    }
}
