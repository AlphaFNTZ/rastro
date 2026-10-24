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

/**
 * Uma linha do registro gravado (CSV de fixos GNSS ou de aquisição) não pôde ser
 * interpretada. A mensagem sempre inclui o número da linha, para o ensaio
 * reprodutível apontar exatamente o dado ruim em vez de descartá-lo em silêncio.
 */
class InvalidRegistroException(mensagem: String) : RastroDomainException(mensagem)

/**
 * A reconciliação não pode ser feita: janela curta demais, intervalo de tempo não
 * positivo, variância nula/não finita ou matriz singular na inversão de A·Σ·Aᵀ.
 */
class InvalidReconciliacaoException(mensagem: String) : RastroDomainException(mensagem)
