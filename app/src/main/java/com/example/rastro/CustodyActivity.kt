package com.example.rastro

import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import com.example.rastro.chat.ChatRuntime
import com.example.rastro.chat.CustodySnapshot
import com.example.rastro.service.EstadoRastro
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.switchmaterial.SwitchMaterial

class CustodyActivity : TelaRastroActivity() {
    private val chat by lazy { ChatRuntime.get(this) }
    private var visible=false
    private var rendering=false
    private var generation=0
    private val listener: () -> Unit = { refresh() }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState); enableEdgeToEdge()
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        setContentView(R.layout.activity_custody); aplicarInsets(R.id.custody_root)
        findViewById<View>(R.id.custody_back).setOnClickListener { finish() }
        findViewById<SwitchMaterial>(R.id.custody_mode).setOnCheckedChangeListener { _, checked ->
            if(!rendering) chat.carrierMode(checked) { result -> result.onFailure { error() }; refresh() }
        }
    }
    override fun onStart() { super.onStart(); visible=true; chat.listen(listener) }
    override fun onStop() { visible=false; generation++; chat.unlisten(listener); super.onStop() }
    override fun renderizar(estado: EstadoRastro) { if(visible) refresh() }
    private fun refresh() {
        if(!visible) return
        val token=++generation
        chat.custodySnapshot { result -> if(visible && token==generation) result.fold(::show) { error() } }
    }
    private fun error() { if(!isFinishing && !isDestroyed) MaterialAlertDialogBuilder(this).setMessage("Não foi possível atualizar a caixa de correspondência.").setPositiveButton("OK",null).show() }
    private fun show(snapshot: CustodySnapshot) {
        rendering=true; findViewById<SwitchMaterial>(R.id.custody_mode).isChecked=snapshot.enabled; rendering=false
        findViewById<TextView>(R.id.custody_status).text="${snapshot.active}/128 pacotes · ${snapshot.bytes/1024} KiB de 2.048 KiB\n" +
            if(estadoAtual.ativo) "Rastro ativo. Encontros e confirmação dependem dos aparelhos disponíveis." else "Ative o Rastro na tela inicial para procurar destinatários."
        val items=findViewById<LinearLayout>(R.id.custody_items); items.removeAllViews()
        if(snapshot.packets.isEmpty()) items.addView(TextView(this).apply { setText(R.string.custody_empty) })
        snapshot.packets.forEach { packet ->
            items.addView(TextView(this).apply {
                setPadding(0,24,0,8)
                text="Pacote ${packet.id.take(8)}\n${packet.origin.take(12)} → ${packet.destination.take(12)}\n" +
                    (if(packet.state=="RECEIPT") "Entregue; levando confirmação à origem" else "Aguardando encontro direto com o destinatário") +
                    "\nValidade restante: ${(packet.remaining/3600000).coerceAtLeast(0)} h"
            })
            items.addView(MaterialButton(this).apply {
                setText(R.string.custody_remove)
                setOnClickListener {
                    MaterialAlertDialogBuilder(this@CustodyActivity).setTitle("Remover esta cópia?")
                        .setMessage("A mensagem ou confirmação deixará de ser transportada por este aparelho. Isso não apaga o histórico do remetente ou destinatário e pode impedir a entrega desta cópia.")
                        .setNegativeButton("Manter",null).setPositiveButton("Remover") { _,_ -> chat.removeCustody(packet.id) { result -> result.onFailure { error() }; refresh() } }.show()
                }
            })
        }
    }
}