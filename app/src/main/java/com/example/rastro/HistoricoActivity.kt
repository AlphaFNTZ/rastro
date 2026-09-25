package com.example.rastro

import android.os.Bundle
import android.view.*
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.example.rastro.service.EstadoRastro
import com.example.rastro.sos.ApresentacaoSos
import com.example.rastro.sos.EventoRastro
import com.example.rastro.sos.TipoEvento
import java.time.*
import java.time.format.DateTimeFormatter
import java.util.Locale

class HistoricoActivity : TelaRastroActivity() {
    private val adapter = HistoricoAdapter()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_historico)
        aplicarInsets(R.id.layout_historico)
        NavegacaoInferior.configurar(this, AbaNavegacao.HISTORICO)
        findViewById<RecyclerView>(R.id.rv_historico).apply { layoutManager = LinearLayoutManager(this@HistoricoActivity); adapter = this@HistoricoActivity.adapter }
    }
    override fun renderizar(estado: EstadoRastro) {
        adapter.submitList(estado.eventos.asReversed())
        findViewById<View>(R.id.tv_historico_vazio).visibility = if (estado.eventos.isEmpty()) View.VISIBLE else View.GONE
    }
    class HistoricoAdapter : RecyclerView.Adapter<HistoricoAdapter.Holder>() {
        private var itens: List<EventoRastro> = emptyList()
        fun submitList(novos: List<EventoRastro>) {
            if (itens == novos) return
            itens = novos.toList(); notifyDataSetChanged()
        }
        override fun getItemCount() = itens.size
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = Holder(LayoutInflater.from(parent.context).inflate(R.layout.item_historico, parent, false))
        override fun onBindViewHolder(holder: Holder, position: Int) {
            val evento = itens[position]
            val dia = Instant.ofEpochMilli(evento.registradoEmEpochMs).atZone(ZoneId.systemDefault()).toLocalDate()
            val anterior = itens.getOrNull(position - 1)?.let { Instant.ofEpochMilli(it.registradoEmEpochMs).atZone(ZoneId.systemDefault()).toLocalDate() }
            holder.bind(evento, if (dia != anterior) rotuloDia(dia) else null, position == 0)
        }
        class Holder(view: View) : RecyclerView.ViewHolder(view) {
            fun bind(e: EventoRastro, dia: String?, primeiro: Boolean) {
                itemView.findViewById<TextView>(R.id.tv_grupo_data).apply {
                    visibility = if (dia == null) View.GONE else View.VISIBLE; text = dia
                    setPadding(0, if (primeiro) 0 else (16 * resources.displayMetrics.density).toInt(), 0, (8 * resources.displayMetrics.density).toInt())
                }
                val tipo = when (e.tipo) {
                    TipoEvento.CONEXAO -> "Conexão"; TipoEvento.SERVICO -> "Serviço"; TipoEvento.ENVIO -> "SOS enviado"
                    TipoEvento.RECEBIMENTO -> "SOS recebido"; TipoEvento.ACK -> "ACK"; TipoEvento.TIMEOUT -> "Sem confirmação"
                }
                itemView.findViewById<TextView>(R.id.tv_titulo_evento).text = "$tipo · ${e.descricao}"
                val hora = DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(e.registradoEmEpochMs))
                itemView.findViewById<TextView>(R.id.tv_subtitulo_evento).text = "${e.aparelho.rotulo} · $hora"
                itemView.findViewById<View>(R.id.evento_conteudo).setOnClickListener {
                    val texto = TextView(itemView.context).apply { text = ApresentacaoSos.evento(e); setTextIsSelectable(true); setPadding(32, 16, 32, 16) }
                    MaterialAlertDialogBuilder(itemView.context).setTitle(tipo).setView(android.widget.ScrollView(itemView.context).apply { addView(texto) }).setPositiveButton("Fechar", null).show()
                }
            }
        }
    }
    companion object {
        fun rotuloDia(dia: LocalDate): String {
            val locale = Locale.forLanguageTag("pt-BR")
            val padrao = if (dia.year == LocalDate.now().year) "d 'de' MMMM" else "d 'de' MMMM 'de' yyyy"
            return ((if (dia == LocalDate.now()) "Hoje, " else "") + dia.format(DateTimeFormatter.ofPattern(padrao, locale))).uppercase(locale)
        }
    }
}
