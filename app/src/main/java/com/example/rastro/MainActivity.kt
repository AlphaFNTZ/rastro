package com.example.rastro

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.rastro.service.EstadoRastro
import com.example.rastro.service.RastroService
import com.example.rastro.network.EstadoVizinho
import com.example.rastro.network.QualidadeEnlace

class MainActivity : AppCompatActivity() {
    private var servico: RastroService? = null
    private var vinculado = false

    private val observador = RastroService.Observador(::renderizar)
    private val conexao = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            servico = (binder as RastroService.LocalBinder).servico()
            vinculado = true
            servico?.observar(observador)
        }
        override fun onServiceDisconnected(name: ComponentName?) {
            vinculado = false
            servico = null
            renderizar(EstadoRastro(status = "Serviço foi encerrado pelo sistema"))
        }
    }

    private val permissoesRede = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        if (permissoesRedeObrigatorias().all(::permitida)) iniciarRastro()
        else texto(R.id.status, "Autorize dispositivos próximos e Bluetooth para iniciar")
    }

    private val permissaoGnss = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        if (permitida(Manifest.permission.ACCESS_FINE_LOCATION)) servico?.iniciarGnss()
        else texto(R.id.gnss_status, "A leitura GNSS requer localização precisa")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { view, insets ->
            val barras = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(barras.left, barras.top, barras.right, barras.bottom)
            insets
        }

        findViewById<Button>(R.id.discover).setOnClickListener {
            val faltantes = permissoesSolicitadas().filterNot(::permitida).toTypedArray()
            if (faltantes.isEmpty()) iniciarRastro() else permissoesRede.launch(faltantes)
        }
        findViewById<Button>(R.id.save_name).setOnClickListener {
            servico?.alterarNome(findViewById<EditText>(R.id.device_name).text.toString())
        }
        findViewById<Button>(R.id.disconnect).setOnClickListener { servico?.pararRastro() }
        findViewById<Button>(R.id.sos).setOnClickListener { servico?.enviarSos() }
        findViewById<Button>(R.id.sensors).setOnClickListener { servico?.alternarSensores() }
        findViewById<Button>(R.id.gnss).setOnClickListener {
            if (permitida(Manifest.permission.ACCESS_FINE_LOCATION)) servico?.iniciarGnss()
            else permissaoGnss.launch(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION))
        }
    }

    override fun onStart() {
        super.onStart()
        bindService(Intent(this, RastroService::class.java), conexao, Context.BIND_AUTO_CREATE)
    }

    override fun onStop() {
        servico?.observar(null)
        if (vinculado) unbindService(conexao)
        vinculado = false
        servico = null
        super.onStop()
    }

    private fun iniciarRastro() {
        ContextCompat.startForegroundService(this, Intent(this, RastroService::class.java).setAction(RastroService.ACTION_START))
    }

    private fun permissoesRedeObrigatorias(): List<String> = buildList {
        when {
            Build.VERSION.SDK_INT >= 33 -> {
                add(Manifest.permission.BLUETOOTH_SCAN)
                add(Manifest.permission.BLUETOOTH_CONNECT)
                add(Manifest.permission.BLUETOOTH_ADVERTISE)
                add(Manifest.permission.NEARBY_WIFI_DEVICES)
            }
            Build.VERSION.SDK_INT == 32 -> {
                add(Manifest.permission.BLUETOOTH_SCAN)
                add(Manifest.permission.BLUETOOTH_CONNECT)
                add(Manifest.permission.BLUETOOTH_ADVERTISE)
            }
            Build.VERSION.SDK_INT == 31 -> {
                add(Manifest.permission.BLUETOOTH_SCAN)
                add(Manifest.permission.BLUETOOTH_CONNECT)
                add(Manifest.permission.BLUETOOTH_ADVERTISE)
                add(Manifest.permission.ACCESS_FINE_LOCATION)
            }
            Build.VERSION.SDK_INT >= 29 -> add(Manifest.permission.ACCESS_FINE_LOCATION)
            else -> add(Manifest.permission.ACCESS_COARSE_LOCATION)
        }
        if (Build.VERSION.SDK_INT >= 37) add("android.permission.ACCESS_LOCAL_NETWORK")
    }

    private fun permissoesSolicitadas(): List<String> = permissoesRedeObrigatorias() + buildList {
        if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
    }

    private fun permitida(permissao: String) =
        ContextCompat.checkSelfPermission(this, permissao) == PackageManager.PERMISSION_GRANTED

    private fun renderizar(estado: EstadoRastro) {
        texto(R.id.status, estado.status)
        texto(R.id.sos_result, estado.sos)
        texto(R.id.sensor_status, estado.sensor)
        texto(R.id.gnss_status, estado.gnss)
        texto(R.id.event_log, estado.eventos.joinToString("\n"))
        val campoNome = findViewById<EditText>(R.id.device_name)
        if (!campoNome.hasFocus() && campoNome.text.toString() != estado.nomeLocal) {
            campoNome.setText(estado.nomeLocal)
        }
        val diretos = estado.vizinhos.map { vizinho ->
                val marcador = if (vizinho.estado == EstadoVizinho.CONECTADO) "●" else "○"
                val situacao = when (vizinho.estado) {
                    EstadoVizinho.DESCOBERTO -> "Descoberto"
                    EstadoVizinho.CONECTANDO -> "Conectando"
                    EstadoVizinho.CONECTADO -> "Conectado"
                }
                val qualidade = when (vizinho.qualidade) {
                    QualidadeEnlace.DESCONHECIDA -> "não informada"
                    QualidadeEnlace.BAIXA -> "baixa"
                    QualidadeEnlace.MEDIA -> "média"
                    QualidadeEnlace.ALTA -> "alta"
                }
                "$marcador ${vizinho.identidade.rotulo} — $situacao\n" +
                    "  Alcance: direto\n" +
                    "  Transporte: Nearby automático (rádio físico não exposto)\n" +
                    "  Qualidade do enlace: $qualidade"
            }
        val indiretos = estado.rotasIndiretas.map { rota ->
            "◆ ${rota.identidade.rotulo} — Alcançável via intermediário\n" +
                "  Alcance: ${rota.saltos} saltos, próximo nó ${rota.via.rotulo}\n" +
                "  Transporte: Nearby automático em cada enlace (rádio físico não exposto)"
        }
        val descricoes = diretos + indiretos
        texto(R.id.peers, if (descricoes.isEmpty()) getString(R.string.no_devices)
            else descricoes.joinToString("\n\n"))
        findViewById<Button>(R.id.sos).isEnabled = estado.ativo && estado.quantidadeConectados > 0
        findViewById<Button>(R.id.sensors).apply {
            isEnabled = estado.ativo
            setText(if (estado.sensoresAtivos) R.string.stop_sensors else R.string.start_sensors)
        }
        findViewById<Button>(R.id.gnss).isEnabled = estado.ativo
        findViewById<Button>(R.id.disconnect).isEnabled = estado.ativo
    }

    private fun texto(id: Int, valor: String) {
        findViewById<TextView>(id).text = valor
    }
}
