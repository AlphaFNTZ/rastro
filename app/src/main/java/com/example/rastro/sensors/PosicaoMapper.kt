package com.example.rastro.sensors

import android.location.Location
import com.example.rastro.sos.LeituraLocal
import com.example.rastro.sos.LocalizacaoSos

/** A ausência de velocidade não invalida coordenadas para mapa/SOS. */
object PosicaoMapper {
    fun converter(location: Location): LeituraLocal? = runCatching {
        LeituraLocal(LocalizacaoSos(location.latitude, location.longitude,
            if (location.hasAccuracy()) location.accuracy.toDouble() else null, location.time),
            location.elapsedRealtimeNanos / 1_000_000)
    }.getOrNull()
}
