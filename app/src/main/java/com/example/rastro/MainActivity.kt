package com.example.rastro

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.example.rastro.mapa.ControlesMapa
import com.example.rastro.service.EstadoRastro
import com.example.rastro.service.RastroService

class MainActivity : TelaRastroActivity() {
    private lateinit var controlesMapa: ControlesMapa
    private var acaoPendente: String? = null
    private val permissoes = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        if (permissoesObrigatorias().all(::permitida)) iniciarRastro()
        else findViewById<TextView>(R.id.status).text = "Autorize os dispositivos próximos para iniciar o Rastro"
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        aplicarInsets(R.id.main)
        controlesMapa = ControlesMapa(this, savedInstanceState)
        NavegacaoInferior.configurar(this, AbaNavegacao.HOME)
        findViewById<View>(R.id.btn_configuracao).setOnClickListener { startActivity(Intent(this, ConfiguracaoActivity::class.java)) }
        findViewById<View>(R.id.btn_ativar_rastro).setOnClickListener {
            val faltantes = (permissoesObrigatorias() + listOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION) +
                if (Build.VERSION.SDK_INT >= 33) listOf(Manifest.permission.POST_NOTIFICATIONS) else emptyList()).distinct().filterNot(::permitida)
            if (faltantes.isEmpty()) iniciarRastro() else permissoes.launch(faltantes.toTypedArray())
        }
        findViewById<View>(R.id.btn_desativar_rastro).setOnClickListener { servico?.pararRastro() }
        findViewById<View>(R.id.btn_enviar_sos).setOnClickListener { servico?.enviarSos() }
        findViewById<View>(R.id.fab_minha_localizacao).setOnClickListener { controlesMapa.mapa.minhaLocalizacao() }
        findViewById<View>(R.id.fab_ver_sos).setOnClickListener { controlesMapa.mapa.verSos() }
        findViewById<View>(R.id.card_alerta_sos).setOnClickListener { controlesMapa.detalhes() }
        findViewById<View>(R.id.map_details).setOnClickListener { controlesMapa.detalhes() }
        findViewById<View>(R.id.status).setOnClickListener { controlesMapa.detalhes() }
        acaoPendente = savedInstanceState?.getString(EXTRA_MAPA) ?: intent.getStringExtra(EXTRA_MAPA)
        executarAcaoMapa()
        renderizar(estadoAtual)
    }
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        acaoPendente = intent.getStringExtra(EXTRA_MAPA)
        executarAcaoMapa()
    }
    private fun executarAcaoMapa() {
        val acao = acaoPendente ?: return
        controlesMapa.executar(acao)
        acaoPendente = null
        intent.removeExtra(EXTRA_MAPA)
    }
    override fun onStart() { super.onStart(); controlesMapa.mapa.onStart(); controlesMapa.observarOffline() }
    override fun onResume() { super.onResume(); controlesMapa.mapa.onResume(); controlesMapa.atualizar(estadoAtual) }
    override fun onPause() { controlesMapa.mapa.onPause(); super.onPause() }
    override fun onStop() { controlesMapa.pararObservarOffline(); controlesMapa.mapa.onStop(); super.onStop() }
    override fun onLowMemory() { super.onLowMemory(); controlesMapa.mapa.onLowMemory() }
    override fun onSaveInstanceState(out: Bundle) { controlesMapa.mapa.salvar(out); out.putString(EXTRA_MAPA, acaoPendente); super.onSaveInstanceState(out) }
    override fun onDestroy() { controlesMapa.destruir(); super.onDestroy() }

    override fun renderizar(estado: EstadoRastro) {
        findViewById<TextView>(R.id.texto_boas_vindas).text = if (estado.nomeLocal.isBlank()) getString(R.string.greeting_initial) else getString(R.string.greeting_format, estado.nomeLocal)
        findViewById<View>(R.id.btn_ativar_rastro).visibility = if (estado.ativo) View.GONE else View.VISIBLE
        findViewById<View>(R.id.layout_acoes_ativado).visibility = if (estado.ativo) View.VISIBLE else View.GONE
        findViewById<View>(R.id.btn_enviar_sos).isEnabled = estado.ativo && estado.quantidadeConectados > 0
        findViewById<TextView>(R.id.status).text = if (estado.ativo) {
            estado.status + if (estado.sos != "Nenhum SOS enviado") "\n${estado.sos}" else ""
        } else getString(R.string.status_idle)
        findViewById<View>(R.id.fab_minha_localizacao).isEnabled = estado.posicaoLocal != null
        findViewById<View>(R.id.fab_ver_sos).isEnabled = estado.ultimoSos?.mensagem?.localizacao != null
        findViewById<View>(R.id.card_alerta_sos).visibility = if (estado.ultimoSos == null) View.GONE else View.VISIBLE
        findViewById<TextView>(R.id.tv_ultimo_sos_resumo).text = estado.ultimoSos?.mensagem?.let {
            "Último SOS recebido · ${it.nomeOrigem ?: it.origem.take(8)}\nToque para horários, posição e detalhes"
        }.orEmpty()
        controlesMapa.atualizar(estado)
    }
    private fun iniciarRastro() = ContextCompat.startForegroundService(this, Intent(this, RastroService::class.java).setAction(RastroService.ACTION_START))
    private fun permitida(p: String) = ContextCompat.checkSelfPermission(this, p) == PackageManager.PERMISSION_GRANTED
    private fun permissoesObrigatorias(): List<String> = buildList {
        if (Build.VERSION.SDK_INT >= 31) addAll(listOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_ADVERTISE))
        if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.NEARBY_WIFI_DEVICES)
        if (Build.VERSION.SDK_INT in 29..31) add(Manifest.permission.ACCESS_FINE_LOCATION)
        if (Build.VERSION.SDK_INT < 29) add(Manifest.permission.ACCESS_COARSE_LOCATION)
        if (Build.VERSION.SDK_INT >= 37) add("android.permission.ACCESS_LOCAL_NETWORK")
    }
    companion object { const val EXTRA_MAPA = "rastro.acao_mapa" }
}
