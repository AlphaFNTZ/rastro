package com.example.rastro

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.rastro.network.*
import com.example.rastro.s2.CircularImuBuffer
import com.example.rastro.sensors.*
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity(), Transporte.Eventos {
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var transporte: Transporte
    private lateinit var no: String
    private val vistas = MensagensVistas()
    private val pendentes = LinkedHashMap<String, Long>()
    private val log = ArrayDeque<String>()
    private val buffer = CircularImuBuffer(500)
    private lateinit var imu: ImuSensorSource
    private var consumidor: ScheduledExecutorService? = null
    private var sensoresAtivos = false
    private var sessaoSensores = 0
    private var visivel = false
    private var gnssAtivo = false
    private val localizacao by lazy { getSystemService(LocationManager::class.java) }

    private val permissoesRede = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        if (permissoesNecessarias().all(::permitida) && visivel) buscar()
        else status("Autorize as permissões de conexão nas configurações do aplicativo")
    }
    private val permissoesGnss = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        if (permitida(Manifest.permission.ACCESS_FINE_LOCATION) && visivel) iniciarGnss()
        else texto(R.id.gnss_status, "A leitura GNSS requer localização precisa")
    }
    private val gpsListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            when (val resultado = LocationMapper.converter(location)) {
                is ResultadoGnss.Indisponivel -> texto(R.id.gnss_status, resultado.motivo)
                is ResultadoGnss.Disponivel -> texto(R.id.gnss_status,
                    "GNSS: raio 68%% = %.2f m; acurácia de velocidade = %.2f m/s\nVariância por eixo = %.3f m²; velocidade = %.3f (m/s)²".format(
                        resultado.posicao.acuraciaHorizontalM, resultado.posicao.acuraciaVelocidadeMps,
                        resultado.covariancia.varianciaHorizontalM2, resultado.covariancia.varianciaVelocidadeM2s2))
            }
        }
        override fun onProviderDisabled(provider: String) { texto(R.id.gnss_status, "Ative a localização do aparelho") }
        override fun onProviderEnabled(provider: String) = Unit
        @Deprecated("Callback legado necessário na API 26")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        val prefs = getSharedPreferences("rastro-no", MODE_PRIVATE)
        no = prefs.getString("id", null) ?: UUID.randomUUID().toString().also { prefs.edit().putString("id", it).apply() }
        imu = ImuSensorSource(this, buffer)
        findViewById<Button>(R.id.discover).setOnClickListener {
            val permissoes = permissoesNecessarias()
            if (permissoes.all(::permitida)) buscar() else permissoesRede.launch(permissoes)
        }
        findViewById<Button>(R.id.disconnect).setOnClickListener { transporte.desconectar() }
        findViewById<Button>(R.id.sos).setOnClickListener { enviarSos() }
        findViewById<Button>(R.id.sensors).setOnClickListener { if (sensoresAtivos) pararSensores() else iniciarSensores() }
        findViewById<Button>(R.id.gnss).setOnClickListener {
            if (permitida(Manifest.permission.ACCESS_FINE_LOCATION)) iniciarGnss()
            else permissoesGnss.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
        }
        registrar("Nó ${no.take(8)} • SOS manual, conexão direta")
    }

    override fun onStart() {
        super.onStart()
        visivel = true
        transporte = WifiDirectTransport(this, this)
    }

    private fun permitida(permissao: String) = ContextCompat.checkSelfPermission(this, permissao) == PackageManager.PERMISSION_GRANTED
    private fun permissoesNecessarias(): Array<String> = buildList {
        if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.NEARBY_WIFI_DEVICES)
        else { add(Manifest.permission.ACCESS_FINE_LOCATION); add(Manifest.permission.ACCESS_COARSE_LOCATION) }
        if (Build.VERSION.SDK_INT >= 37) add("android.permission.ACCESS_LOCAL_NETWORK")
    }.toTypedArray()

    private fun buscar() { transporte.iniciar(); transporte.descobrir() }

    private fun enviarSos() {
        if (pendentes.size >= 16) { texto(R.id.sos_result, "Aguarde as confirmações pendentes"); return }
        val mensagem = Mensagem.sos(no)
        if (!transporte.enviar(mensagem)) { texto(R.id.sos_result, "Sem canal disponível ou fila cheia. Conecte e tente novamente"); return }
        pendentes[mensagem.id] = SystemClock.elapsedRealtime()
        texto(R.id.sos_result, "SOS ${mensagem.id.take(8)} enviado; aguardando confirmação…")
        registrar("Enviado SOS ${mensagem.id.take(8)}")
        handler.postDelayed({
            if (pendentes.remove(mensagem.id) != null) {
                texto(R.id.sos_result, "SOS ${mensagem.id.take(8)} sem confirmação após 10 segundos. Tente novamente")
                registrar("Timeout ${mensagem.id.take(8)}")
            }
        }, 10_000)
    }

    override fun recebida(mensagem: Mensagem) {
        when (mensagem.tipo) {
            TipoMensagem.SOS_MANUAL -> {
                val confirmou = transporte.enviar(mensagem.confirmar(no))
                if (vistas.primeiraVez(mensagem.id)) {
                    texto(R.id.sos_result, "SOS de teste recebido do nó ${mensagem.origem.take(8)}")
                    registrar("Recebido SOS ${mensagem.id.take(8)}")
                }
                if (!confirmou) registrar("Não foi possível enfileirar confirmação")
            }
            TipoMensagem.CONFIRMACAO -> {
                if (mensagem.destino != no) return
                val inicio = pendentes.remove(mensagem.referencia) ?: return
                val tempo = SystemClock.elapsedRealtime() - inicio
                texto(R.id.sos_result, "SOS ${mensagem.referencia?.take(8)} confirmado pelo aplicativo remoto em $tempo ms")
                registrar("Confirmado ${mensagem.referencia?.take(8)} em $tempo ms")
            }
        }
    }

    override fun conectado(ativo: Boolean) {
        findViewById<Button>(R.id.sos).isEnabled = ativo
        if (!ativo && pendentes.isNotEmpty()) {
            texto(R.id.sos_result, "Conexão encerrada; ${pendentes.size} SOS sem confirmação")
            pendentes.clear()
        }
    }
    override fun status(texto: String) { texto(R.id.status, texto); registrar(texto) }
    override fun dispositivos(lista: List<Dispositivo>) {
        val container = findViewById<LinearLayout>(R.id.peers)
        container.removeAllViews()
        lista.forEach { dispositivo ->
            container.addView(Button(this).apply {
                text = "Conectar a ${dispositivo.nome}"
                setOnClickListener { transporte.conectar(dispositivo) }
            })
        }
        if (lista.isEmpty()) status("Nenhum dispositivo encontrado; mantenha a busca ativa nos dois aparelhos")
    }

    private fun iniciarSensores() {
        if (!imu.iniciar()) { texto(R.id.sensor_status, "Acelerômetro indisponível"); return }
        sensoresAtivos = true
        val sessao = ++sessaoSensores
        buffer.drenar()
        findViewById<Button>(R.id.sensors).setText(R.string.stop_sensors)
        var total = 0L
        var primeiro = 0L
        var ultimo = 0L
        consumidor = Executors.newSingleThreadScheduledExecutor { Thread(it, "rastro-consumidor-imu") }.also {
            it.scheduleAtFixedRate({
                val lote = buffer.drenar()
                if (lote.isNotEmpty()) {
                    if (primeiro == 0L) primeiro = lote.first().timestampMonotonicoNs
                    ultimo = lote.last().timestampMonotonicoNs
                    total += lote.size
                }
                val hz = if (ultimo > primeiro) (total - 1) * 1e9 / (ultimo - primeiro) else 0.0
                val resumo = "IMU: $total leituras; %.1f Hz observados; ${buffer.descartadas} descartadas\nQualidade: ${lote.lastOrNull()?.qualidade ?: "aguardando"}; covariância numérica indisponível".format(hz)
                handler.post { if (visivel && sensoresAtivos && sessao == sessaoSensores) texto(R.id.sensor_status, resumo) }
            }, 1, 1, TimeUnit.SECONDS)
        }
    }

    private fun pararSensores() {
        sensoresAtivos = false
        sessaoSensores++
        imu.parar()
        consumidor?.shutdownNow()
        consumidor = null
        findViewById<Button>(R.id.sensors).setText(R.string.start_sensors)
        texto(R.id.sensor_status, "Leituras IMU paradas")
    }

    @SuppressLint("MissingPermission")
    private fun iniciarGnss() {
        if (gnssAtivo) return
        try {
            if (!localizacao.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                texto(R.id.gnss_status, "Ative a localização. Para obter GNSS, prefira um local aberto")
                return
            }
            localizacao.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 0f, gpsListener, Looper.getMainLooper())
            gnssAtivo = true
            texto(R.id.gnss_status, "Aguardando GNSS com acurácia de velocidade; pode levar alguns minutos")
        } catch (e: Exception) { texto(R.id.gnss_status, "Não foi possível iniciar GNSS: ${e.message}") }
    }

    override fun onStop() {
        visivel = false
        pararSensores()
        localizacao.removeUpdates(gpsListener)
        gnssAtivo = false
        texto(R.id.gnss_status, "Leitura GNSS parada")
        transporte.close()
        texto(R.id.status, "Sessão encerrada; busque dispositivos para reconectar")
        findViewById<LinearLayout>(R.id.peers).removeAllViews()
        handler.removeCallbacksAndMessages(null)
        super.onStop()
    }

    private fun texto(id: Int, valor: String) { findViewById<TextView>(id).text = valor }
    private fun registrar(evento: String) {
        if (log.size == 30) log.removeFirst()
        log.addLast(evento)
        texto(R.id.event_log, log.joinToString("\n"))
    }
}
