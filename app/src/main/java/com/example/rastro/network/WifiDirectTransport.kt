package com.example.rastro.network

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.wifi.WpsInfo
import android.net.wifi.p2p.WifiP2pConfig
import android.net.wifi.p2p.WifiP2pManager
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat

/** Prova de transporte direto, restrita a dois aparelhos e ao ciclo visível da tela.
 * Permissões são solicitadas pela Activity antes de iniciar esta instância.
 */
@SuppressLint("MissingPermission")
class WifiDirectTransport(context: Context, private val eventos: Transporte.Eventos) : Transporte {
    private val context = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private val manager = this.context.getSystemService(WifiP2pManager::class.java)
    private var channel: WifiP2pManager.Channel? = null
    private var registrado = false
    private var ativo = false
    private var geracao = 0
    private var tcp: CanalTcp? = null
    private var grupo: String? = null
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION -> canal { ch ->
                    manager.requestPeers(ch) { peers ->
                        if (ativo) eventos.dispositivos(peers.deviceList.map { Dispositivo(it.deviceAddress, it.deviceName.ifBlank { "Dispositivo Wi-Fi Direct" }) })
                    }
                }
                WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION -> atualizarConexao()
                WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION -> {
                    if (intent.getIntExtra(WifiP2pManager.EXTRA_WIFI_STATE, -1) != WifiP2pManager.WIFI_P2P_STATE_ENABLED) {
                        encerrarCanal()
                        eventos.status("Ative o Wi-Fi para conectar")
                    }
                }
            }
        }
    }

    override fun iniciar() {
        if (ativo) return
        if (!context.packageManager.hasSystemFeature(PackageManager.FEATURE_WIFI_DIRECT) || manager == null) {
            eventos.status("Este aparelho não oferece Wi-Fi Direct")
            return
        }
        ativo = true
        channel = manager.initialize(context, Looper.getMainLooper()) {
            if (ativo) {
                encerrarCanal()
                eventos.status("Serviço Wi-Fi Direct indisponível; saia e abra a tela novamente")
            }
        }
        val filter = IntentFilter().apply {
            addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION)
        }
        // Broadcasts protegidos do sistema; Wi-Fi pode ser emitido por UID privilegiado.
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_EXPORTED)
        registrado = true
        atualizarConexao()
    }

    private fun canal(acao: (WifiP2pManager.Channel) -> Unit) {
        val ch = channel ?: return
        if (!ativo) return
        try { acao(ch) } catch (_: SecurityException) {
            eventos.status("Permissão ausente. Autorize dispositivos próximos/localização")
        }
    }

    private fun resultado(sucesso: String) = object : WifiP2pManager.ActionListener {
        override fun onSuccess() { if (ativo) eventos.status(sucesso) }
        override fun onFailure(reason: Int) {
            val motivo = when (reason) {
                WifiP2pManager.BUSY -> "Wi-Fi ocupado; tente novamente"
                WifiP2pManager.P2P_UNSUPPORTED -> "Wi-Fi Direct não suportado"
                else -> "erro $reason; confira Wi-Fi, localização e permissões"
            }
            if (ativo) eventos.status(motivo)
        }
    }

    override fun descobrir() = canal { manager.discoverPeers(it, resultado("Buscando aparelhos próximos…")) }

    override fun conectar(dispositivo: Dispositivo) = canal {
        val config = WifiP2pConfig().apply {
            deviceAddress = dispositivo.endereco
            wps.setup = WpsInfo.PBC
        }
        manager.connect(it, config, resultado("Conexão solicitada. Aceite no outro aparelho"))
    }

    private fun atualizarConexao() = canal { ch ->
        val solicitacao = geracao
        manager.requestConnectionInfo(ch) { info ->
            if (!ativo || solicitacao != geracao) return@requestConnectionInfo
            if (!info.groupFormed || info.groupOwnerAddress == null) {
                encerrarCanal()
                return@requestConnectionInfo
            }
            val endereco = info.groupOwnerAddress.hostAddress ?: return@requestConnectionInfo
            val chave = "${info.isGroupOwner}:$endereco"
            if (grupo == chave && tcp != null) return@requestConnectionInfo
            encerrarCanal()
            grupo = chave
            val sessao = geracao
            eventos.status(if (info.isGroupOwner) "Grupo criado; aguardando o outro aparelho…" else "Abrindo canal com o outro aparelho…")
            tcp = CanalTcp(info.isGroupOwner, endereco, estado = { conectado, texto ->
                main.post {
                    if (ativo && sessao == geracao) {
                        eventos.conectado(conectado)
                        eventos.status(texto)
                        if (!conectado) encerrarCanal()
                    }
                }
            }, receber = { mensagem -> main.post { if (ativo && sessao == geracao) eventos.recebida(mensagem) } }).also { it.iniciar() }
        }
    }

    private fun encerrarCanal() {
        geracao++
        tcp?.close()
        tcp = null
        grupo = null
        eventos.conectado(false)
    }

    override fun enviar(mensagem: Mensagem): Boolean = tcp?.enviar(mensagem) ?: false

    override fun desconectar() {
        encerrarCanal()
        canal {
            manager.cancelConnect(it, null)
            manager.removeGroup(it, resultado("Desconectado"))
        }
    }

    override fun close() {
        desconectar()
        ativo = false
        if (registrado) context.unregisterReceiver(receiver)
        registrado = false
        channel?.close()
        channel = null
    }
}
