package com.example.rastro.mapa

import org.junit.Assert.*
import org.junit.Test

class PerfilAlcanceTest {
    private fun perfil(amostras: List<AmostraAlcance>) = PerfilAlcance("Ensaio", "A", "B", "aberto", "plano", "nenhum",
        "vertical", "caderno de medições", 1000, 1000, 0.9, 10, amostras)
    @Test fun `sem medicoes suficientes nao ha raio`() {
        assertNull(perfil(List(5) { AmostraAlcance(10.0, 1) } + List(5) { AmostraAlcance(20.0, 1) }).raioMetros)
    }
    @Test fun `raio para na primeira distancia que falha criterio`() {
        val p = perfil(List(10) { AmostraAlcance(10.0, 300) } + List(10) { AmostraAlcance(20.0, null) } + List(10) { AmostraAlcance(30.0, 300) })
        assertEquals(10.0, p.raioMetros!!, 0.0)
    }
    @Test fun `ack fora do intervalo conta como falha`() {
        assertNull(perfil(List(10) { AmostraAlcance(10.0, 1001) }).raioMetros)
        assertEquals(20.0, perfil(List(9) { AmostraAlcance(20.0, 1000) } + AmostraAlcance(20.0, null)).raioMetros!!, 0.0)
    }
    @Test fun `circulo usa metros e fecha contorno`() {
        val pontos = GeometriaMapa.circulo(0.0, 0.0, 1000.0)
        assertEquals(97, pontos.size)
        assertEquals(0.0089932, pontos.first().first, 0.000001)
        assertEquals(pontos.first().first, pontos.last().first, 1e-10)
        assertEquals(pontos.first().second, pontos.last().second, 1e-10)
    }
}
