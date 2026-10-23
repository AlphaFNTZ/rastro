package com.example.rastro.av1

import kotlin.math.sqrt

/**
 * Resultado do ensaio estático (aparelho parado): dispersão medida contra a acurácia
 * declarada pelo sensor.
 *
 * @property n fixos usados.
 * @property duracaoS duração do registro em segundos.
 * @property sigmaLesteMedidoM, sigmaNorteMedidoM desvio padrão das posições em cada eixo (m).
 * @property sigmaDeclaradoM σ por eixo declarado, √(média de σᵢ²) com σᵢ = r68ᵢ / 1,5096 (m).
 * @property razaoOtimismo √((σE² + σN²)/2) / σ_declarado. Acima de 1: o sensor promete mais
 *   precisão do que entrega (otimista); abaixo de 1: pessimista.
 * @property fracaoDentroRaio68 fração dos fixos a até r68 (o próprio raio declarado) da média.
 *   Se a acurácia estiver calibrada, ≈ 0,68.
 * @property rmsVelocidadeMps RMS da velocidade informada parado (deveria ser ~0).
 * @property rmsAcuraciaVelocidadeMps RMS da acurácia de velocidade declarada.
 * @property fracaoRepetidos fração dos fixos com exatamente a mesma posição do fixo anterior. Alta,
 *   indica que o receptor está retendo a posição com o aparelho parado: aí a dispersão medida
 *   reflete a retenção, não o ruído do receptor.
 * @property pontos (leste, norte, r68) de cada fixo em relação à média, para o gráfico.
 */
data class ResultadoEstatico(
    val n: Int,
    val duracaoS: Double,
    val sigmaLesteMedidoM: Double,
    val sigmaNorteMedidoM: Double,
    val sigmaDeclaradoM: Double,
    val razaoOtimismo: Double,
    val razaoLeste: Double,
    val razaoNorte: Double,
    val fracaoDentroRaio68: Double,
    val raio68MedioM: Double,
    val rmsVelocidadeMps: Double?,
    val rmsAcuraciaVelocidadeMps: Double?,
    val fracaoRepetidos: Double,
    val pontos: List<DoubleArray>
)

object AnaliseEstatica {
    fun analisar(registro: List<RegistroFix>): ResultadoEstatico {
        val gps = registro.filter { it.provedor == "gps" }.ifEmpty { registro }
        val f = gps.filter { (it.acuraciaHorizontalM ?: 0.0) > 0.0 }.sortedBy { it.tempoMonotonicoNs }
        require(f.size >= 10) { "Ensaio estático precisa de pelo menos 10 fixos com acurácia; há ${f.size}" }
        val latM = f.map { it.latitude }.average()
        val lonM = f.map { it.longitude }.average()
        val proj = ProjecaoLocal(latM, lonM)
        val en = f.map { proj.paraLocal(it.latitude, it.longitude) }
        val e = en.map { it[0] }.toDoubleArray()
        val nn = en.map { it[1] }.toDoubleArray()
        val sE = Estatistica.desvioPadrao(e)
        val sN = Estatistica.desvioPadrao(nn)
        val sDecl = sqrt(f.map { Estatistica.varianciaPorEixo(it.acuraciaHorizontalM!!) }.average())
        val sComb = sqrt((sE * sE + sN * sN) / 2)
        val dentro = f.indices.count { i -> sqrt(e[i] * e[i] + nn[i] * nn[i]) <= f[i].acuraciaHorizontalM!! }
        val vel = f.mapNotNull { it.velocidadeMps }.toDoubleArray()
        val accVel = f.mapNotNull { it.acuraciaVelocidadeMps }.toDoubleArray()
        return ResultadoEstatico(
            n = f.size,
            duracaoS = (f.last().tempoMonotonicoNs - f.first().tempoMonotonicoNs) / 1e9,
            sigmaLesteMedidoM = sE, sigmaNorteMedidoM = sN, sigmaDeclaradoM = sDecl,
            razaoOtimismo = sComb / sDecl, razaoLeste = sE / sDecl, razaoNorte = sN / sDecl,
            fracaoDentroRaio68 = dentro.toDouble() / f.size,
            raio68MedioM = f.map { it.acuraciaHorizontalM!! }.average(),
            rmsVelocidadeMps = if (vel.isEmpty()) null else Estatistica.rms(vel),
            rmsAcuraciaVelocidadeMps = if (accVel.isEmpty()) null else Estatistica.rms(accVel),
            fracaoRepetidos = f.zipWithNext().count { (a, b) -> a.latitude == b.latitude && a.longitude == b.longitude }
                .toDouble() / (f.size - 1),
            pontos = f.indices.map { doubleArrayOf(e[it], nn[it], f[it].acuraciaHorizontalM!!) }
        )
    }
}

