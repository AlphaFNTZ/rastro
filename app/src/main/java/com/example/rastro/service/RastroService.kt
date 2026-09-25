package com.example.rastro.service

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Binder
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.rastro.MainActivity
import com.example.rastro.R
import com.example.rastro.network.MeshRouter
import com.example.rastro.network.Mensagem
import com.example.rastro.network.NearbyTransport
import com.example.rastro.network.TipoMensagem
import com.example.rastro.network.AnuncioNo
import com.example.rastro.network.EstadoVizinho
import com.example.rastro.network.IdentidadeNo
import com.example.rastro.network.RotaIndireta
import com.example.rastro.network.Vizinho
import com.example.rastro.s2.CircularImuBuffer
import com.example.rastro.sensors.ImuSensorSource
import com.example.rastro.sensors.LocationMapper
import com.example.rastro.sensors.ResultadoGnss
import com.example.rastro.sensors.PosicaoMapper
import com.example.rastro.sos.*
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

data class EstadoRastro(
    val ativo: Boolean = false,
    val nomeLocal: String = "",
    val vizinhos: List<Vizinho> = emptyList(),
    val rotasIndiretas: List<RotaIndireta> = emptyList(),
    val status: String = "Serviço parado",
    val sos: String = "Nenhum SOS enviado",
    val sensoresAtivos: Boolean = false,
    val sensor: String = "Leituras inerciais paradas",
    val gnss: String = "Aguardando início da leitura GNSS",
    val eventos: List<EventoRastro> = emptyList(),
    val posicaoLocal: LeituraLocal? = null,
    val estadoLocalizacao: EstadoLocalizacao = EstadoLocalizacao.INDISPONIVEL,
    val localizacaoStatus: String = "Localização ainda não iniciada",
    val limitePosicaoRecenteMs: Long = ConfiguracaoLocalizacao.LIMITE_RECENTE_PADRAO_MS,
    val ultimoSos: SosRecebido? = null
) {
    val quantidadeConectados: Int get() = vizinhos.count { it.estado == EstadoVizinho.CONECTADO }
}

/** Mantém Nearby e sensores ativos com a tela apagada após início explícito pelo usuário. */
class RastroService : Service(), NearbyTransport.Eventos {
    inner class LocalBinder : Binder() {
        fun servico(): RastroService = this@RastroService
    }

    fun interface Observador { fun atualizado(estado: EstadoRastro) }

    private val binder = LocalBinder()
    private val main = Handler(Looper.getMainLooper())
    private val buffer = CircularImuBuffer(500)
    private val historico = HistoricoSessao()
    private val pendentes = ConfirmacoesPendentes(SystemClock::elapsedRealtime)
    private val rotasIndiretas = LinkedHashMap<String, RotaIndireta>()
    private val localizacao by lazy { getSystemService(LocationManager::class.java) }
    private lateinit var noLocal: String
    private lateinit var nomeLocal: String
    private lateinit var transporte: NearbyTransport
    private lateinit var router: MeshRouter
    private lateinit var imu: ImuSensorSource
    private val observadores = LinkedHashSet<Observador>()
    private var estado = EstadoRastro()
    private var consumidor: ScheduledExecutorService? = null
    private var gnssAtivo = false
    private var ultimoProvedor: String? = null
    private var destruindo = false
    private var anunciosPresencaAtivos = false

    private val anunciarPresenca = object : Runnable {
        override fun run() {
            if (!anunciosPresencaAtivos || !estado.ativo) return
            router.originar(Mensagem.presenca(noLocal, nomeLocal))
            expirarRotas()
            atualizarIdadePosicao()
            main.postDelayed(this, INTERVALO_PRESENCA_MS)
        }
    }

