package com.example.rastro.network

/**
 * Roteamento por inundação para mensagens pequenas. O transporte conhece apenas
 * vizinhos de rádio; este componente acrescenta deduplicação, TTL e ACK multi-hop.
 */
class MeshRouter(
    private val noLocal: String,
    private val transmitir: (mensagem: Mensagem, excetoEndpoint: String?) -> Int,
    private val entregar: (vindoDe: String, mensagem: Mensagem) -> Unit,
    capacidadeHistorico: Int = 256,
    private val nomeLocal: () -> String? = { null }
) {
    private val vistas = MensagensVistas(capacidadeHistorico)

    /** Registra antes de transmitir para que uma cópia devolvida pela malha seja descartada. */
    fun originar(mensagem: Mensagem): Boolean {
        require(mensagem.origem == noLocal) { "A origem da mensagem deve ser o nó local" }
        vistas.primeiraVez(mensagem.id)
        return transmitir(mensagem, null) > 0
    }

    fun receber(vindoDe: String, mensagem: Mensagem) {
        if (!vistas.primeiraVez(mensagem.id)) return

        when (mensagem.tipo) {
            TipoMensagem.SOS_MANUAL -> {
                entregar(vindoDe, mensagem)
                originar(mensagem.confirmar(noLocal, nomeLocal()))
            }
            TipoMensagem.CONFIRMACAO -> if (mensagem.destino == noLocal) entregar(vindoDe, mensagem)
            TipoMensagem.PRESENCA -> entregar(vindoDe, mensagem)
        }

        if (mensagem.limiteSaltos == 0) return
        if (mensagem.tipo == TipoMensagem.CONFIRMACAO && mensagem.destino == noLocal) return
        transmitir(mensagem.avancarSalto(), vindoDe)
    }
}
