package com.example.rastro.mapa

import kotlin.math.*

object GeometriaMapa {
    /** Pontos geodésicos; raio em metros, jamais pixels ou qualidade de enlace. */
    fun circulo(latitude: Double, longitude: Double, raioMetros: Double): List<Pair<Double, Double>> {
        require(latitude in -90.0..90.0 && longitude in -180.0..180.0 && raioMetros.isFinite() && raioMetros >= 0)
        val lat = Math.toRadians(latitude)
        val lon = Math.toRadians(longitude)
        val d = raioMetros / 6_371_008.8
        return (0..96).map { i ->
            val angulo = 2 * PI * i / 96
            val y = asin((sin(lat) * cos(d) + cos(lat) * sin(d) * cos(angulo)).coerceIn(-1.0, 1.0))
            val x = lon + atan2(sin(angulo) * sin(d) * cos(lat), cos(d) - sin(lat) * sin(y))
            Math.toDegrees(y) to ((Math.toDegrees(x) + 540) % 360 - 180)
        }
    }
}
