package com.example.rastro.network

import com.example.rastro.sos.EstadoLocalizacao
import com.example.rastro.sos.LocalizacaoSos
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.util.Base64
import java.util.UUID

enum class TipoMensagem { SOS_MANUAL, CONFIRMACAO, PRESENCA }

/** v3; horários UTC são dados, nunca relógios para timeouts entre aparelhos. */
data class Mensagem(
    val id: String,
    val origem: String,
    val tipo: TipoMensagem,
    val limiteSaltos: Int = 4,
    val referencia: String? = null,
    val destino: String? = null,
    val saltosPercorridos: Int = 0,
    val nomeOrigem: String? = null,
    val emitidoEmEpochMs: Long? = null,
    val localizacao: LocalizacaoSos? = null,
    val idadeLocalizacaoNaEmissaoMs: Long? = null,
    val estadoLocalizacao: EstadoLocalizacao = EstadoLocalizacao.INDISPONIVEL
) {
    init {
        require(uuidValido(id) && uuidValido(origem)) { "Identificador inválido" }
        require(limiteSaltos in 0..16 && saltosPercorridos in 0..16 && limiteSaltos + saltosPercorridos <= 16)
        require(nomeOrigem == null || AnuncioNo.normalizarNome(nomeOrigem) == nomeOrigem) { "Nome inválido" }
        require(emitidoEmEpochMs == null || emitidoEmEpochMs > 0)
        require(idadeLocalizacaoNaEmissaoMs == null || idadeLocalizacaoNaEmissaoMs >= 0)
        require(if (localizacao == null) idadeLocalizacaoNaEmissaoMs == null && estadoLocalizacao == EstadoLocalizacao.INDISPONIVEL
            else idadeLocalizacaoNaEmissaoMs != null && emitidoEmEpochMs != null && estadoLocalizacao != EstadoLocalizacao.INDISPONIVEL)
        require(when (tipo) {
            TipoMensagem.SOS_MANUAL -> referencia == null && destino == null
            TipoMensagem.CONFIRMACAO -> referencia != null && destino != null && uuidValido(referencia) && uuidValido(destino)
            TipoMensagem.PRESENCA -> referencia == null && destino == null && nomeOrigem != null
        }) { "Campos incompatíveis com o tipo" }
        if (tipo != TipoMensagem.SOS_MANUAL) require(localizacao == null && emitidoEmEpochMs == null)
    }

    fun codificar(): String {
        val nome = nomeOrigem?.let { Base64.getUrlEncoder().withoutPadding().encodeToString(it.toByteArray(Charsets.UTF_8)) } ?: ""
        return listOf("3", id, origem, tipo.name, limiteSaltos.toString(), referencia ?: "", destino ?: "",
            saltosPercorridos.toString(), nome, emitidoEmEpochMs?.toString() ?: "", localizacao?.latitude?.toString() ?: "",
            localizacao?.longitude?.toString() ?: "", localizacao?.precisaoMetros?.toString() ?: "",
            localizacao?.lidaEmEpochMs?.toString() ?: "", idadeLocalizacaoNaEmissaoMs?.toString() ?: "",
            estadoLocalizacao.name).joinToString("|").also { require(it.toByteArray(Charsets.UTF_8).size <= MAX_BYTES) }
    }

    fun confirmar(no: String, nome: String? = null): Mensagem = Mensagem(
        UUID.randomUUID().toString(), no, TipoMensagem.CONFIRMACAO, referencia = id, destino = origem, nomeOrigem = nome
    )

    fun avancarSalto(): Mensagem = copy(limiteSaltos = limiteSaltos - 1, saltosPercorridos = saltosPercorridos + 1)

    companion object {
        const val MAX_BYTES = 1024
        private fun uuidValido(valor: String) = runCatching { UUID.fromString(valor).toString() == valor }.getOrDefault(false)
        fun sos(no: String, nome: String? = null, emitidoEm: Long? = null, posicao: LocalizacaoSos? = null,
                idadeMs: Long? = null, estado: EstadoLocalizacao = EstadoLocalizacao.INDISPONIVEL) = Mensagem(
            UUID.randomUUID().toString(), no, TipoMensagem.SOS_MANUAL, nomeOrigem = nome, emitidoEmEpochMs = emitidoEm,
            localizacao = posicao, idadeLocalizacaoNaEmissaoMs = idadeMs, estadoLocalizacao = estado)
        fun presenca(no: String, nome: String) = Mensagem(UUID.randomUUID().toString(), no, TipoMensagem.PRESENCA,
            nomeOrigem = AnuncioNo.normalizarNome(nome))

        fun decodificar(bytes: ByteArray): Mensagem {
            require(bytes.size <= MAX_BYTES) { "Mensagem excede o limite em bytes" }
            return decodificar(utf8(bytes))
        }
        private fun utf8(bytes: ByteArray): String = try {
            Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString()
        } catch (e: java.nio.charset.CharacterCodingException) { throw IllegalArgumentException("UTF-8 inválido", e) }

        fun decodificar(texto: String): Mensagem {
            require(texto.toByteArray(Charsets.UTF_8).size <= MAX_BYTES) { "Mensagem excede o limite em bytes" }
            val c = texto.split('|')
            val versao = c.firstOrNull()
            require((versao == "1" && c.size == 7) || (versao == "2" && c.size == 9) || (versao == "3" && c.size == 16)) { "Versão ou formato inválido" }
            val nome = if (c.size >= 9) c[8].takeIf { it.isNotEmpty() }?.let { utf8(Base64.getUrlDecoder().decode(it)) } else null
            var posicao: LocalizacaoSos? = null
            if (versao == "3" && c.subList(10, 14).any { it.isNotEmpty() }) {
                require(c[10].isNotEmpty() && c[11].isNotEmpty() && c[13].isNotEmpty()) { "Posição incompleta" }
                posicao = LocalizacaoSos(c[10].toDouble(), c[11].toDouble(), c[12].takeIf { it.isNotEmpty() }?.toDouble(), c[13].toLong())
            }
            return Mensagem(c[1], c[2], TipoMensagem.valueOf(c[3]), c[4].toInt(), c[5].ifEmpty { null }, c[6].ifEmpty { null },
                if (c.size >= 9) c[7].toInt() else 0, nome,
                if (versao == "3") c[9].takeIf { it.isNotEmpty() }?.toLong() else null, posicao,
                if (versao == "3") c[14].takeIf { it.isNotEmpty() }?.toLong() else null,
                if (versao == "3") EstadoLocalizacao.valueOf(c[15]) else EstadoLocalizacao.INDISPONIVEL)
        }
    }
}

/** Histórico limitado; entradas antigas podem ser expulsas. */
class MensagensVistas(private val capacidade: Int = 256) {
    init { require(capacidade > 0) }
    private val ids = LinkedHashSet<String>()
    @Synchronized fun primeiraVez(id: String): Boolean {
        if (!ids.add(id)) return false
        if (ids.size > capacidade) ids.remove(ids.first())
        return true
    }
}
