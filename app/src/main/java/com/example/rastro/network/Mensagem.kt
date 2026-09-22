package com.example.rastro.network

import java.util.UUID

enum class TipoMensagem { SOS_MANUAL, CONFIRMACAO }

/** Protocolo v1 de demonstração; IDs aleatórios, sem localização ou dados pessoais.
 * Confirmação significa recebimento pelo aplicativo, não atendimento humano.
 */
data class Mensagem(
    val id: String,
    val origem: String,
    val tipo: TipoMensagem,
    val limiteSaltos: Int = 4,
    val referencia: String? = null,
    val destino: String? = null
) {
    init {
        require(uuidValido(id) && uuidValido(origem)) { "Identificador inválido" }
        require(limiteSaltos in 0..16) { "Limite de saltos inválido" }
        require(when (tipo) {
            TipoMensagem.SOS_MANUAL -> referencia == null && destino == null
            TipoMensagem.CONFIRMACAO -> referencia != null && destino != null && uuidValido(referencia) && uuidValido(destino)
        }) { "Campos incompatíveis com o tipo" }
    }
    fun codificar(): String = listOf("1", id, origem, tipo.name, limiteSaltos.toString(), referencia ?: "", destino ?: "").joinToString("|")
    fun confirmar(no: String): Mensagem = Mensagem(UUID.randomUUID().toString(), no, TipoMensagem.CONFIRMACAO, referencia = id, destino = origem)

    companion object {
        private fun uuidValido(valor: String): Boolean = try { UUID.fromString(valor).toString() == valor } catch (_: IllegalArgumentException) { false }
        fun sos(no: String): Mensagem = Mensagem(UUID.randomUUID().toString(), no, TipoMensagem.SOS_MANUAL)
        fun decodificar(texto: String): Mensagem {
            require(texto.length <= 512) { "Mensagem excede o limite" }
            val campos = texto.split('|')
            require(campos.size == 7 && campos[0] == "1") { "Versão ou formato inválido" }
            return Mensagem(campos[1], campos[2], TipoMensagem.valueOf(campos[3]), campos[4].toInt(),
                campos[5].ifEmpty { null }, campos[6].ifEmpty { null })
        }
    }
}

/** Cache limitado para supressão de duplicatas. Histórico antigo pode ser expulso. */
class MensagensVistas(private val capacidade: Int = 256) {
    init { require(capacidade > 0) }
    private val ids = LinkedHashSet<String>()
    @Synchronized fun primeiraVez(id: String): Boolean {
        if (!ids.add(id)) return false
        if (ids.size > capacidade) ids.remove(ids.first())
        return true
    }
}
