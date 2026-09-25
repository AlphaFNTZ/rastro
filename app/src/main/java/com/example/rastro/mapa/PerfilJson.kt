package com.example.rastro.mapa

import org.json.JSONObject

object PerfilJson {
    const val MAX_BYTES = 65_536
    fun ler(texto: String): PerfilAlcance {
        require(texto.toByteArray(Charsets.UTF_8).size <= MAX_BYTES)
        val j = JSONObject(texto)
        require(j.getInt("versao") == 1 && j.getBoolean("enlaceDireto")) { "O perfil exige medições de enlace direto" }
        val a = j.getJSONArray("amostras")
        val perfil = PerfilAlcance(j.getString("nome"), j.getString("modeloA"), j.getString("modeloB"),
            j.getString("ambiente"), j.getString("terreno"), j.getString("obstaculos"), j.getString("orientacao"),
            j.getString("evidencia"), j.getLong("medidoEmEpochMs"), j.getLong("timeoutAckMs"),
            j.getDouble("taxaMinima"), j.getInt("repeticoesMinimas"), (0 until a.length()).map {
                val m = a.getJSONObject(it)
                AmostraAlcance(m.getDouble("distanciaMetros"), if (m.isNull("latenciaAckMs")) null else m.getLong("latenciaAckMs"))
            })
        require(perfil.raioMetros != null) { "Nenhuma distância satisfez o critério de comunicação utilizável" }
        return perfil
    }
}
