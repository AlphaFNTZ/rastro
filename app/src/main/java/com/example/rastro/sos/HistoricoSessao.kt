package com.example.rastro.sos

import com.example.rastro.network.IdentidadeNo
import com.example.rastro.network.Mensagem
import com.example.rastro.network.MensagensVistas
import com.example.rastro.network.TipoMensagem

enum class TipoEvento { ENVIO, RECEBIMENTO, ACK, TIMEOUT, CONEXAO, SERVICO }

data class EventoRastro(
    val registradoEmEpochMs: Long,
    val tipo: TipoEvento,
    val aparelho: IdentidadeNo,
    val sosId: String? = null,
    val descricao: String,
    val mensagem: Mensagem? = null,
    val intermediario: IdentidadeNo? = null
)

data class SosRecebido(val mensagem: Mensagem, val recebidoEmEpochMs: Long, val intermediario: IdentidadeNo?)

/** Estado da sessão, mantido no serviço e independente da recriação da tela. */
class HistoricoSessao(private val capacidade: Int = 100) {
    init { require(capacidade > 0) }
    private val registros = ArrayDeque<EventoRastro>()
    private val recebidos = MensagensVistas(2048)
    var ultimoSos: SosRecebido? = null
        private set
    val eventos: List<EventoRastro> get() = registros.toList()

    fun registrar(evento: EventoRastro) {
        if (registros.size == capacidade) registros.removeFirst()
        registros.addLast(evento)
    }

    fun receber(mensagem: Mensagem, agoraEpochMs: Long, via: IdentidadeNo?): Boolean {
        require(mensagem.tipo == TipoMensagem.SOS_MANUAL)
        if (!recebidos.primeiraVez(mensagem.id)) return false
        val intermediario = via?.takeIf { it.id != mensagem.origem }
        ultimoSos = SosRecebido(mensagem, agoraEpochMs, intermediario)
        registrar(EventoRastro(agoraEpochMs, TipoEvento.RECEBIMENTO,
            IdentidadeNo(mensagem.origem, mensagem.nomeOrigem ?: "Nó ${mensagem.origem.take(8)}"),
            mensagem.id, "SOS recebido", mensagem, intermediario))
        return true
    }
}
