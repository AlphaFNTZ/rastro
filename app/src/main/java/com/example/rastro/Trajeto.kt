package com.example.rastro

/**
 * Trajeto — sequência ordenada e imutável de leituras [Posicao], de capacidade fixa.
 *
 * Contrato exigido pela capacidade 1 (Anexo A): responde comprimento, velocidade
 * média e recorte temporal em tempo linear O(n), com memória constante — a
 * estrutura nunca cresce além de [capacidade] elementos, não importa quantos
 * pontos já foram observados no total.
 *
 * Trajeto é um valor imutável: [comNovoPonto] nunca modifica a instância
 * receptora, sempre devolve uma nova instância (janela deslizante funcional).
 * Isso o torna trivialmente seguro para leitura concorrente sem qualquer
 * sincronização — é exatamente a propriedade sobre a qual a capacidade 3
 * (CircularTrackBuffer, região crítica) vai construir a escrita concorrente
 * seguro na Semana 4. O teste de concorrência em TrajetoTest demonstra isso.
 */
class Trajeto private constructor(
    val capacidade: Int,
    private val pontos: List<Posicao>
) {

    /** Número de pontos atualmente armazenados. Nunca excede [capacidade]. O(1). */
    val tamanho: Int
        get() = pontos.size

    fun estaVazio(): Boolean = pontos.isEmpty()

    /**
     * Devolve um NOVO [Trajeto] com [posicao] anexada ao final. Se a capacidade
     * já estiver cheia, o ponto mais antigo é descartado — o que garante memória
     * constante independentemente de quantos pontos já foram observados.
     *
     * @throws InvalidTrajetoException se [posicao] tiver timestamp anterior ao
     *   último ponto já armazenado (violação de ordem cronológica). Timestamps
     *   iguais são aceitos — duas leituras podem chegar no mesmo milissegundo.
     */
    fun comNovoPonto(posicao: Posicao): Trajeto {
        val ultimo = pontos.lastOrNull()
        if (ultimo != null && posicao.timestampMs < ultimo.timestampMs) {
            throw InvalidTrajetoException(
                "Ponto fora de ordem cronológica: novo timestamp ${posicao.timestampMs} " +
                    "é anterior ao último registrado (${ultimo.timestampMs})"
            )
        }

        val tamanhoFinal = minOf(pontos.size + 1, capacidade)
        val descartarAte = (pontos.size + 1) - tamanhoFinal
        val novosPontos = ArrayList<Posicao>(tamanhoFinal)
        for (indice in descartarAte until pontos.size) {
            novosPontos.add(pontos[indice])
        }
        novosPontos.add(posicao)
        return Trajeto(capacidade, novosPontos)
    }

    /**
     * Recorte temporal inclusivo nas duas pontas: todos os pontos com
     * `desdeMs <= timestampMs <= ateMs`. Tempo linear O(n) no tamanho do trajeto
     * (que é limitado por [capacidade], nunca cresce sem limite).
     *
     * @throws InvalidTrajetoException se `desdeMs > ateMs`.
     */
    fun recorteTemporal(desdeMs: Long, ateMs: Long): List<Posicao> {
        if (desdeMs > ateMs) {
            throw InvalidTrajetoException(
                "Intervalo inválido: desdeMs=$desdeMs é posterior a ateMs=$ateMs"
            )
        }
        val resultado = ArrayList<Posicao>()
        for (ponto in pontos) {
            if (ponto.timestampMs in desdeMs..ateMs) {
                resultado.add(ponto)
            }
        }
        return resultado
    }

    /**
     * Velocidade média (m/s) dos pontos armazenados, calculada a partir da
     * velocidade que cada [Posicao] já traz do sensor GNSS (não recalculada por
     * distância/tempo entre pontos). Tempo linear O(n). Devolve 0.0 para trajeto
     * vazio — identidade neutra, não é um erro de domínio.
     */
    fun velocidadeMediaMps(): Double {
        if (pontos.isEmpty()) return 0.0
        var soma = 0.0
        for (ponto in pontos) {
            soma += ponto.velocidadeMps
        }
        return soma / pontos.size
    }

    /** Cópia defensiva dos pontos, em ordem cronológica. O(n). Nunca expõe a lista interna. */
    fun pontosOrdenados(): List<Posicao> = pontos.toList()

    companion object {
        const val CAPACIDADE_MINIMA = 1

        /** Cria um trajeto vazio com capacidade fixa. @throws InvalidTrajetoException se capacidade < 1. */
        fun vazio(capacidade: Int): Trajeto {
            if (capacidade < CAPACIDADE_MINIMA) {
                throw InvalidTrajetoException(
                    "capacidade deve ser >= $CAPACIDADE_MINIMA, recebida: $capacidade"
                )
            }
            return Trajeto(capacidade, emptyList())
        }
    }
}
