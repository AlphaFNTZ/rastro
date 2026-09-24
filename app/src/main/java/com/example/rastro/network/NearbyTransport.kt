package com.example.rastro.network

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.AdvertisingOptions
import com.google.android.gms.nearby.connection.BandwidthInfo
import com.google.android.gms.nearby.connection.ConnectionInfo
import com.google.android.gms.nearby.connection.ConnectionLifecycleCallback
import com.google.android.gms.nearby.connection.ConnectionResolution
import com.google.android.gms.nearby.connection.ConnectionsClient
import com.google.android.gms.nearby.connection.ConnectionsStatusCodes
import com.google.android.gms.nearby.connection.DiscoveredEndpointInfo
import com.google.android.gms.nearby.connection.DiscoveryOptions
import com.google.android.gms.nearby.connection.EndpointDiscoveryCallback
import com.google.android.gms.nearby.connection.Payload
import com.google.android.gms.nearby.connection.PayloadCallback
import com.google.android.gms.nearby.connection.PayloadTransferUpdate
import com.google.android.gms.nearby.connection.Strategy

/** Enlace M:N. O nome é visual; somente o UUID participa da regra de conexão. */
@SuppressLint("MissingPermission")
class NearbyTransport(
    context: Context,
    private val noLocal: String,
    nomeLocal: String,
    private val eventos: Eventos
) : AutoCloseable {
    interface Eventos {
        fun status(texto: String)
        fun vizinhosAlterados(vizinhos: List<Vizinho>)
        fun recebida(endpointId: String, mensagem: Mensagem)
    }

    private val client: ConnectionsClient = Nearby.getConnectionsClient(context.applicationContext)
    private val main = Handler(Looper.getMainLooper())
    private val vizinhos = LinkedHashMap<String, Vizinho>()
    private var anuncioLocal = AnuncioNo.codificar(noLocal, nomeLocal)
    private var ativo = false

    private val payloadCallback = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            val bytes = payload.asBytes() ?: return
            val mensagem = runCatching { Mensagem.decodificar(bytes.toString(Charsets.UTF_8)) }
                .getOrElse {
                    publicarStatus("Mensagem inválida descartada de ${rotulo(endpointId)}")
                    return
                }
            main.post { if (ativo) eventos.recebida(endpointId, mensagem) }
        }

        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) {
            if (update.status == PayloadTransferUpdate.Status.FAILURE) {
                publicarStatus("Falha ao transferir mensagem para ${rotulo(endpointId)}")
            }
        }
    }

    private val conexoes = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, info: ConnectionInfo) {
            val identidade = AnuncioNo.decodificar(info.endpointName)
            if (identidade == null) {
                client.rejectConnection(endpointId)
                publicarStatus("Conexão rejeitada: anúncio Rastro inválido")
                return
            }
            atualizar(endpointId, identidade, EstadoVizinho.CONECTANDO)
            // Protótipo acadêmico: o nome/UUID declarado não é autenticado.
            client.acceptConnection(endpointId, payloadCallback)
                .addOnFailureListener { falha(endpointId, "aceitar conexão", it) }
        }

        override fun onConnectionResult(endpointId: String, result: ConnectionResolution) {
            val atual = vizinhos[endpointId]
            if (result.status.statusCode == ConnectionsStatusCodes.STATUS_OK && atual != null) {
                atualizar(endpointId, atual.identidade, EstadoVizinho.CONECTADO)
                publicarStatus("Conectado automaticamente a ${atual.identidade.rotulo}")
            } else {
                vizinhos.remove(endpointId)
                publicarVizinhos()
                publicarStatus("Conexão Nearby recusada/encerrada (${result.status.statusCode})")
            }
        }

        override fun onDisconnected(endpointId: String) {
            val nome = vizinhos.remove(endpointId)?.identidade?.rotulo ?: endpointId.take(8)
            publicarVizinhos()
            publicarStatus("$nome desconectado; descoberta continua ativa")
        }

        override fun onBandwidthChanged(endpointId: String, bandwidthInfo: BandwidthInfo) {
            val atual = vizinhos[endpointId] ?: return
            val qualidade = when (bandwidthInfo.quality) {
                BandwidthInfo.Quality.LOW -> QualidadeEnlace.BAIXA
                BandwidthInfo.Quality.MEDIUM -> QualidadeEnlace.MEDIA
                BandwidthInfo.Quality.HIGH -> QualidadeEnlace.ALTA
                else -> QualidadeEnlace.DESCONHECIDA
            }
            vizinhos[endpointId] = atual.copy(qualidade = qualidade)
            publicarVizinhos()
        }
    }

    private val descoberta = object : EndpointDiscoveryCallback() {
        override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
            val identidade = AnuncioNo.decodificar(info.endpointName) ?: return
            val atual = vizinhos[endpointId]
            if (atual?.estado == EstadoVizinho.CONECTADO || atual?.estado == EstadoVizinho.CONECTANDO) return
            atualizar(endpointId, identidade, EstadoVizinho.DESCOBERTO)
            if (noLocal >= identidade.id) return
            atualizar(endpointId, identidade, EstadoVizinho.CONECTANDO)
            client.requestConnection(anuncioLocal, endpointId, conexoes)
                .addOnFailureListener { falha(endpointId, "solicitar conexão", it) }
        }

        override fun onEndpointLost(endpointId: String) {
            if (vizinhos[endpointId]?.estado != EstadoVizinho.CONECTADO) {
                vizinhos.remove(endpointId)
                publicarVizinhos()
            }
        }
    }

    fun iniciar() {
        if (ativo) return
        ativo = true
        val advertising = AdvertisingOptions.Builder().setStrategy(Strategy.P2P_CLUSTER).build()
        val discovery = DiscoveryOptions.Builder().setStrategy(Strategy.P2P_CLUSTER).build()
        client.startAdvertising(anuncioLocal, SERVICE_ID, conexoes, advertising)
            .addOnSuccessListener { publicarStatus("Rastro visível para aparelhos próximos") }
            .addOnFailureListener { publicarStatus("Falha ao anunciar: ${it.message ?: it.javaClass.simpleName}") }
        client.startDiscovery(SERVICE_ID, descoberta, discovery)
            .addOnSuccessListener { publicarStatus("Descoberta automática Nearby ativa") }
            .addOnFailureListener { publicarStatus("Falha na descoberta: ${it.message ?: it.javaClass.simpleName}") }
    }

    fun atualizarNome(nome: String) {
        val estavaAtivo = ativo
        close()
        anuncioLocal = AnuncioNo.codificar(noLocal, nome)
        if (estavaAtivo) iniciar()
    }

    fun enviar(mensagem: Mensagem, excetoEndpoint: String? = null): Int {
        val destinos = vizinhos.values
            .filter { it.estado == EstadoVizinho.CONECTADO && it.endpointId != excetoEndpoint }
            .map(Vizinho::endpointId)
        if (destinos.isEmpty()) return 0
        val payload = Payload.fromBytes(mensagem.codificar().toByteArray(Charsets.UTF_8))
        client.sendPayload(destinos, payload).addOnFailureListener {
            publicarStatus("Falha ao enfileirar mensagem: ${it.message ?: it.javaClass.simpleName}")
        }
        return destinos.size
    }

    fun quantidadeConectados(): Int = vizinhos.values.count { it.estado == EstadoVizinho.CONECTADO }

    override fun close() {
        if (!ativo) return
        ativo = false
        client.stopDiscovery()
        client.stopAdvertising()
        client.stopAllEndpoints()
        vizinhos.clear()
        publicarVizinhos()
    }

    private fun atualizar(endpointId: String, identidade: IdentidadeNo, estado: EstadoVizinho) {
        val qualidade = vizinhos[endpointId]?.qualidade ?: QualidadeEnlace.DESCONHECIDA
        vizinhos[endpointId] = Vizinho(endpointId, identidade, estado, qualidade)
        publicarVizinhos()
    }

    private fun falha(endpointId: String, operacao: String, erro: Exception) {
        val atual = vizinhos[endpointId]
        if (atual != null) atualizar(endpointId, atual.identidade, EstadoVizinho.DESCOBERTO)
        publicarStatus("Não foi possível $operacao: ${erro.message ?: erro.javaClass.simpleName}")
    }

    private fun publicarStatus(texto: String) = main.post { if (ativo) eventos.status(texto) }
    private fun publicarVizinhos() {
        val copia = vizinhos.values.sortedWith(compareBy<Vizinho> { it.estado }.thenBy { it.identidade.nome }).toList()
        main.post { eventos.vizinhosAlterados(copia) }
    }
    private fun rotulo(endpointId: String) = vizinhos[endpointId]?.identidade?.rotulo ?: endpointId.take(8)

    companion object { private const val SERVICE_ID = "com.example.rastro" }
}
