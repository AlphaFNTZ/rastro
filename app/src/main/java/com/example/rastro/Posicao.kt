package com.example.rastro

/**
 * Posicao — leitura de posicionamento GNSS.
 *
 * Corresponde ao "Fix" do glossário da disciplina (seção 13 do guia GAT108): uma
 * medição de posicionamento com instante, coordenadas, velocidade e as acurácias
 * informadas pelo próprio sensor.
 *
 * Estritamente imutável: todos os campos são `val`, a validação ocorre uma única
 * vez, em [init], e não existe nenhum método que altere o estado depois de
 * construída. Uma instância válida nunca se torna inválida.
 *
 * @property latitude em graus decimais (WGS84), no intervalo [-90, 90].
 * @property longitude em graus decimais (WGS84), no intervalo [-180, 180].
 * @property timestampMs instante da leitura, em milissegundos desde a época Unix. Deve ser positivo.
 * @property acuraciaHorizontalM raio horizontal com confiança de 68% informado pelo GNSS,
 *   em metros (não é sigma de cada eixo). Nunca negativo.
 * @property velocidadeMps velocidade instantânea informada pelo sensor GNSS, em m/s. Nunca negativa (é uma magnitude).
 * @property acuraciaVelocidadeMps acurácia da velocidade informada pelo sensor GNSS, em m/s. Nunca negativa.
 *
 * A acurácia aqui é justamente o que alimentará a covariância da Semana 2
 * (capacidade 2, Reconciler) — por isso ela é campo do domínio e não um detalhe
 * de infraestrutura descartável.
 *
 * @throws InvalidSensorReadException se qualquer invariante física for violada.
 */
data class Posicao(
    val latitude: Double,
    val longitude: Double,
    val timestampMs: Long,
    val acuraciaHorizontalM: Double,
    val velocidadeMps: Double,
    val acuraciaVelocidadeMps: Double
) {
    init {
        exigirFinito(latitude, "latitude")
        exigirFinito(longitude, "longitude")
        exigirFinito(acuraciaHorizontalM, "acuraciaHorizontalM")
        exigirFinito(velocidadeMps, "velocidadeMps")
        exigirFinito(acuraciaVelocidadeMps, "acuraciaVelocidadeMps")

        if (latitude < LATITUDE_MIN || latitude > LATITUDE_MAX) {
            throw InvalidSensorReadException(
                "latitude fora do intervalo [$LATITUDE_MIN, $LATITUDE_MAX]: $latitude"
            )
        }
        if (longitude < LONGITUDE_MIN || longitude > LONGITUDE_MAX) {
            throw InvalidSensorReadException(
                "longitude fora do intervalo [$LONGITUDE_MIN, $LONGITUDE_MAX]: $longitude"
            )
        }
        if (timestampMs <= 0L) {
            throw InvalidSensorReadException("timestampMs deve ser positivo, recebido: $timestampMs")
        }
        if (acuraciaHorizontalM < 0.0) {
            throw InvalidSensorReadException(
                "acuraciaHorizontalM não pode ser negativa: $acuraciaHorizontalM"
            )
        }
        if (velocidadeMps < 0.0) {
            throw InvalidSensorReadException("velocidadeMps não pode ser negativa: $velocidadeMps")
        }
        if (acuraciaVelocidadeMps < 0.0) {
            throw InvalidSensorReadException(
                "acuraciaVelocidadeMps não pode ser negativa: $acuraciaVelocidadeMps"
            )
        }
    }

    /**
     * Double.NaN comparado com `<` ou `>` sempre devolve `false` — uma checagem de
     * intervalo sozinha deixaria NaN passar como "válido" silenciosamente. Por isso
     * essa checagem de finitude roda antes de qualquer comparação de faixa.
     */
    private fun exigirFinito(valor: Double, nomeDoCampo: String) {
        if (!valor.isFinite()) {
            throw InvalidSensorReadException(
                "$nomeDoCampo precisa ser finito (não pode ser NaN ou infinito): $valor"
            )
        }
    }

    companion object {
        const val LATITUDE_MIN = -90.0
        const val LATITUDE_MAX = 90.0
        const val LONGITUDE_MIN = -180.0
        const val LONGITUDE_MAX = 180.0
    }
}
