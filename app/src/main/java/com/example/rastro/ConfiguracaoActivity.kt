package com.example.rastro

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.example.rastro.mapa.*
import com.example.rastro.network.AnuncioNo
import com.example.rastro.service.EstadoRastro
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch
import org.maplibre.android.MapLibre
import java.io.ByteArrayOutputStream

class ConfiguracaoActivity : TelaRastroActivity() {
    private lateinit var offline: MapasOffline
    private lateinit var preferencias: PreferenciasMapa
    private var atualizandoSwitch = false
    private var diagnostico: TextView? = null
    private var dialogoDiagnostico: androidx.appcompat.app.AlertDialog? = null
    private val permissaoLocalizacao = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { servico?.iniciarGnss() }
    private val seletorPerfil = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) Thread {
            val resultado = runCatching {
                val conteudo = contentResolver.openInputStream(uri)?.use { input ->
                    val out = ByteArrayOutputStream()
                    val buffer = ByteArray(4096)
                    while (true) {
                        val lidos = input.read(buffer)
                        if (lidos < 0) break
                        require(out.size() + lidos <= PerfilJson.MAX_BYTES) { "Perfil excede 64 KiB" }
                        out.write(buffer, 0, lidos)
                    }
                    out.toString("UTF-8")
                } ?: error("Não foi possível ler o arquivo")
                PerfilJson.ler(conteudo) to conteudo
            }
            runOnUiThread {
                if (!isDestroyed) resultado.fold({ (perfil, conteudo) ->
                    preferencias.importar(conteudo)
                    renderizar(estadoAtual)
                    aviso("Perfil importado", perfil.descricao + "\nRaio medido: ${perfil.raioMetros} m. Habilite somente em condições correspondentes.")
                }, { aviso("Perfil inválido", it.message ?: "Verifique o arquivo") })
            }
        }.start()
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_configuracao)
        aplicarInsets(R.id.layout_configuracao)
        MapLibre.getInstance(applicationContext)
        offline = MapasOffline.obter(this)
        preferencias = PreferenciasMapa(this)
        botao(R.id.btn_voltar) { finish() }
        botao(R.id.card_nome_usuario) {
            val input = EditText(this).apply { setSingleLine(true); setText(estadoAtual.nomeLocal); setSelection(text.length) }
            val dialog = MaterialAlertDialogBuilder(this).setTitle(R.string.item_nome_usuario).setView(input)
                .setPositiveButton("Salvar", null).setNegativeButton("Cancelar", null).create()
            dialog.setOnShowListener {
                dialog.getButton(-1).setOnClickListener {
                    runCatching { AnuncioNo.normalizarNome(input.text.toString()) }.fold(
                        { servico?.alterarNome(it); dialog.dismiss() }, { input.error = it.message })
                }
            }
            dialog.show()
        }
        botao(R.id.card_tempo_atualizacao) {
            val segundos = longArrayOf(15, 30, 60, 120, 300, 600)
            MaterialAlertDialogBuilder(this).setTitle("Validade da posição recente")
                .setSingleChoiceItems(segundos.map { "$it segundos" }.toTypedArray(), segundos.indexOf(estadoAtual.limitePosicaoRecenteMs / 1000)) { d, i ->
                    servico?.configurarIdadePosicao(segundos[i] * 1000); d.dismiss()
                }.setNegativeButton("Cancelar", null).show()
        }
        botao(R.id.card_importar_perfil) {
            val perfil = preferencias.perfil()
            if (perfil == null) importarPerfil()
            else MaterialAlertDialogBuilder(this).setTitle(perfil.nome).setMessage(perfil.descricao)
                .setPositiveButton("Substituir perfil") { _, _ -> importarPerfil() }.setNegativeButton("Fechar", null).show()
        }
        findViewById<MaterialSwitch>(R.id.switch_alcance_estimado).setOnCheckedChangeListener { _, checked ->
            if (atualizandoSwitch) return@setOnCheckedChangeListener
            if (!checked) { preferencias.desativar(); return@setOnCheckedChangeListener }
            atualizarAlcance()
            val perfil = preferencias.perfil() ?: return@setOnCheckedChangeListener
            val sos = estadoAtual.ultimoSos?.mensagem ?: return@setOnCheckedChangeListener
            MaterialAlertDialogBuilder(this).setTitle("Confirmar condições do ensaio")
                .setMessage("${perfil.descricao}\n\nRaio medido: ${perfil.raioMetros} m. Os modelos e condições correspondem a este SOS?\nO círculo usa a posição histórica do SOS. Cobertura irregular; não comprova conexão direta ou atual.")
                .setPositiveButton("Condições correspondem") { _, _ ->
                    if (estadoAtual.ultimoSos?.mensagem?.id == sos.id) preferencias.confirmar(sos.id)
                    atualizarAlcance()
                }.setNegativeButton("Cancelar", null).show()
        }
        botao(R.id.card_preparar_mapa) {
            MaterialAlertDialogBuilder(this).setTitle(R.string.prepare_map)
                .setMessage("Será usada a área enquadrada na tela principal. ${getString(R.string.offline_instructions)}")
                .setPositiveButton("Escolher zoom no mapa") { _, _ -> abrirMapa("preparar") }.setNegativeButton("Cancelar", null).show()
        }
        botao(R.id.card_ver_regiao) {
            if (offline.definicao == null) aviso("Mapa offline", "Nenhuma região foi preparada.") else abrirMapa("regiao")
        }
        botao(R.id.card_recarregar_mapa) { abrirMapa("recarregar") }
        botao(R.id.card_remover_mapa) {
            MaterialAlertDialogBuilder(this).setTitle(R.string.delete_map)
                .setMessage("Remove somente o mapa baixado. Para recuperá-lo, prepare a região novamente com internet.")
                .setPositiveButton("Remover") { _, _ -> offline.remover() }.setNegativeButton("Cancelar", null).show()
        }
        botao(R.id.pause_map) { if (offline.baixando) offline.pausar() else offline.retomar() }
        botao(R.id.open_diagnostics) { abrirDiagnostico() }
        botao(R.id.open_licenses) {
            aviso("Licenças e atribuição", getString(R.string.map_attribution) + "\n\nMapLibre Native — BSD-2-Clause\n\n" +
                assets.open("licenses/PlusJakartaSans-OFL.txt").bufferedReader().use { it.readText() })
        }
        findViewById<TextView>(R.id.version_label).text = "Versão ${packageManager.getPackageInfo(packageName, 0).versionName}"
        renderizar(estadoAtual)
    }
    private val observadorOffline: () -> Unit = { atualizarOffline() }
    override fun onStart() { super.onStart(); offline.observar(observadorOffline); atualizarOffline() }
    override fun onStop() { offline.removerObservador(observadorOffline); super.onStop() }
    override fun onDestroy() { dialogoDiagnostico?.dismiss(); super.onDestroy() }
    private fun importarPerfil() = seletorPerfil.launch(arrayOf("application/json", "text/plain", "application/octet-stream"))
    private fun abrirMapa(acao: String) {
        startActivity(Intent(this, MainActivity::class.java).putExtra(MainActivity.EXTRA_MAPA, acao)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP))
        finish()
    }
    override fun renderizar(estado: EstadoRastro) {
        findViewById<View>(R.id.card_nome_usuario).contentDescription = "Nome do usuário: ${estado.nomeLocal}"
        findViewById<View>(R.id.card_tempo_atualizacao).contentDescription = "Tempo de atualização: posição recente até ${estado.limitePosicaoRecenteMs / 1000} segundos"
        atualizarAlcance(); atualizarDiagnostico()
    }
    private fun atualizarAlcance() {
        val p = preferencias.perfil()
        val sos = estadoAtual.ultimoSos?.mensagem
        atualizandoSwitch = true
        findViewById<MaterialSwitch>(R.id.switch_alcance_estimado).apply {
            isEnabled = p != null && sos?.localizacao != null
            isChecked = preferencias.habilitado(sos?.id) && isEnabled
            contentDescription = if (isEnabled) "Alcance estimado: ${p?.nome}" else "Importe um perfil e receba um SOS com localização para habilitar o alcance estimado"
        }
        atualizandoSwitch = false
    }
    private fun atualizarOffline() {
        findViewById<TextView>(R.id.tv_status_offline).text = if (offline.definicao != null || offline.baixando) offline.mensagem else ""
        findViewById<View>(R.id.pause_map).visibility = if (offline.definicao != null && !offline.completo) View.VISIBLE else View.GONE
        findViewById<TextView>(R.id.pause_map).text = if (offline.baixando) "Pausar download" else "Retomar download"
    }
    private fun abrirDiagnostico() {
        diagnostico = TextView(this).apply { setPadding(32, 16, 32, 16); setTextIsSelectable(true); textSize = 14f }
        atualizarDiagnostico()
        dialogoDiagnostico = MaterialAlertDialogBuilder(this).setTitle(R.string.diagnostics)
            .setView(ScrollView(this).apply { addView(diagnostico) })
            .setPositiveButton("Fechar", null).setNeutralButton("IMU", null).setNegativeButton("Localização", null).create()
        dialogoDiagnostico?.setOnShowListener {
            atualizarDiagnostico()
            dialogoDiagnostico?.getButton(-3)?.setOnClickListener { servico?.alternarSensores() }
            dialogoDiagnostico?.getButton(-2)?.setOnClickListener {
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) servico?.iniciarGnss()
                else permissaoLocalizacao.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
            }
        }
        dialogoDiagnostico?.setOnDismissListener { diagnostico = null; dialogoDiagnostico = null }
        dialogoDiagnostico?.show()
    }
    private fun atualizarDiagnostico() {
        diagnostico?.text = "${estadoAtual.status}\n\n${estadoAtual.sensor}\n\n${estadoAtual.localizacaoStatus}\n${estadoAtual.gnss}\n\n${getString(R.string.nearby_transport_explanation)}"
        dialogoDiagnostico?.getButton(-3)?.isEnabled = estadoAtual.ativo
        dialogoDiagnostico?.getButton(-2)?.isEnabled = estadoAtual.ativo
    }
    private fun botao(id: Int, acao: () -> Unit) { findViewById<View>(id).setOnClickListener { acao() } }
    private fun aviso(titulo: String, texto: String) {
        val conteudo = TextView(this).apply { setPadding(32, 16, 32, 16); text = texto; setTextIsSelectable(true) }
        MaterialAlertDialogBuilder(this).setTitle(titulo).setView(ScrollView(this).apply { addView(conteudo) }).setPositiveButton("Fechar", null).show()
    }
}
