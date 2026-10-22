package com.example.rastro.av1

import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * Gera as evidências da AV1 a partir dos registros REAIS gravados pelo aplicativo.
 *
 * Entrada (copiada do celular, ver docs/ENSAIO_AV1.md):
 *   docs/evidencias/dados/estatico   gnss_AAAAMMDD_HHMMSS.csv — aparelho parado, céu aberto
 *   docs/evidencias/dados/movimento  gnss_AAAAMMDD_HHMMSS.csv — caminhada
 *   docs/evidencias/dados/bloqueado  aquisicao_….csv e gnss_….csv da MESMA sessão, ≥ 30 min com a tela bloqueada
 * Saída: arquivos .md, .json e dispersao_estatica.svg em docs/evidencias/resultados.
 *
 * Cada bloco é pulado (Assume) se os dados correspondentes ainda não existem, para
 * que `gradlew test` continue verde antes da coleta. Comando:
 *   .\gradlew.bat :app:testDebugUnitTest --tests "*EvidenciasAv1Test" --rerun-tasks
 * A semente é fixa ([SEMENTE]) e declarada em cada arquivo gerado.
 */
class EvidenciasAv1Test {
    companion object {
        const val SEMENTE = EvidenciasPadrao.SEMENTE
        const val TAMANHO_JANELA = EvidenciasPadrao.TAMANHO_JANELA
    }

    private val raiz: File? by lazy {
        System.getProperty("rastro.evidencias")?.let { File(it) }
            ?: generateSequence(File(System.getProperty("user.dir")).absoluteFile) { it.parentFile }
                .map { File(it, "docs/evidencias") }.firstOrNull { it.isDirectory }
    }
    private fun dados(sub: String, prefixo: String): List<File> =
        raiz?.let { File(it, "dados/$sub").listFiles { f -> f.name.startsWith(prefixo) && f.name.endsWith(".csv") }?.sortedBy { it.name } }.orEmpty()
    private fun saida(nome: String, conteudo: String) {
        val dir = File(raiz, "resultados").also { it.mkdirs() }
        File(dir, nome).writeText(conteudo, Charsets.UTF_8)
        println("evidência gravada: ${File(dir, nome).path}")
    }
    private fun cabecalho(titulo: String, arquivos: List<File>) = buildString {
        appendLine("# $titulo")
        appendLine()
        appendLine("Gerado por `EvidenciasAv1Test` (comando: `.\\gradlew.bat :app:testDebugUnitTest --tests \"*EvidenciasAv1Test\" --rerun-tasks`).")
        appendLine("Semente: `$SEMENTE`. Dados: ${arquivos.joinToString { "`dados/${it.parentFile.name}/${it.name}`" }}.")
        appendLine()
    }

    @Test fun `ensaio estatico - dispersao e razao de otimismo`() {
        val arquivos = dados("estatico", "gnss_")
        assumeTrue("sem registro estático", arquivos.isNotEmpty())
        val fixos = arquivos.flatMap { RegistroFixCsv.ler(it.readText()) }
        val r = AnaliseEstatica.analisar(fixos)
        saida("estatico.md", cabecalho("Ensaio estático — dispersão e razão de otimismo", arquivos) + SaidaEvidencias.tabelaEstatico(r))
        saida("dispersao_estatica.svg", SaidaEvidencias.svgDispersao(r, "Leituras estáticas GNSS"))
        saida("estatico.json", SaidaEvidencias.json(mapOf("fixos" to r.n, "duracao_min" to r.duracaoS / 60, "r68_medio_m" to r.raio68MedioM,
            "sigma_declarado_m" to r.sigmaDeclaradoM, "sigma_leste_m" to r.sigmaLesteMedidoM, "sigma_norte_m" to r.sigmaNorteMedidoM,
            "razao_otimismo" to r.razaoOtimismo, "fracao_dentro_r68" to r.fracaoDentroRaio68)))
    }

