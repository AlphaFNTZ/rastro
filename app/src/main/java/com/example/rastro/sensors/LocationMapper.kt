package com.example.rastro.sensors

import android.location.Location
import com.example.rastro.Posicao
import com.example.rastro.s2.CovarianciaGnss

sealed class ResultadoGnss {
    data class Disponivel(val posicao: Posicao, val covariancia: CovarianciaGnss) : ResultadoGnss()
    data class Indisponivel(val motivo: String) : ResultadoGnss()
}

object LocationMapper {
    fun converter(location: Location): ResultadoGnss {
        if (!location.hasAccuracy() || !location.hasSpeed() || !location.hasSpeedAccuracy()) {
            return ResultadoGnss.Indisponivel("GNSS sem acurácia horizontal, velocidade ou acurácia de velocidade")
        }
        return try {
            val posicao = Posicao(location.latitude, location.longitude, location.time,
                location.accuracy.toDouble(), location.speed.toDouble(), location.speedAccuracyMetersPerSecond.toDouble())
            ResultadoGnss.Disponivel(posicao, CovarianciaGnss.de(posicao))
        } catch (e: com.example.rastro.InvalidSensorReadException) {
            ResultadoGnss.Indisponivel(e.message ?: "GNSS inválido")
        }
    }
}
