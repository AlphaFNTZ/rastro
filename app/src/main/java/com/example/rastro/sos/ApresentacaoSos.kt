package com.example.rastro.sos

import com.example.rastro.network.Mensagem
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

object ApresentacaoSos {
    fun horario(ms: Long?): String = ms?.let {
        DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss z", Locale.getDefault()).withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(it))
    } ?: "não informado (versão antiga)"
    fun posicao(p: LocalizacaoSos?): String = p?.let {
        "%.6f, %.6f\nPrecisão da localização: %s\nLeitura: %s".format(Locale.getDefault(), it.latitude, it.longitude,
            it.precisaoMetros?.let { m -> "%.1f m (68%%)".format(m) } ?: "não informada", horario(it.lidaEmEpochMs))
    } ?: "Localização indisponível"
    fun condicao(m: Mensagem): String = when (m.estadoLocalizacao) {
        EstadoLocalizacao.RECENTE -> "Posição recente na emissão"
        EstadoLocalizacao.ANTIGA -> "Última posição conhecida"
        EstadoLocalizacao.INDISPONIVEL -> "Localização indisponível"
    } + (m.idadeLocalizacaoNaEmissaoMs?.let { " — leitura feita ${it / 1000} s antes da emissão" } ?: "")
    fun resumo(s: SosRecebido?): String {
        if (s == null) return "Nenhum SOS recebido nesta sessão"
        val m = s.mensagem
        return "${m.nomeOrigem ?: "Nó"} [${m.origem.take(8)}] • SOS ${m.id.take(8)}\n" +
            "Emissão: ${horario(m.emitidoEmEpochMs)}\nRecebimento: ${horario(s.recebidoEmEpochMs)}\n" +
            "${condicao(m)}\n${posicao(m.localizacao)}\n" +
            (s.intermediario?.let { "Recebido via ${it.rotulo}; não comprova alcance direto.\n" } ?: "") +
            "A posição pertence ao SOS; não é rastreamento atual. ACK não confirma resgate."
    }
    fun evento(e: EventoRastro): String = "${horario(e.registradoEmEpochMs)} — ${e.aparelho.rotulo}\n" +
        "${e.tipo}: ${e.descricao}" + (e.sosId?.let { " • SOS ${it.take(8)}" } ?: "") +
        (e.mensagem?.takeIf { it.tipo == com.example.rastro.network.TipoMensagem.SOS_MANUAL }?.let {
            "\nEmissão: ${horario(it.emitidoEmEpochMs)}\n${condicao(it)}\n${posicao(it.localizacao)}"
        } ?: "") + (e.intermediario?.let { "\nIntermediário: ${it.rotulo}" } ?: "")
}
