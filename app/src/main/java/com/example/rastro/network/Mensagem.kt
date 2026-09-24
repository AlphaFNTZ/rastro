package com.example.rastro.network

import java.util.Base64
import java.util.UUID

enum class TipoMensagem { SOS_MANUAL, CONFIRMACAO, PRESENCA }

/** Protocolo v2: SOS/ACK e anúncios de presença usados para observar rotas multi-hop. */
data class Mensagem(
    val id: String,
    val origem: String,
    val tipo: TipoMensagem,
    val limiteSaltos: Int = 4,
    val referencia: String? = null,
    val destino: String? = null,
    val saltosPercorridos: Int = 0,
    val nomeOrigem: String? = null
) {
    init {
        require(uuidValido(id) && uuidValido(origem)) { "Identificador inválido" }
        require(limiteSaltos in 0..16 && saltosPercorridos in 0..16) { "Contagem de saltos inválida" }
        require(when (tipo) {
            TipoMensagem.SOS_MANUAL -> referencia == null && destino == null && nomeOrigem == null && saltosPercorridos == 0
            TipoMensagem.CONFIRMACAO -> referencia != null && destino != null &&
                uuidValido(referencia) && uuidValido(destino) && nomeOrigem == null && saltosPercorridos == 0
            TipoMensagem.PRESENCA -> referencia == null && destino == null &&
                nomeOrigem?.let { nome ->
                    runCatching { AnuncioNo.normalizarNome(nome) == nome }.getOrDefault(false)
                } == true
        }) { "Campos incompatíveis com o tipo" }
    }

    fun codificar(): String {
        val nome = nomeOrigem?.let {
            Base64.getUrlEncoder().withoutPadding().encodeToString(it.toByteArray(Charsets.UTF_8))
        } ?: ""
        return listOf("2", id, origem, tipo.name, limiteSaltos.toString(), referencia ?: "",
            destino ?: "", saltosPercorridos.toString(), nome).joinToString("|")
    }

    fun confirmar(no: String): Mensagem = Mensagem(
        UUID.randomUUID().toString(), no, TipoMensagem.CONFIRMACAO, referencia = id, destino = origem
    )

    fun avancarSalto(): Mensagem = if (tipo == TipoMensagem.PRESENCA)
        copy(limiteSaltos = limiteSaltos - 1, saltosPercorridos = saltosPercorridos + 1)
    else copy(limiteSaltos = limiteSaltos - 1)

    companion object {
        private fun uuidValido(valor: String): Boolean = try {
            UUID.fromString(valor).toString() == valor
        } catch (_: IllegalArgumentException) { false }

        fun sos(no: String): Mensagem = Mensagem(UUID.randomUUID().toString(), no, TipoMensagem.SOS_MANUAL)

        fun presenca(no: String, nome: String): Mensagem = Mensagem(
            UUID.randomUUID().toString(), no, TipoMensagem.PRESENCA,
            nomeOrigem = AnuncioNo.normalizarNome(nome)
        )

        fun decodificar(texto: String): Mensagem {
            require(texto.length <= 512) { "Mensagem excede o limite" }
            val campos = texto.split('|')
            return when (campos.firstOrNull()) {
                "1" -> {
                    require(campos.size == 7) { "Formato v1 inválido" }
                    Mensagem(campos[1], campos[2], TipoMensagem.valueOf(campos[3]), campos[4].toInt(),
                        campos[5].ifEmpty { null }, campos[6].ifEmpty { null })
                }
                "2" -> {
                    require(campos.size == 9) { "Formato v2 inválido" }
                    val nome = campos[8].ifEmpty { null }?.let {
                        Base64.getUrlDecoder().decode(it).toString(Charsets.UTF_8)
                    }
                    Mensagem(campos[1], campos[2], TipoMensagem.valueOf(campos[3]), campos[4].toInt(),
                        campos[5].ifEmpty { null }, campos[6].ifEmpty { null }, campos[7].toInt(), nome)
                }
                else -> throw IllegalArgumentException("Versão ou formato inválido")
            }
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
