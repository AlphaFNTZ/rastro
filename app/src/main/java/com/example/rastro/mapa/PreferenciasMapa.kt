package com.example.rastro.mapa

import android.content.Context

/** Consentimento de alcance associado a um SOS e ao perfil importado. */
class PreferenciasMapa(context: Context) {
    private val prefs = context.getSharedPreferences("rastro-mapa", Context.MODE_PRIVATE)
    fun perfil(): PerfilAlcance? = prefs.getString("perfil", null)?.let { runCatching { PerfilJson.ler(it) }.getOrNull() }
    fun importar(texto: String): PerfilAlcance {
        val perfil = PerfilJson.ler(texto)
        prefs.edit().putString("perfil", texto).remove("alcance-sos").apply()
        return perfil
    }
    fun confirmar(sosId: String) { prefs.edit().putString("alcance-sos", sosId).apply() }
    fun desativar() { prefs.edit().remove("alcance-sos").apply() }
    fun habilitado(sosId: String?): Boolean = sosId != null && prefs.getString("alcance-sos", null) == sosId && perfil() != null
    fun raio(sosId: String?): Double? = perfil()?.raioMetros?.takeIf { habilitado(sosId) }
}
