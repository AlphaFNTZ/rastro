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
    private lateinit var paginas: PaginasPrincipais
    private lateinit var dispositivos: PaginaDispositivos
    private lateinit var historico: PaginaHistorico
    private val voltarInicio = object : androidx.activity.OnBackPressedCallback(false) {
        override fun handleOnBackPressed() { selecionarAba(AbaNavegacao.HOME) }
    }
    private var acaoPendente: String? = null
    private val permissoes = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        if (permissoesObrigatorias().all(::permitida)) iniciarRastro()
        else findViewById<TextView>(R.id.status).text = "Autorize os dispositivos próximos para iniciar o Rastro"
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_paginas)
        aplicarInsets(R.id.paginas_root)
        paginas = findViewById(R.id.paginas)
        val layouts = listOf(R.layout.activity_main, R.layout.activity_dispositivos, R.layout.activity_historico)
        val views = layouts.map { layoutInflater.inflate(it, paginas, false) }
        views.forEach { root ->
            root.findViewById<View>(R.id.bottom_navigation_bar)?.let { (it.parent as android.view.ViewGroup).removeView(it) }
        }
        paginas.offscreenPageLimit = 2
        paginas.adapter = object : androidx.viewpager.widget.PagerAdapter() {
            override fun getCount() = views.size
            override fun isViewFromObject(view: View, item: Any) = view === item
            override fun instantiateItem(container: android.view.ViewGroup, position: Int): Any =
                views[position].also { if(it.parent == null) container.addView(it) }
            override fun destroyItem(container: android.view.ViewGroup, position: Int, item: Any) {
                container.removeView(item as View)
            }
            override fun getPageTitle(position: Int): CharSequence =
                getString(listOf(R.string.nav_home,R.string.nav_devices,R.string.nav_history)[position])
        }
        // Attach the retained pages before map/control initialization uses findViewById.
        views.forEach { paginas.addView(it) }
        dispositivos = PaginaDispositivos(this, views[1])
        historico = PaginaHistorico(this, views[2])
        paginas.addOnPageChangeListener(object : androidx.viewpager.widget.ViewPager.SimpleOnPageChangeListener() {
            override fun onPageSelected(position: Int) {
                NavegacaoInferior.configurar(this@MainActivity, AbaNavegacao.entries[position])
                voltarInicio.isEnabled = position != 0
                currentFocus?.let {
                    (getSystemService(INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager)
                        .hideSoftInputFromWindow(it.windowToken, 0)
                    it.clearFocus()
                }
            }
        })
        onBackPressedDispatcher.addCallback(this, voltarInicio)
        paginas.setCurrentItem((savedInstanceState?.getInt(EXTRA_ABA) ?: intent.getIntExtra(EXTRA_ABA,0)).coerceIn(0,2),false)
        voltarInicio.isEnabled = paginas.currentItem != 0

        controlesMapa = ControlesMapa(this, savedInstanceState)
        NavegacaoInferior.configurar(this, AbaNavegacao.entries[paginas.currentItem])
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
        selecionarAba(AbaNavegacao.entries[intent.getIntExtra(EXTRA_ABA,0).coerceIn(0,2)])
        acaoPendente = intent.getStringExtra(EXTRA_MAPA)
        executarAcaoMapa()
    }
    fun selecionarAba(aba: AbaNavegacao) { paginas.setCurrentItem(aba.ordinal, true) }
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
    override fun onSaveInstanceState(out: Bundle) { controlesMapa.mapa.salvar(out); out.putInt(EXTRA_ABA, paginas.currentItem); out.putString(EXTRA_MAPA, acaoPendente); super.onSaveInstanceState(out) }
    override fun onDestroy() { controlesMapa.destruir(); super.onDestroy() }

    override fun renderizar(estado: EstadoRastro) {
        dispositivos.renderizar(estado)
        historico.renderizar(estado)
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
    companion object { const val EXTRA_MAPA = "rastro.acao_mapa"; const val EXTRA_ABA = "rastro.aba" }
}
