package com.example.rastro

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.*
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.rastro.chat.*
import com.example.rastro.service.EstadoRastro
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.zxing.BarcodeFormat
import com.journeyapps.barcodescanner.BarcodeEncoder
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class ChatActivity : TelaRastroActivity() {
    private val chat by lazy { ChatRuntime.get(this) }
    private var visible = false
    private var peer: String? = null
    private var peerName = ""
    private var generation = 0
    private val rows = Rows()
    private val listener: () -> Unit = { refresh() }
    private val permission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { if(it) scan() else message(getString(R.string.chat_camera_denied)) }
    private val scanner = registerForActivityResult(ScanContract()) { result ->
        result.contents?.let { qr -> chat.inspectQr(qr) { response ->
            if(!isFinishing && !isDestroyed) response.fold(::confirmContact) { message("QR Code inválido ou incompatível. Confira se é um contato Rastro.") }
        } }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        setContentView(R.layout.activity_chat); aplicarInsets(R.id.chat_root)
        peer = savedInstanceState?.getString("peer"); peerName = savedInstanceState?.getString("peerName").orEmpty()
        findViewById<RecyclerView>(R.id.chat_list).apply { layoutManager = LinearLayoutManager(this@ChatActivity); adapter = rows }
        findViewById<View>(R.id.chat_back).setOnClickListener { back() }
        onBackPressedDispatcher.addCallback(this,object : OnBackPressedCallback(true) { override fun handleOnBackPressed() { back() } })
        findViewById<View>(R.id.chat_my_qr).setOnClickListener { showQr() }
        findViewById<View>(R.id.chat_scan).setOnClickListener {
            if(!packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)) message("Este aparelho não possui câmera disponível.")
            else if(ContextCompat.checkSelfPermission(this,Manifest.permission.CAMERA)==PackageManager.PERMISSION_GRANTED) scan()
            else permission.launch(Manifest.permission.CAMERA)
        }
        findViewById<View>(R.id.chat_send).setOnClickListener {
            val selected = peer ?: return@setOnClickListener
            val input = findViewById<EditText>(R.id.chat_text)
            val text = input.text.toString()
            findViewById<View>(R.id.chat_send).isEnabled = false
            chat.send(selected,text) { result ->
                if(isDestroyed) return@send
                findViewById<View>(R.id.chat_send).isEnabled = true
                result.fold({ if(peer == selected && input.text.toString() == text) input.text.clear(); refresh() }, { message(it.message ?: "Não foi possível salvar a mensagem") })
            }
        }
    }
    override fun onStart() { super.onStart(); visible = true; chat.listen(listener) }
    override fun onStop() { visible = false; generation++; chat.unlisten(listener); super.onStop() }
    override fun onSaveInstanceState(outState: Bundle) { outState.putString("peer",peer); outState.putString("peerName",peerName); super.onSaveInstanceState(outState) }
    override fun renderizar(estado: EstadoRastro) { if(visible) refresh() }
    private fun back() { if(peer != null) { peer = null; findViewById<EditText>(R.id.chat_text).text.clear(); refresh() } else finish() }
    private fun scan() { scanner.launch(ScanOptions().setDesiredBarcodeFormats(ScanOptions.QR_CODE).setPrompt("Leia o QR Code no aparelho do contato").setBeepEnabled(false).setOrientationLocked(false)) }
    private fun message(text: String) { if(!isFinishing && !isDestroyed) MaterialAlertDialogBuilder(this).setMessage(text).setPositiveButton("OK",null).show() }
    private fun confirmContact(c: ChatContact) {
        MaterialAlertDialogBuilder(this).setTitle("Cadastrar contato verificado?")
            .setMessage("${c.name}\nIdentidade: ${c.id.chunked(8).joinToString(" ")}\n\nConfirme presencialmente que este código pertence à pessoa esperada. Ela também precisa ler seu QR Code. Uma chave diferente cria outro contato, sem substituir o anterior.")
            .setNegativeButton("Cancelar",null).setPositiveButton("Confirmar cadastro") { _, _ ->
                chat.saveContact(c) { result -> result.fold({ refresh() }, { message(it.message ?: "Não foi possível cadastrar") }) }
            }.show()
    }
    private fun editAlias(entry: ChatContactEntry) {
        val input = EditText(this).apply { setText(entry.alias); hint = "Apelido local"; inputType = android.text.InputType.TYPE_CLASS_TEXT; importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO }
        MaterialAlertDialogBuilder(this).setTitle("Contato verificado")
            .setMessage("Nome no QR: ${entry.contact.name}\nCadastrado: ${time(entry.added)}\nÚltima mensagem autenticada: ${if(entry.lastActivity == 0L) "ainda não recebida" else time(entry.lastActivity)}\nIdentidade: ${entry.contact.id}\nO apelido não altera a identidade do contato.")
            .setView(input).setNegativeButton("Fechar",null).setPositiveButton("Salvar apelido") { _,_ ->
                chat.alias(entry.contact.id,input.text.toString().trim()) { result -> result.fold({ refresh() },{ message(it.message ?: "Não foi possível salvar o apelido") }) }
            }.show()
    }
    private fun showQr() {
        chat.ownQr { result -> result.fold({ qr ->
            Thread {
                val bitmap = runCatching { BarcodeEncoder().encodeBitmap(qr,BarcodeFormat.QR_CODE,720,720) }
                runOnUiThread {
                    if(!visible) return@runOnUiThread
                    bitmap.fold({ image ->
                        val view = ImageView(this).apply { setImageBitmap(image); adjustViewBounds = true; contentDescription = "QR Code público deste aparelho"; setPadding(16,16,16,16) }
                        MaterialAlertDialogBuilder(this).setTitle("Meu QR Code")
                            .setMessage("Peça ao contato para ler este código. Depois leia o código dele para concluir o cadastro mútuo.")
                            .setView(view).setPositiveButton("Fechar",null).show()
                    }, { message("Não foi possível gerar o QR Code") })
                }
            }.start()
        }, { message("Identidade indisponível. As chaves existentes não foram substituídas.") }) }
    }
    private fun refresh() {
        if(!visible) return
        val token = ++generation
        val selected = peer
        findViewById<TextView>(R.id.chat_title).text = if(selected == null) getString(R.string.chat_title) else peerName
        findViewById<View>(R.id.chat_contact_actions).visibility = if(selected == null) View.VISIBLE else View.GONE
        findViewById<View>(R.id.chat_composer).visibility = if(selected == null) View.GONE else View.VISIBLE
        findViewById<TextView>(R.id.chat_state).text = if(estadoAtual.ativo) "Rastro ativo · ${estadoAtual.quantidadeConectados} enlace(s)\n${chat.notice}" else "Ative o Rastro na tela inicial para transmitir. Mensagens ficam salvas na caixa de saída."
        if(selected == null) chat.entries { result ->
            if(!visible || generation != token) return@entries
            result.fold({ contacts ->
                empty(contacts.isEmpty(),getString(R.string.chat_empty))
                rows.show(contacts.map { entry ->
                    val c = entry.contact
                    Row("${entry.alias}\n${c.id.take(16)} · Verificado por QR Code\nToque para conversar; segure para detalhes.",
                        { peer = c.id; peerName = entry.alias; refresh() }, { editAlias(entry) })
                })
            }, { message("Não foi possível abrir os contatos protegidos") })
        } else chat.lines(selected) { result ->
            if(!visible || generation != token) return@lines
            result.fold({ lines ->
                empty(lines.isEmpty(),"Nenhuma mensagem. Confirme que o contato também cadastrou seu QR Code.")
                rows.show(lines.map { line ->
                    val state = when(line.status) { "DELIVERED" -> "Entrega confirmada"; "WAITING" -> "Aguardando conexão"; "AWAITING_ACK" -> "Aguardando confirmação do destinatário"; else -> "Recebida" }
                    Row("${if(line.outgoing) "Você" else peerName} · ${time(line.created)}\n${line.text}\n$state${if(!line.outgoing) " · recebida ${time(line.received)}" else ""}")
                })
            }, { message("Não foi possível abrir a conversa protegida") })
        }
    }
    private fun empty(show: Boolean, text: String) { findViewById<TextView>(R.id.chat_empty).apply { visibility = if(show) View.VISIBLE else View.GONE; this.text = text } }
    private fun time(value: Long) = DateTimeFormatter.ofPattern("dd/MM HH:mm:ss").withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(value))
    private data class Row(val text: String, val click: (() -> Unit)? = null, val longClick: (() -> Unit)? = null)
    private class Rows : RecyclerView.Adapter<Rows.Holder>() {
        private var rows = emptyList<Row>()
        class Holder(val text: TextView) : RecyclerView.ViewHolder(text)
        fun show(value: List<Row>) { if(rows.map { it.text } == value.map { it.text }) return; rows = value; notifyDataSetChanged() }
        override fun getItemCount() = rows.size
        override fun onCreateViewHolder(parent: ViewGroup, type: Int) = Holder(TextView(parent.context).apply { layoutParams = RecyclerView.LayoutParams(-1,-2); setPadding(12,24,12,24); textSize = 16f })
        override fun onBindViewHolder(holder: Holder, position: Int) { val row = rows[position]; holder.text.text = row.text; holder.text.setOnLongClickListener(if(row.longClick == null) null else View.OnLongClickListener { row.longClick.invoke(); true }); holder.text.setTextIsSelectable(row.click == null); holder.text.setOnClickListener(if(row.click == null) null else View.OnClickListener { row.click.invoke() }) }
    }
}