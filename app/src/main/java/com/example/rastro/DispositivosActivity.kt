package com.example.rastro

import android.os.Bundle
import android.view.*
import android.widget.*
import androidx.activity.enableEdgeToEdge
import androidx.core.content.ContextCompat
import androidx.core.widget.doAfterTextChanged
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.example.rastro.network.EstadoVizinho
import com.example.rastro.network.QualidadeEnlace
import com.example.rastro.service.EstadoRastro
import java.time.LocalDate

enum class TipoEnlaceUi { DIRETO, INDIRETO, BAIXO }
data class DispositivoVisual(val nome: String, val detalhes: String, val completo: String, val tipo: TipoEnlaceUi)

class DispositivosActivity : TelaRastroActivity() {
    private var listaCompleta: List<DispositivoVisual> = emptyList()
    private val adapter = DispositivosAdapter()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_dispositivos)
        aplicarInsets(R.id.layout_dispositivos)
        NavegacaoInferior.configurar(this, AbaNavegacao.DISPOSITIVOS)
        findViewById<RecyclerView>(R.id.rv_dispositivos).apply { layoutManager = LinearLayoutManager(this@DispositivosActivity); adapter = this@DispositivosActivity.adapter }
        findViewById<EditText>(R.id.et_buscar_dispositivos).doAfterTextChanged { filtrar(it?.toString().orEmpty()) }
        renderizar(estadoAtual)
    }
    override fun renderizar(estado: EstadoRastro) {
        findViewById<TextView>(R.id.tv_data_dispositivos).text = HistoricoActivity.rotuloDia(LocalDate.now())
        listaCompleta = estado.vizinhos.map { v ->
            val situacao = when (v.estado) { EstadoVizinho.DESCOBERTO -> "Descoberto"; EstadoVizinho.CONECTANDO -> "Conectando"; EstadoVizinho.CONECTADO -> "Conectado" }
            val qualidade = when (v.qualidade) { QualidadeEnlace.DESCONHECIDA -> "Não informada"; QualidadeEnlace.BAIXA -> "Baixa"; QualidadeEnlace.MEDIA -> "Média"; QualidadeEnlace.ALTA -> "Alta" }
            DispositivoVisual(v.identidade.nome, "Direto · Nearby automático · $qualidade",
                "${v.identidade.rotulo}\nUUID: ${v.identidade.id}\n$situacao\nAlcance: direto\nQualidade: $qualidade\n\n${getString(R.string.nearby_transport_explanation)}",
                if (v.qualidade == QualidadeEnlace.BAIXA) TipoEnlaceUi.BAIXO else TipoEnlaceUi.DIRETO)
        } + estado.rotasIndiretas.map { r ->
            DispositivoVisual(r.identidade.nome, "Indireto · Nearby automático · ${r.saltos} saltos",
                "${r.identidade.rotulo}\nUUID: ${r.identidade.id}\nAlcançável via ${r.via.rotulo}\n${r.saltos} saltos\nQualidade do enlace remoto não informada.\n\n${getString(R.string.nearby_transport_explanation)}\nA rota representa um caminho observado recentemente.",
                TipoEnlaceUi.INDIRETO)
        }
        filtrar(findViewById<EditText>(R.id.et_buscar_dispositivos).text.toString())
    }
    private fun filtrar(query: String) {
        val itens = listaCompleta.filter { it.nome.contains(query, true) || it.completo.contains(query, true) }
        adapter.submitList(itens)
        findViewById<TextView>(R.id.tv_dispositivos_vazio).apply {
            visibility = if (itens.isEmpty()) View.VISIBLE else View.GONE
            setText(if (query.isBlank()) R.string.empty_dispositivos else R.string.no_search_results)
        }
    }
    class DispositivosAdapter : RecyclerView.Adapter<DispositivosAdapter.Holder>() {
        private var itens: List<DispositivoVisual> = emptyList()
        fun submitList(novos: List<DispositivoVisual>) { if (itens != novos) { itens = novos.toList(); notifyDataSetChanged() } }
        override fun getItemCount() = itens.size
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = Holder(LayoutInflater.from(parent.context).inflate(R.layout.item_dispositivo, parent, false))
        override fun onBindViewHolder(holder: Holder, position: Int) = holder.bind(itens[position])
        class Holder(view: View) : RecyclerView.ViewHolder(view) {
            fun bind(item: DispositivoVisual) {
                itemView.findViewById<TextView>(R.id.tv_nome_dispositivo).text = item.nome
                itemView.findViewById<TextView>(R.id.tv_detalhes_dispositivo).text = item.detalhes
                val (bg, fg) = when (item.tipo) {
                    TipoEnlaceUi.DIRETO -> R.drawable.bg_badge_device_direct to R.color.device_badge_direct_fg
                    TipoEnlaceUi.INDIRETO -> R.drawable.bg_badge_device_indirect to R.color.device_badge_indirect_fg
                    TipoEnlaceUi.BAIXO -> R.drawable.bg_badge_device_low to R.color.device_badge_low_fg
                }
                itemView.findViewById<View>(R.id.badge_dispositivo).setBackgroundResource(bg)
                itemView.findViewById<ImageView>(R.id.iv_icone_dispositivo).setColorFilter(ContextCompat.getColor(itemView.context, fg))
                itemView.isFocusable = true
                itemView.contentDescription = item.completo
                itemView.setOnClickListener {
                    val texto = TextView(itemView.context).apply { text = item.completo; setTextIsSelectable(true); setPadding(32, 16, 32, 16) }
                    MaterialAlertDialogBuilder(itemView.context).setTitle(item.nome).setView(ScrollView(itemView.context).apply { addView(texto) }).setPositiveButton("Fechar", null).show()
                }
            }
        }
    }
}