/**
 * Resultado do ensaio de aquisição com o aparelho bloqueado (capacidade 4).
 *
 * @property duracaoTotalMin duração do registro de aquisição.
 * @property bloqueadoMin maior trecho contínuo em repouso: aparelho bloqueado ou tela apagada.
 * @property maiorLacunaRegistroS maior intervalo entre linhas do registro dentro do trecho
 *   (o consumidor grava a cada 1 s; lacuna grande = tarefa periódica parou).
 * @property linhasNoTrecho linhas do registro dentro desse trecho.
 * @property imuHzMedio, imuHzMinimo taxa efetiva do acelerômetro no trecho (solicitado: 50 Hz), por linha;
 *   as linhas sem lote ficam de fora da média e do mínimo e são contadas em [janelasImuVazias].
 * @property imuDtMaxMs maior intervalo entre amostras IMU no trecho.
 * @property janelasImuVazias linhas do trecho em que nenhum lote IMU chegou (imu_janela = 0). As leituras
 *   não se perdem: vão no lote seguinte. É o efeito de fase entre duas tarefas de 1 s (D-22).
 * @property fixosNoTrecho fixos de posição recebidos no trecho, de qualquer provedor (gps ou rede).
 * @property fixosGpsNoTrecho só os do provedor "gps" (null sem arquivo GNSS).
 * @property coberturaGps fração do trecho coberta por fixos "gps" seguidos (intervalo ≤ 3 s).
 * @property maiorIntervaloFixosS maior intervalo entre fixos "gps" no trecho (null sem arquivo GNSS).
 * @property maiorIntervaloQualquerFixoS maior intervalo entre fixos de qualquer provedor: quanto tempo
 *   o nó ficou sem nenhuma posição nova para anunciar na malha.
 * @property r68RedeMedianoM mediana do r68 declarado dos fixos de rede (não gps) no trecho.
 * @property bateriaInicio, bateriaFim carga em %; consumoPctHora = queda por hora.
 */
data class ResultadoAquisicao(
    val linhas: Int,
    val linhasNoTrecho: Int,
    val duracaoTotalMin: Double,
    val bloqueadoMin: Double,
    val bloqueadoInicioUtcMs: Long?,
    val bloqueadoFimUtcMs: Long?,
    val maiorLacunaRegistroS: Double,
    val imuHzMedio: Double?,
    val imuHzMinimo: Double?,
    val imuDtMaxMs: Double?,
    val imuLeiturasNoTrecho: Long,
    val descartadasNoTrecho: Long,
    val janelasImuVazias: Int,
    val fixosNoTrecho: Long,
    val fixosGpsNoTrecho: Int?,
    val coberturaGps: Double?,
    val maiorIntervaloFixosS: Double?,
    val maiorIntervaloQualquerFixoS: Double?,
    val r68RedeMedianoM: Double?,
    val bateriaInicio: Int?,
    val bateriaFim: Int?,
    val consumoPctHora: Double?,
    val temperaturaMaxC: Double?
) {
    /** Critério declarado no relatório: ≥ 30 min bloqueado, sem lacuna > 5 s e com IMU e GNSS chegando. */
    val atendeCriterio: Boolean
        get() = bloqueadoMin >= 30.0 && maiorLacunaRegistroS <= 5.0 && (imuHzMinimo ?: 0.0) > 0.0 && fixosNoTrecho > 0
}