    @Test fun `aquisicao com aparelho bloqueado`() {
        val arquivos = dados("bloqueado", "aquisicao_")
        assumeTrue("sem registro de aquisição", arquivos.isNotEmpty())
        val linhas = arquivos.flatMap { RegistroAquisicaoCsv.ler(it.readText()) }
        val gnss = dados("bloqueado", "gnss_").flatMap { RegistroFixCsv.ler(it.readText()) }
        val r = AnaliseAquisicao.analisar(linhas, gnss)
        saida("aquisicao.md", cabecalho("Aquisição periódica com o aparelho bloqueado", arquivos) + SaidaEvidencias.tabelaAquisicao(r))
        saida("aquisicao.json", SaidaEvidencias.json(mapOf("bloqueado_min" to r.bloqueadoMin, "fixos" to r.fixosNoTrecho,
            "fixos_gps" to r.fixosGpsNoTrecho, "imu_hz_medio" to r.imuHzMedio, "imu_hz_min" to r.imuHzMinimo,
            "imu_linhas_sem_lote" to r.janelasImuVazias, "imu_leituras" to r.imuLeiturasNoTrecho, "imu_descartadas" to r.descartadasNoTrecho,
            "cobertura_gps" to r.coberturaGps, "maior_intervalo_fixos_s" to r.maiorIntervaloFixosS,
            "maior_intervalo_qualquer_fixo_s" to r.maiorIntervaloQualquerFixoS, "r68_rede_mediano_m" to r.r68RedeMedianoM,
            "consumo_pct_h" to r.consumoPctHora, "atende" to r.atendeCriterio)))
    }

    @Test fun `reconciliacao erro grosseiro e sensibilidade em janelas reais`() {
        val arquivos = dados("movimento", "gnss_")
        assumeTrue("sem registro em movimento", arquivos.isNotEmpty())
        val fixos = arquivos.flatMap { RegistroFixCsv.ler(it.readText()) }
        val todas = Janelas.disjuntas(fixos, TAMANHO_JANELA)
        // Janelas com a posição retida pelo receptor (aparelho imóvel) já têm A·y = 0: ficam de fora.
        val janelas = todas.filterNot { Janelas.retida(it) }
        val retidas = todas.size - janelas.size
        assumeTrue("menos de 3 janelas de $TAMANHO_JANELA fixos contínuos", janelas.size >= 3)
        val detector = DetectorErroGrosseiro()
        val residuos = TabelaResiduos.calcular(janelas, detector)
        val demo = DemonstracaoDetector.calcular(janelas, detector, InjetorFalhas(SEMENTE), saltoM = 30.0)
        val ensaio = EnsaioSensibilidade(SEMENTE, detector)
        val sens = ensaio.executar(janelas.take(30))
        val sensRepetida = EnsaioSensibilidade(SEMENTE, detector).executar(janelas.take(30))
        check(sens == sensRepetida) { "Ensaio não determinístico" }
        val minimo = EnsaioSensibilidade.saltoMinimoDetectavel(sens)
        saida("residuos.md", cabecalho("Resíduo das restrições antes e depois da reconciliação", arquivos) +
            "Janelas disjuntas de $TAMANHO_JANELA fixos GPS contínuos (Δt ≤ 3 s). Restrição: pₐ(k+1) − pₐ(k) − ½(vₐ(k)+vₐ(k+1))Δtₖ = 0.\n" +
            "Descartadas $retidas de ${todas.size} janelas com a posição retida pelo receptor (mesma posição e velocidade zero " +
            "em todos os fixos: A·y = 0 já antes de reconciliar). Restam ${janelas.size}.\n\n" +
            SaidaEvidencias.tabelaResiduos(residuos))
        saida("demonstracao_detector.md", cabecalho("Sem o detector, a reconciliação piora", arquivos) +
            "Salto de 30 m injetado no fixo central de cada janela; referência = reconciliação da janela limpa.\n\n" +
            SaidaEvidencias.tabelaDemonstracao(demo))
        saida("sensibilidade.md", cabecalho("Sensibilidade da detecção à degradação injetada", arquivos) +
            "${minOf(30, janelas.size)} janelas reais × ${ensaio.repeticoes} repetições por linha. α = ${detector.alfa} (Šidák por janela).\n" +
            "Menor salto com detecção ≥ 90%: ${minimo?.let { SaidaEvidencias.f(it, 0) + " m" } ?: "nenhum na faixa ensaiada"}.\n\n" +
            SaidaEvidencias.tabelaSensibilidade(sens))
        saida("reconciliacao.json", SaidaEvidencias.json(mapOf(
            "janelas" to janelas.size,
            "janelas_retidas_descartadas" to retidas,
            "rms_antes_medio_m" to residuos.map { it.rmsAntesM }.average(),
            "rms_depois_max_m" to residuos.maxOf { it.rmsDepoisM },
            "janelas_com_eliminacao" to residuos.count { it.eliminados.isNotEmpty() },
            "demo_rms_sem_detector_m" to demo.map { it.rmsSemDetectorM }.average(),
            "demo_rms_com_detector_m" to demo.map { it.rmsComDetectorM }.average(),
            "demo_localizou" to demo.count { it.localizouCorretamente },
            "salto_minimo_90pct_m" to minimo,
            "falso_alarme_controle" to sens.first { it.degradacao == "salto" && it.saltoM == 0.0 }.taxaFalsoAlarme,
            "semente" to SEMENTE)))
    }
}
