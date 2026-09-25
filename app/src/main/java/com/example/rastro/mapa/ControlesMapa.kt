package com.example.rastro.mapa

import android.os.Bundle
import android.os.SystemClock
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.example.rastro.R
import com.example.rastro.service.EstadoRastro
import com.example.rastro.sos.ApresentacaoSos
import com.example.rastro.sos.EstadoLocalizacao

/** Interações do mapa principal; configurações residem na própria tela. */
class ControlesMapa(private val activity: AppCompatActivity, saved: Bundle?) {
    private var estado = EstadoRastro()
    private var statusMapa = "Carregando mapa…"
    private val prefs = PreferenciasMapa(activity)
    private var textoDetalhes: TextView? = null
    private var dialogo: androidx.appcompat.app.AlertDialog? = null
    val mapa = MapaRastro(activity, activity.findViewById(R.id.map_host), saved) { statusMapa = it; atualizarDetalhes() }
    fun atualizar(novo: EstadoRastro) {
        estado = novo
        mapa.atualizar(novo, prefs.raio(novo.ultimoSos?.mensagem?.id))
        atualizarDetalhes()
    }
    fun detalhes() {
        if (dialogo?.isShowing == true) return
        val dp = activity.resources.displayMetrics.density
        textoDetalhes = TextView(activity).apply { setPadding((20*dp).toInt(), (8*dp).toInt(), (20*dp).toInt(), (16*dp).toInt()); setTextIsSelectable(true); textSize = 14f }
        val scroll = ScrollView(activity).apply { addView(textoDetalhes) }
        atualizarDetalhes()
        dialogo = MaterialAlertDialogBuilder(activity).setTitle(R.string.map_details).setView(scroll)
            .setPositiveButton("Fechar", null).setNeutralButton(R.string.view_sos) { _, _ -> mapa.verSos() }.create()
        dialogo?.setOnDismissListener { textoDetalhes = null; dialogo = null }
        dialogo?.show()
    }
    private fun atualizarDetalhes() {
        val posicao = estado.posicaoLocal
        val condicao = if (estado.estadoLocalizacao == EstadoLocalizacao.RECENTE) "Posição local recente" else "Última posição conhecida"
        textoDetalhes?.text = "${estado.status}\n${estado.sos}\n\n${estado.localizacaoStatus}\n" +
            (posicao?.let { "$condicao · ${it.idade(SystemClock.elapsedRealtime()) / 1000} s\n" } ?: "") +
            ApresentacaoSos.posicao(posicao?.posicao) + "\n\nÚltimo SOS recebido\n" + ApresentacaoSos.resumo(estado.ultimoSos) +
            "\n\n$statusMapa\n${mapa.offline.mensagem}\n\n" +
            listOf(R.string.map_legend_local, R.string.map_legend_sos, R.string.map_legend_range, R.string.map_accuracy_explanation, R.string.map_attribution).joinToString("\n") { activity.getString(it) } +
            (prefs.raio(estado.ultimoSos?.mensagem?.id)?.let { "\n\nAlcance estimado: $it m\n${prefs.perfil()?.descricao}\nCobertura irregular; centro histórico do SOS. Não comprova conexão atual." } ?: "")
    }
    fun executar(acao: String) = mapa.quandoPronto {
        when (acao) {
            "preparar" -> {
                val bounds = mapa.areaVisivel() ?: return@quandoPronto
                val pares = listOf(8.0 to 14.0, 10.0 to 16.0, 12.0 to 16.0)
                MaterialAlertDialogBuilder(activity).setTitle(R.string.prepare_map)
                    .setItems(arrayOf("Zoom 8–14 · visão geral", "Zoom 10–16 · padrão", "Zoom 12–16 · detalhe local")) { _, i ->
                        mapa.offline.preparar(bounds, pares[i].first, pares[i].second, activity.resources.displayMetrics.density)
                        detalhes()
                    }.setNegativeButton("Cancelar", null).show()
            }
            "regiao" -> mapa.verRegiao()
            "recarregar" -> mapa.carregarBase()
        }
    }
    private val observadorOffline: () -> Unit = { atualizarDetalhes() }
    fun observarOffline() { mapa.offline.observar(observadorOffline) }
    fun pararObservarOffline() { mapa.offline.removerObservador(observadorOffline) }
    fun destruir() { dialogo?.dismiss(); mapa.destruir() }
}
