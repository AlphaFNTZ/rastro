package com.example.rastro.network

import java.util.Base64
import java.util.UUID

data class IdentidadeNo(val id: String, val nome: String) {
    val rotulo: String get() = "$nome • ${id.take(8)}"
}

enum class EstadoVizinho { DESCOBERTO, CONECTANDO, CONECTADO }
enum class QualidadeEnlace { DESCONHECIDA, BAIXA, MEDIA, ALTA }

data class Vizinho(
    val endpointId: String,
    val identidade: IdentidadeNo,
    val estado: EstadoVizinho,
    val qualidade: QualidadeEnlace = QualidadeEnlace.DESCONHECIDA
)

data class RotaIndireta(
    val identidade: IdentidadeNo,
    val saltos: Int,
    val via: IdentidadeNo,
    val atualizadoEmMs: Long
)

/** Formato público do endpoint: rastro1|UUID|nome-em-base64url. */
object AnuncioNo {
    private const val VERSAO = "rastro1"
    const val MAX_NOME_BYTES = 32

    fun codificar(id: String, nome: String): String {
        val uuid = UUID.fromString(id).toString()
        val normalizado = normalizarNome(nome)
        val nomeCodificado = Base64.getUrlEncoder().withoutPadding()
            .encodeToString(normalizado.toByteArray(Charsets.UTF_8))
        return "$VERSAO|$uuid|$nomeCodificado"
    }

    fun decodificar(valor: String): IdentidadeNo? = runCatching {
        val partes = valor.split('|')
        require(partes.size == 3 && partes[0] == VERSAO)
        val id = UUID.fromString(partes[1]).toString()
        val bytes = Base64.getUrlDecoder().decode(partes[2])
        require(bytes.size <= MAX_NOME_BYTES)
        val nome = bytes.toString(Charsets.UTF_8)
        require(nome.toByteArray(Charsets.UTF_8).contentEquals(bytes))
        IdentidadeNo(id, normalizarNome(nome))
    }.getOrNull()

    fun normalizarNome(valor: String): String {
        val limpo = valor.map { if (it.isISOControl()) ' ' else it }.joinToString("")
            .trim()
            .replace(Regex("\\s+"), " ")
        require(limpo.isNotEmpty()) { "O nome do dispositivo não pode ficar vazio" }

        val resultado = StringBuilder()
        var indice = 0
        while (indice < limpo.length) {
            val ponto = limpo.codePointAt(indice)
            val trecho = String(Character.toChars(ponto))
            val candidato = resultado.toString() + trecho
            if (candidato.toByteArray(Charsets.UTF_8).size > MAX_NOME_BYTES) break
            resultado.append(trecho)
            indice += Character.charCount(ponto)
        }
        require(resultado.isNotEmpty()) { "Informe um nome válido" }
        return resultado.toString()
    }
}