    private val gpsListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            val leitura = PosicaoMapper.converter(location)
            val agora = SystemClock.elapsedRealtime()
            if (leitura != null && leitura.tempoMonotonicoMs <= agora &&
                leitura.tempoMonotonicoMs >= (estado.posicaoLocal?.tempoMonotonicoMs ?: 0)) {
                ultimoProvedor = location.provider
                publicar(estado.copy(posicaoLocal = leitura,
                    estadoLocalizacao = leitura.estado(agora, estado.limitePosicaoRecenteMs, gnssAtivo),
                    localizacaoStatus = if (precisaPermitida()) "Localização precisa autorizada" else "Localização aproximada"))
            }
            val texto = when (val resultado = LocationMapper.converter(location)) {
                is ResultadoGnss.Indisponivel -> resultado.motivo
                is ResultadoGnss.Disponivel ->
                    "GNSS: raio 68%% = %.2f m; acurácia velocidade = %.2f m/s\nVariância horizontal = %.3f m²; velocidade = %.3f (m/s)²".format(
                        resultado.posicao.acuraciaHorizontalM,
                        resultado.posicao.acuraciaVelocidadeMps,
                        resultado.covariancia.varianciaHorizontalM2,
                        resultado.covariancia.varianciaVelocidadeM2s2
                    )
            }
            publicar(estado.copy(gnss = texto))
        }
        override fun onProviderDisabled(provider: String) {
            atualizarIdadePosicao()
            publicar(estado.copy(localizacaoStatus = "Provedor $provider desligado; aguardando localização disponível"))
        }
        override fun onProviderEnabled(provider: String) {
            publicar(estado.copy(localizacaoStatus = "Aguardando nova leitura de localização"))
        }
        @Deprecated("Callback necessário na API 26")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
    }

    override fun onCreate() {
        super.onCreate()
        criarCanalNotificacao()
        noLocal = getSharedPreferences("rastro-no", MODE_PRIVATE).let { prefs ->
            prefs.getString("id", null) ?: UUID.randomUUID().toString().also {
                prefs.edit().putString("id", it).apply()
            }
        }
        nomeLocal = getSharedPreferences("rastro-no", MODE_PRIVATE)
            .getString("nome", null)
            ?.let { runCatching { AnuncioNo.normalizarNome(it) }.getOrNull() }
            ?: AnuncioNo.normalizarNome("${Build.MANUFACTURER} ${Build.MODEL}")
        val limite = getSharedPreferences("rastro-no", MODE_PRIVATE).getLong("idade-posicao-ms", ConfiguracaoLocalizacao.LIMITE_RECENTE_PADRAO_MS)
        estado = estado.copy(nomeLocal = nomeLocal, limitePosicaoRecenteMs = limite.takeIf(ConfiguracaoLocalizacao::limiteValido)
            ?: ConfiguracaoLocalizacao.LIMITE_RECENTE_PADRAO_MS)
        imu = ImuSensorSource(this, buffer)
        transporte = NearbyTransport(this, noLocal, nomeLocal, this)
        router = MeshRouter(noLocal, transporte::enviar, ::entregarLocalmente, capacidadeHistorico = 2048, nomeLocal = { nomeLocal })
        registrar("Nó ${noLocal.take(8)} preparado para Nearby P2P_CLUSTER")
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            pararRastro()
            return START_NOT_STICKY
        }
        iniciarForeground()
        transporte.iniciar()
        publicar(estado.copy(ativo = true, status = "Descoberta automática iniciada"))
        iniciarAnunciosPresenca()
        iniciarGnss()
        return START_STICKY
    }

    fun observar(novo: Observador) {
        observadores.add(novo)
        novo.atualizado(estado)
    }

    fun removerObservador(antigo: Observador) { observadores.remove(antigo) }

    fun enviarSos() {
        if (!estado.ativo || transporte.quantidadeConectados() == 0) {
            publicar(estado.copy(sos = "Nenhum vizinho conectado; o SOS não foi enviado"))
            return
        }
        if (pendentes.size >= 16) {
            publicar(estado.copy(sos = "Aguarde as confirmações pendentes"))
            return
        }
        atualizarIdadePosicao()
        val leitura = estado.posicaoLocal
        val mensagem = Mensagem.sos(noLocal, nomeLocal, System.currentTimeMillis(), leitura?.posicao,
            leitura?.idade(SystemClock.elapsedRealtime()), estado.estadoLocalizacao)
        if (!router.originar(mensagem)) {
            publicar(estado.copy(sos = "Falha ao enviar: nenhum enlace disponível"))
            return
        }
        pendentes.registrar(mensagem.id)
        registrar("SOS enviado para a malha", TipoEvento.ENVIO, mensagem = mensagem)
        publicar(estado.copy(sos = "SOS ${mensagem.id.take(8)} enviado; aguardando ACK multi-hop"))
        main.postDelayed({
            if (pendentes.expirar(mensagem.id)) {
                registrar("Sem ACK após 10 segundos", TipoEvento.TIMEOUT,
                    aparelho = IdentidadeNo(mensagem.origem, mensagem.nomeOrigem ?: mensagem.origem.take(8)), mensagem = mensagem)
                publicar(estado.copy(sos = "SOS ${mensagem.id.take(8)} sem confirmação após 10 segundos"))
            }
        }, TIMEOUT_ACK_MS)
    }

    fun alternarSensores() {
        if (estado.sensoresAtivos) pararSensores() else iniciarSensores()
    }

    fun alterarNome(valor: String) {
        val novo = runCatching { AnuncioNo.normalizarNome(valor) }.getOrElse {
            publicar(estado.copy(status = it.message ?: "Nome inválido"))
            return
        }
        if (novo == nomeLocal) return
        nomeLocal = novo
        getSharedPreferences("rastro-no", MODE_PRIVATE).edit().putString("nome", novo).apply()
        transporte.atualizarNome(novo)
        rotasIndiretas.clear()
        registrar("Nome deste nó alterado para $novo")
        publicar(estado.copy(nomeLocal = novo, rotasIndiretas = emptyList(), status = "Nome salvo; conexões sendo refeitas"))
    }

    private fun iniciarSensores() {
        if (!imu.iniciar()) {
            publicar(estado.copy(sensor = "Acelerômetro indisponível"))
            return
        }
        buffer.drenar()
        publicar(estado.copy(sensoresAtivos = true, sensor = "Aguardando leituras IMU"))
        var total = 0L
        var primeiro = 0L
        var ultimo = 0L
        consumidor = Executors.newSingleThreadScheduledExecutor { Thread(it, "rastro-consumidor-imu") }.also {
            it.scheduleWithFixedDelay({
                val lote = buffer.drenar()
                if (lote.isNotEmpty()) {
                    if (primeiro == 0L) primeiro = lote.first().timestampMonotonicoNs
                    ultimo = lote.last().timestampMonotonicoNs
                    total += lote.size
                }
                val hz = if (ultimo > primeiro) (total - 1) * 1e9 / (ultimo - primeiro) else 0.0
                main.post {
                    if (estado.sensoresAtivos) publicar(estado.copy(sensor =
                        "IMU: $total leituras; %.1f Hz; ${buffer.descartadas} descartadas\nQualidade: ${lote.lastOrNull()?.qualidade ?: "aguardando"}; covariância indisponível".format(hz)))
                }
            }, 1, 1, TimeUnit.SECONDS)
        }
    }

    private fun pararSensores() {
        imu.parar()
        consumidor?.shutdownNow()
        consumidor = null
        publicar(estado.copy(sensoresAtivos = false, sensor = "Leituras IMU paradas"))
    }

    @SuppressLint("MissingPermission")
    fun iniciarGnss() {
        if (!estado.ativo) return
        if (!localizacaoPermitida()) {
            if (gnssAtivo) localizacao.removeUpdates(gpsListener)
            gnssAtivo = false
            atualizarIdadePosicao()
            publicar(estado.copy(localizacaoStatus = "Permissão de localização negada; SOS disponível sem nova posição"))
            return
        }
        if (gnssAtivo) localizacao.removeUpdates(gpsListener)
        try {
            atualizarForegroundComLocalizacao()
            val provedores = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
                .filter { it in localizacao.allProviders && (it != LocationManager.GPS_PROVIDER || precisaPermitida()) }
            provedores.forEach { provider ->
                localizacao.requestLocationUpdates(provider, ConfiguracaoLocalizacao.INTERVALO_MS, 0f, gpsListener, Looper.getMainLooper())
            }
            gnssAtivo = provedores.isNotEmpty()
            publicar(estado.copy(localizacaoStatus = when {
                provedores.none { localizacao.isProviderEnabled(it) } -> "Localização desligada ou provedor indisponível; SOS segue sem nova posição"
                !precisaPermitida() -> "Localização aproximada; aguardando leitura"
                else -> "Aguardando localização; prefira um local aberto"
            }))
        } catch (_: SecurityException) {
            gnssAtivo = false
            publicar(estado.copy(localizacaoStatus = "Localização não autorizada; SOS permanece disponível"))
        }
    }

    private fun precisaPermitida() = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    private fun localizacaoPermitida() = precisaPermitida() || ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    fun configurarIdadePosicao(ms: Long) {
        require(ConfiguracaoLocalizacao.limiteValido(ms))
        getSharedPreferences("rastro-no", MODE_PRIVATE).edit().putLong("idade-posicao-ms", ms).apply()
        publicar(estado.copy(limitePosicaoRecenteMs = ms))
        atualizarIdadePosicao()
    }

    private fun atualizarIdadePosicao() {
        val providerAtivo = localizacaoPermitida() && ultimoProvedor?.let {
            it in localizacao.allProviders && localizacao.isProviderEnabled(it)
        } == true
        val situacao = estado.posicaoLocal?.estado(SystemClock.elapsedRealtime(), estado.limitePosicaoRecenteMs,
            estado.ativo && gnssAtivo && providerAtivo) ?: EstadoLocalizacao.INDISPONIVEL
        publicar(estado.copy(estadoLocalizacao = situacao))
    }

    override fun status(texto: String) {
        registrar(texto, TipoEvento.CONEXAO)
        publicar(estado.copy(status = "$texto • ${estado.quantidadeConectados} conectado(s)"))
    }

    override fun vizinhosAlterados(vizinhos: List<Vizinho>) {
        val anteriores = estado.vizinhos.associateBy { it.identidade.id }
        vizinhos.filter { anteriores[it.identidade.id]?.estado != it.estado }.forEach {
            registrar("Estado do enlace: ${it.estado}", TipoEvento.CONEXAO, it.identidade)
        }
        val idsAtuais = vizinhos.map { it.identidade.id }.toSet()
        estado.vizinhos.filter { it.identidade.id !in idsAtuais }.forEach {
            registrar("Aparelho saiu dos vizinhos diretos", TipoEvento.CONEXAO, it.identidade)
        }
        val conectados = vizinhos.count { it.estado == EstadoVizinho.CONECTADO }
        val idsDiretos = vizinhos.filter { it.estado == EstadoVizinho.CONECTADO }.map { it.identidade.id }.toSet()
        rotasIndiretas.entries.removeAll { (id, rota) -> id in idsDiretos || rota.via.id !in idsDiretos }
        publicar(estado.copy(vizinhos = vizinhos,
            rotasIndiretas = rotasIndiretas.values.sortedBy { it.identidade.nome }, status = when {
            conectados > 0 -> "Malha ativa com $conectados conectado(s)"
            vizinhos.isNotEmpty() -> "${vizinhos.size} aparelho(s) encontrado(s); conectando"
            else -> "Procurando aparelhos próximos"
        }))
    }

    override fun recebida(endpointId: String, mensagem: Mensagem) = router.receber(endpointId, mensagem)

    private fun entregarLocalmente(vindoDe: String, mensagem: Mensagem) {
        when (mensagem.tipo) {
            TipoMensagem.SOS_MANUAL -> {
                val via = estado.vizinhos.firstOrNull { it.endpointId == vindoDe }?.identidade
                if (historico.receber(mensagem, System.currentTimeMillis(), via)) {
                    publicar(estado.copy(ultimoSos = historico.ultimoSos,
                        sos = "SOS recebido de ${mensagem.nomeOrigem ?: mensagem.origem.take(8)}; ACK de recebimento enviado"))
                }
            }
            TipoMensagem.CONFIRMACAO -> {
                val tempo = pendentes.confirmar(mensagem.referencia) ?: return
                registrar("ACK recebido em $tempo ms (tempo local). Não confirma atendimento ou resgate.", TipoEvento.ACK,
                    IdentidadeNo(mensagem.origem, mensagem.nomeOrigem ?: "Nó ${mensagem.origem.take(8)}"), mensagem)
                publicar(estado.copy(sos = "SOS ${mensagem.referencia?.take(8)} recebido por outro aplicativo em $tempo ms; atendimento não confirmado"))
            }
            TipoMensagem.PRESENCA -> atualizarRota(vindoDe, mensagem)
        }
    }

    private fun atualizarRota(vindoDe: String, mensagem: Mensagem) {
        if (mensagem.origem == noLocal) return
        val diretos = estado.vizinhos.filter { it.estado == EstadoVizinho.CONECTADO }
        if (diretos.any { it.identidade.id == mensagem.origem }) {
            rotasIndiretas.remove(mensagem.origem)
            return
        }
        val via = diretos.firstOrNull { it.endpointId == vindoDe }?.identidade ?: return
        val nome = mensagem.nomeOrigem ?: return
        val saltos = mensagem.saltosPercorridos + 1
        if (saltos <= 1) return
        val nova = RotaIndireta(IdentidadeNo(mensagem.origem, nome), saltos, via, SystemClock.elapsedRealtime())
        val atual = rotasIndiretas[mensagem.origem]
        if (atual == null || saltos <= atual.saltos || nova.atualizadoEmMs - atual.atualizadoEmMs > VALIDADE_ROTA_MS / 2) {
            rotasIndiretas[mensagem.origem] = nova
            publicar(estado.copy(rotasIndiretas = rotasIndiretas.values.sortedBy { it.identidade.nome }))
        }
    }

    private fun iniciarAnunciosPresenca() {
        if (anunciosPresencaAtivos) return
        anunciosPresencaAtivos = true
        main.post(anunciarPresenca)
    }

    private fun expirarRotas() {
        val limite = SystemClock.elapsedRealtime() - VALIDADE_ROTA_MS
        if (rotasIndiretas.entries.removeAll { it.value.atualizadoEmMs < limite }) {
            publicar(estado.copy(rotasIndiretas = rotasIndiretas.values.sortedBy { it.identidade.nome }))
        }
    }

    fun pararRastro() {
        if (destruindo) return
        pararSensores()
        if (gnssAtivo) localizacao.removeUpdates(gpsListener)
        gnssAtivo = false
        transporte.close()
        pendentes.clear()
        rotasIndiretas.clear()
        anunciosPresencaAtivos = false
        main.removeCallbacksAndMessages(null)
        registrar("Monitoramento encerrado")
        publicar(estado.copy(ativo = false, vizinhos = emptyList(), rotasIndiretas = emptyList(), status = "Serviço parado", gnss = "Leitura GNSS parada",
            estadoLocalizacao = if (estado.posicaoLocal == null) EstadoLocalizacao.INDISPONIVEL else EstadoLocalizacao.ANTIGA,
            localizacaoStatus = "Aquisição parada; posição mantida apenas como histórica"))
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        destruindo = true
        anunciosPresencaAtivos = false
        imu.parar()
        consumidor?.shutdownNow()
        if (gnssAtivo) localizacao.removeUpdates(gpsListener)
        transporte.close()
        main.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    private fun publicar(novo: EstadoRastro) {
        estado = novo.copy(eventos = historico.eventos)
        observadores.toList().forEach { it.atualizado(estado) }
        atualizarNotificacao()
    }

    private fun registrar(texto: String, tipo: TipoEvento = TipoEvento.SERVICO,
                          aparelho: IdentidadeNo = IdentidadeNo(noLocal, nomeLocal), mensagem: Mensagem? = null) {
        historico.registrar(EventoRastro(System.currentTimeMillis(), tipo, aparelho,
            if (mensagem?.tipo == TipoMensagem.CONFIRMACAO) mensagem.referencia else mensagem?.id, texto, mensagem))
    }

    private fun iniciarForeground() {
        val notificacao = notificacao()
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTIFICATION_ID, notificacao, ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
        } else startForeground(NOTIFICATION_ID, notificacao)
    }

    private fun atualizarForegroundComLocalizacao() {
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTIFICATION_ID, notificacao(),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE or ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        }
    }

    private fun atualizarNotificacao() {
        if (!estado.ativo) return
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notificacao())
    }

    private fun notificacao() = NotificationCompat.Builder(this, CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_rastro_notification)
        .setContentTitle(getString(R.string.notification_title))
        .setContentText(if (estado.quantidadeConectados == 0) getString(R.string.notification_text) else "${estado.quantidadeConectados} vizinho(s) conectado(s)")
        .setOngoing(true)
        .setContentIntent(PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
        .build()

    private fun criarCanalNotificacao() {
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL_ID, getString(R.string.notification_channel), NotificationManager.IMPORTANCE_LOW))
    }

    companion object {
        const val ACTION_START = "com.example.rastro.START"
        const val ACTION_STOP = "com.example.rastro.STOP"
        private const val CHANNEL_ID = "rastro_monitoramento"
        private const val NOTIFICATION_ID = 108
        private const val TIMEOUT_ACK_MS = 10_000L
        private const val INTERVALO_PRESENCA_MS = 5_000L
        private const val VALIDADE_ROTA_MS = 16_000L
    }
}