object AnaliseAquisicao {
    fun analisar(registro: List<RegistroAquisicao>, fixos: List<RegistroFix> = emptyList(), lacunaQuebraS: Double = 5.0): ResultadoAquisicao {
        require(registro.size >= 2) { "Registro de aquisição vazio" }
        val r = registro.sortedBy { it.tempoMonotonicoNs }
        // Maior sequência de linhas em repouso (bloqueado ou tela apagada); a sequência quebra se o
        // aparelho for desbloqueado ou se o registro tiver lacuna maior que lacunaQuebraS (aquisição interrompida).
        val duracao = { a: RegistroAquisicao, b: RegistroAquisicao -> (b.tempoMonotonicoNs - a.tempoMonotonicoNs) / 60e9 }
        var melhorIni = -1
        var melhorFim = -1
        fun fechar(a: Int, b: Int) {
            if (a < 0 || b < a) return
            if (melhorIni < 0 || duracao(r[a], r[b]) > duracao(r[melhorIni], r[melhorFim])) { melhorIni = a; melhorFim = b }
        }
        var ini = -1
        for (i in r.indices) {
            if (i > 0 && (r[i].tempoMonotonicoNs - r[i - 1].tempoMonotonicoNs) / 1e9 > lacunaQuebraS) { fechar(ini, i - 1); ini = -1 }
            if (!r[i].emRepouso) { fechar(ini, i - 1); ini = -1 } else if (ini < 0) ini = i
        }
        fechar(ini, r.lastIndex)
        val trecho = if (melhorIni < 0) emptyList() else r.subList(melhorIni, melhorFim + 1)
        val lacuna = trecho.zipWithNext().maxOfOrNull { (a, b) -> (b.tempoMonotonicoNs - a.tempoMonotonicoNs) / 1e9 } ?: 0.0
        val hz = trecho.drop(1).map { it.imuHz }.filter { it > 0 }
        val noTrecho = if (trecho.isEmpty()) emptyList() else fixos.filter {
            it.tempoMonotonicoNs in trecho.first().tempoMonotonicoNs..trecho.last().tempoMonotonicoNs }.sortedBy { it.tempoMonotonicoNs }
        val gpsNoTrecho = noTrecho.filter { it.provedor == "gps" }
        val intervalo = { a: RegistroFix, b: RegistroFix -> (b.tempoMonotonicoNs - a.tempoMonotonicoNs) / 1e9 }
        val duracaoTrechoS = if (trecho.size >= 2) (trecho.last().tempoMonotonicoNs - trecho.first().tempoMonotonicoNs) / 1e9 else 0.0
        val r68Rede = noTrecho.filter { it.provedor != "gps" }.mapNotNull { it.acuraciaHorizontalM }.sorted()
        val bat0 = trecho.firstOrNull()?.bateriaPct
        val bat1 = trecho.lastOrNull()?.bateriaPct
        val horas = if (trecho.size >= 2) duracao(trecho.first(), trecho.last()) / 60 else 0.0
        return ResultadoAquisicao(
            linhas = r.size,
            linhasNoTrecho = trecho.size,
            duracaoTotalMin = duracao(r.first(), r.last()),
            bloqueadoMin = if (trecho.size >= 2) duracao(trecho.first(), trecho.last()) else 0.0,
            bloqueadoInicioUtcMs = trecho.firstOrNull()?.tempoUtcMs,
            bloqueadoFimUtcMs = trecho.lastOrNull()?.tempoUtcMs,
            maiorLacunaRegistroS = lacuna,
            imuHzMedio = hz.takeIf { it.isNotEmpty() }?.average(),
            imuHzMinimo = hz.minOrNull(),
            imuDtMaxMs = trecho.mapNotNull { it.imuDtMaxMs }.maxOrNull(),
            imuLeiturasNoTrecho = if (trecho.size >= 2) trecho.last().imuTotal - trecho.first().imuTotal else 0,
            descartadasNoTrecho = if (trecho.size >= 2) trecho.last().imuDescartadas - trecho.first().imuDescartadas else 0,
            janelasImuVazias = trecho.drop(1).count { it.imuJanela == 0 },
            fixosNoTrecho = if (trecho.size >= 2) trecho.last().fixosTotal - trecho.first().fixosTotal else 0,
            fixosGpsNoTrecho = if (fixos.isEmpty()) null else gpsNoTrecho.size,
            coberturaGps = if (fixos.isEmpty() || duracaoTrechoS <= 0.0) null else
                gpsNoTrecho.zipWithNext().map { (a, b) -> intervalo(a, b) }.filter { it <= 3.0 }.sum() / duracaoTrechoS,
            maiorIntervaloFixosS = gpsNoTrecho.zipWithNext().maxOfOrNull { (a, b) -> intervalo(a, b) },
            maiorIntervaloQualquerFixoS = noTrecho.zipWithNext().maxOfOrNull { (a, b) -> intervalo(a, b) },
            r68RedeMedianoM = if (r68Rede.isEmpty()) null else
                if (r68Rede.size % 2 == 1) r68Rede[r68Rede.size / 2] else (r68Rede[r68Rede.size / 2 - 1] + r68Rede[r68Rede.size / 2]) / 2,
            bateriaInicio = bat0, bateriaFim = bat1,
            consumoPctHora = if (bat0 != null && bat1 != null && horas > 0.05) (bat0 - bat1) / horas else null,
            temperaturaMaxC = trecho.mapNotNull { it.temperaturaBateriaC }.maxOrNull()
        )
    }
}
