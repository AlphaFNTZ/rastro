package com.example.rastro

/**
 * Raiz das exceções de domínio do Rastro. Nunca é lançada diretamente — sempre uma
 * das subclasses abaixo, para que quem trata o erro (Reconciler, Agent, RuleEngine)
 * saiba exatamente qual invariante foi violada. É o que a capacidade 1 chama de
 * "exceção específica", em oposição a uma IllegalArgumentException genérica.
 *
 * É `sealed` de propósito: um `when` exaustivo sobre RastroDomainException é
 * verificado pelo compilador, então uma nova subclasse não passa despercebida
 * em algum tratamento de erro esquecido em outra camada.
 */
sealed class RastroDomainException(mensagem: String) : Exception(mensagem)

/**
 * Uma leitura de sensor — GNSS via [Posicao] ou IMU via [IMURead] — violou uma
 * invariante física conhecida: valor não finito (NaN/infinito), fora da faixa
 * fisicamente plausível, ou acurácia/velocidade negativa.
 */
class InvalidSensorReadException(mensagem: String) : RastroDomainException(mensagem)

/**
 * Uma operação sobre [Trajeto] violou sua invariante estrutural: capacidade
 * inválida na construção, ponto fora de ordem cronológica, ou intervalo de
 * recorte temporal inválido (desde > até).
 */
class InvalidTrajetoException(mensagem: String) : RastroDomainException(mensagem)

/** Configuração inválida da região crítica de leituras. */
class InvalidBufferException(mensagem: String) : RastroDomainException(mensagem)
