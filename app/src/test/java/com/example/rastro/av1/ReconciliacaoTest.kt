package com.example.rastro.av1

import com.example.rastro.InvalidReconciliacaoException
import com.example.rastro.InvalidRegistroException
import com.example.rastro.Posicao
import com.example.rastro.s2.CovarianciaGnss
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs

class MatrizTest {
    @Test fun `produto e transposta`() {
        val a = Matriz.de(arrayOf(doubleArrayOf(1.0, 2.0, 3.0), doubleArrayOf(4.0, 5.0, 6.0)))
        val p = a * a.transposta()
        assertArrayEquals(doubleArrayOf(14.0, 32.0), doubleArrayOf(p[0, 0], p[0, 1]), 1e-12)
        assertArrayEquals(doubleArrayOf(32.0, 77.0), doubleArrayOf(p[1, 0], p[1, 1]), 1e-12)
        assertArrayEquals(doubleArrayOf(14.0, 32.0), a * doubleArrayOf(1.0, 2.0, 3.0), 1e-12)
    }

    @Test fun `inversa por Gauss-Jordan com pivotamento devolve a identidade`() {
        // Pivô zero na primeira posição: exige troca de linhas.
        val a = Matriz.de(arrayOf(doubleArrayOf(0.0, 2.0, 1.0), doubleArrayOf(1.0, 1.0, 0.0), doubleArrayOf(3.0, 0.0, 1.0)))
        val i = a * a.inversa()
        for (l in 0..2) for (c in 0..2) assertEquals(if (l == c) 1.0 else 0.0, i[l, c], 1e-12)
    }

    @Test fun `matriz singular lanca excecao especifica`() {
        val a = Matriz.de(arrayOf(doubleArrayOf(1.0, 2.0), doubleArrayOf(2.0, 4.0)))
        assertThrows(InvalidReconciliacaoException::class.java) { a.inversa() }
        assertThrows(InvalidReconciliacaoException::class.java) { a * Matriz.identidade(3) }
    }
}

class RegistroCsvTest {
    @Test fun `ida e volta preserva campos ausentes`() {
        val f = RegistroFix(5_000_000_000, 1_790_000_000_000, -21.22661234, -44.97891234, 3.9, 1.25, 0.4, null, null, "gps")
        val lido = RegistroFixCsv.ler(RegistroFixCsv.CABECALHO + "\n# comentário\n" + RegistroFixCsv.linha(f)).single()
        assertEquals(f, lido)
        assertNull(lido.rumoGraus)
    }

    @Test fun `ultima linha incompleta pode ser tolerada durante a gravacao`() {
        val f = RegistroFix(5_000_000_000, 1_790_000_000_000, -21.2, -44.9, 3.9, 1.25, 0.4, 10.0, 3.0, "gps")
        val texto = RegistroFixCsv.CABECALHO + "\n" + RegistroFixCsv.linha(f) + "\n6000000000,1790000001000,-21.2"
        assertEquals(1, RegistroFixCsv.ler(texto, tolerarUltimaLinha = true).size)
        assertThrows(InvalidRegistroException::class.java) { RegistroFixCsv.ler(texto) }
    }

    @Test fun `linha invalida informa o numero da linha`() {
        val e = assertThrows(InvalidRegistroException::class.java) {
            RegistroFixCsv.ler(RegistroFixCsv.CABECALHO + "\n1,2,3")
        }
        assertTrue(e.message!!.startsWith("linha 2"))
        val neg = assertThrows(InvalidRegistroException::class.java) {
            RegistroFixCsv.ler("1000,1000,-21.2,-44.9,-1,,,,,gps")
        }
        assertTrue(neg.message!!.contains("acuraciaHorizontalM"))
    }

    @Test fun `registro de aquisicao ida e volta`() {
        val r = RegistroAquisicao(2_000_000_000, 1_790_000_000_000, 500, 50, 49.8, 20.1, 31.0, 0, 12, false, true, 87, 31.5)
        val l = RegistroAquisicaoCsv.ler(RegistroAquisicaoCsv.CABECALHO + "\n" + RegistroAquisicaoCsv.linha(r)).single()
        assertEquals(r.copy(imuHz = 49.8, imuDtMedioMs = 20.1), l)
    }
}

class ReconciliadorTest {
    @Test fun `variancia por eixo e a mesma da covariancia da S2`() {
        val p = Posicao(-21.2, -44.9, 1_790_000_000_000, 4.2, 1.0, 0.3)
        assertEquals(CovarianciaGnss.de(p).varianciaHorizontalM2, Estatistica.varianciaPorEixo(4.2), 1e-12)
    }

    @Test fun `apos reconciliar a restricao cinematica e satisfeita e o residuo cai`() {
        repeat(5) { s ->
            val r = Reconciliador.reconciliar(Sintetico.caminhada(10, semente = 100L + s))
            assertTrue("antes ${r.rmsResiduoAntesM}", r.rmsResiduoAntesM > 0.5)
            assertTrue("depois ${r.rmsResiduoDepoisM}", r.rmsResiduoDepoisM < 1e-8)
            assertEquals(18, r.grausLiberdade)
        }
    }

    @Test fun `dt e usado em segundos a partir do relogio em nanossegundos`() {
        // Dois fixos a 1 s; 1,0 m para leste; velocidade 1 m/s para leste nos dois: restrição exata.
        val proj = ProjecaoLocal(Sintetico.LAT0, Sintetico.LON0)
        val g = proj.paraGeografica(1.0, 0.0)
        val a = RegistroFix(1_000_000_000, 1_790_000_000_000, Sintetico.LAT0, Sintetico.LON0, 3.0, 1.0, 0.2, 90.0, 5.0, "gps")
        val b = RegistroFix(2_000_000_000, 1_790_000_001_000, g[0], g[1], 3.0, 1.0, 0.2, 90.0, 5.0, "gps")
        val r = Reconciliador.reconciliar(listOf(a, b))
        assertEquals(0.0, r.rmsResiduoAntesM, 1e-6)
    }

    @Test fun `a medida mais incerta recebe a maior correcao`() {
        val proj = ProjecaoLocal(Sintetico.LAT0, Sintetico.LON0)
        val g = proj.paraGeografica(10.0, 0.0) // deslocamento de 10 m incompatível com velocidade nula
        val preciso = RegistroFix(1_000_000_000, 1_790_000_000_000, Sintetico.LAT0, Sintetico.LON0, 2.0, 0.0, 0.1, null, null, "gps")
        val incerto = RegistroFix(2_000_000_000, 1_790_000_001_000, g[0], g[1], 20.0, 0.0, 0.1, null, null, "gps")
        val r = Reconciliador.reconciliar(listOf(preciso, incerto))
        val corrPreciso = InjetorFalhas.distancia(r.posicaoMedida(0), r.posicaoReconciliada(0))
        val corrIncerto = InjetorFalhas.distancia(r.posicaoMedida(1), r.posicaoReconciliada(1))
        assertTrue("$corrIncerto > $corrPreciso", corrIncerto > 50 * corrPreciso)
        assertEquals(10.0, corrPreciso + corrIncerto, 0.05)
    }

    @Test fun `estatistica global tem media proxima dos graus de liberdade quando o sensor e calibrado`() {
        val razoes = (0 until 300).map { s ->
            val r = Reconciliador.reconciliar(Sintetico.caminhada(10, semente = 1000L + s))
            r.estatisticaGlobal / r.grausLiberdade
        }
        assertEquals(1.0, razoes.average(), 0.12)
    }

    @Test fun `janela invalida lanca excecao especifica`() {
        val f = Sintetico.caminhada(3, 1)
        assertThrows(InvalidReconciliacaoException::class.java) { Reconciliador.reconciliar(f.take(1)) }
        assertThrows(InvalidReconciliacaoException::class.java) { Reconciliador.reconciliar(listOf(f[1], f[0])) }
        assertThrows(InvalidReconciliacaoException::class.java) { Reconciliador.reconciliar(listOf(f[0], f[1].copy(velocidadeMps = null))) }
    }
}

class EstatisticaTest {
    @Test fun `quantis conhecidos`() {
        assertEquals(1.959964, Estatistica.quantilNormal(0.975), 1e-5)
        assertEquals(-2.326348, Estatistica.quantilNormal(0.01), 1e-5)
        assertEquals(28.869, Estatistica.quantilQuiQuadrado(0.95, 18), 0.15)
        assertTrue(Estatistica.limiarSidak(0.05, 40) in 3.1..3.3)
    }
}

class DetectorErroGrosseiroTest {
    private val detector = DetectorErroGrosseiro()

    @Test fun `janela limpa raramente dispara alarme`() {
        val alarmes = (0 until 200).count { s -> detector.analisar(Sintetico.caminhada(10, semente = 5000L + s)).detectou }
        assertTrue("alarmes $alarmes/200", alarmes <= 20)
    }

    @Test fun `salto injetado no interior e detectado e localizado`() {
        val inj = InjetorFalhas(42)
        var localizados = 0
        repeat(50) { s ->
            val limpa = Sintetico.caminhada(10, semente = 7000L + s)
            val idx = inj.indiceInterior(10, "t$s")
            val (suja, _) = inj.salto(limpa, idx, 40.0, "t$s")
            val det = detector.analisar(suja)
            if (det.eliminados.firstOrNull()?.indiceOriginal == idx) localizados++
        }
        assertTrue("localizados $localizados/50", localizados >= 47)
    }

    @Test fun `sem o detector a reconciliacao espalha o salto para os vizinhos`() {
        val janelas = (0 until 10).map { Sintetico.caminhada(10, semente = 9000L + it) }
        val linhas = DemonstracaoDetector.calcular(janelas, detector, InjetorFalhas(7), saltoM = 30.0)
        linhas.forEach {
            assertTrue("janela ${it.janela}: ${it.rmsSemDetectorM} vs ${it.rmsComDetectorM}", it.rmsSemDetectorM > 2 * it.rmsComDetectorM)
            assertTrue(it.localizouCorretamente)
        }
    }
}

class InjetorFalhasTest {
    @Test fun `mesma semente produz a mesma degradacao`() {
        val base = Sintetico.caminhada(30, 3)
        val a = InjetorFalhas(123); val b = InjetorFalhas(123); val c = InjetorFalhas(124)
        assertEquals(a.ruido(base, 3.0), b.ruido(base, 3.0))
        assertEquals(a.perda(base, 0.3), b.perda(base, 0.3))
        assertEquals(a.salto(base, 5, 20.0), b.salto(base, 5, 20.0))
        assertNotEquals(a.ruido(base, 3.0), c.ruido(base, 3.0))
    }

    @Test fun `salto tem exatamente a magnitude pedida e so afeta um fixo`() {
        val base = Sintetico.caminhada(10, 4)
        val (suja, inj) = InjetorFalhas(9).salto(base, 4, 25.0)
        val proj = ProjecaoLocal(base[0].latitude, base[0].longitude)
        val d = InjetorFalhas.distancia(proj.paraLocal(base[4].latitude, base[4].longitude), proj.paraLocal(suja[4].latitude, suja[4].longitude))
        assertEquals(25.0, d, 1e-6)
        assertEquals(4, inj.indice)
        assertEquals(base.filterIndexed { i, _ -> i != 4 }, suja.filterIndexed { i, _ -> i != 4 })
    }

    @Test fun `perda respeita a probabilidade e os protegidos`() {
        val base = Sintetico.caminhada(2000, 5)
        val restante = InjetorFalhas(11).perda(base, 0.3, protegidos = setOf(0, 1999))
        assertEquals(0.7, restante.size / 2000.0, 0.04)
        assertTrue(restante.first() == base.first() && restante.last() == base.last())
    }
}

class EnsaioSensibilidadeTest {
    private val janelas = (0 until 8).map { Sintetico.caminhada(10, semente = 300L + it) }

    @Test fun `ensaio e deterministico com a semente declarada`() {
        val a = EnsaioSensibilidade(20261020L, repeticoes = 5).executar(janelas)
        val b = EnsaioSensibilidade(20261020L, repeticoes = 5).executar(janelas)
        assertEquals(a, b)
    }

    @Test fun `deteccao cresce com o salto e falha para saltos pequenos`() {
        val l = EnsaioSensibilidade(1L, repeticoes = 10).executar(janelas).filter { it.degradacao == "salto" }
        val taxa = l.associate { it.saltoM to it.taxaDeteccao }
        assertTrue(taxa.getValue(100.0) >= 0.95)
        assertTrue(taxa.getValue(2.0) < 0.3)
        assertTrue(l.first { it.saltoM == 0.0 }.taxaFalsoAlarme <= 0.15)
        val minimo = EnsaioSensibilidade.saltoMinimoDetectavel(l)
        assertNotNull(minimo)
        assertTrue(minimo!! in 10.0..50.0)
    }
}

class AnalisesTest {
    @Test fun `razao de otimismo recupera o fator de ruido`() {
        val calibrado = AnaliseEstatica.analisar(Sintetico.estatico(3000, 1, fatorRuido = 1.0))
        val otimista = AnaliseEstatica.analisar(Sintetico.estatico(3000, 2, fatorRuido = 2.0))
        assertEquals(1.0, calibrado.razaoOtimismo, 0.05)
        assertEquals(0.68, calibrado.fracaoDentroRaio68, 0.03)
        assertEquals(2.0, otimista.razaoOtimismo, 0.1)
        assertTrue(otimista.fracaoDentroRaio68 < 0.35)
        assertTrue(SaidaEvidencias.svgDispersao(calibrado, "teste").startsWith("<svg"))
    }

    @Test fun `maior trecho bloqueado respeita tela e lacunas`() {
        val linhas = (0 until 2400).map { s ->
            RegistroAquisicao(1_000_000_000L + s * 1_000_000_000L, 1_790_000_000_000L + s * 1000L, s * 50L, 50, 50.0, 20.0, 25.0,
                0, s / 1L, telaInterativa = s < 300, bloqueado = s >= 300, bateriaPct = 100 - s / 240, temperaturaBateriaC = 30.0)
        }
        val r = AnaliseAquisicao.analisar(linhas)
        assertEquals(35.0, r.bloqueadoMin, 0.05)
        assertTrue(r.atendeCriterio)
        // Uma lacuna de 60 s quebra o trecho em dois de ~17 min: não atende.
        val comLacuna = linhas.filterIndexed { i, _ -> i !in 1300..1360 }
        assertFalse(AnaliseAquisicao.analisar(comLacuna).atendeCriterio)
    }

    @Test fun `corte do gps com a tela apagada aparece na cobertura e no intervalo entre fixos`() {
        // 600 s bloqueado; IMU sem lote nas linhas 100 e 400; gps a 1 Hz exceto entre 200 s e 500 s,
        // quando só chega posição de rede (r68 = 100 m) a cada 20 s.
        val linhas = (0..600).map { s ->
            RegistroAquisicao(1_000_000_000L + s * 1_000_000_000L, 1_790_000_000_000L + s * 1000L, s * 50L,
                if (s == 100 || s == 400) 0 else 50, if (s == 100 || s == 400) 0.0 else 50.0, 20.0, 25.0, 0, s.toLong(),
                telaInterativa = false, bloqueado = true, bateriaPct = 90, temperaturaBateriaC = 30.0)
        }
        fun fixo(s: Int, provedor: String, r68: Double) = RegistroFix(1_000_000_000L + s * 1_000_000_000L,
            1_790_000_000_000L + s * 1000L, Sintetico.LAT0, Sintetico.LON0, r68, 0.0, 0.1, null, null, provedor)
        val fixos = (0..600).filter { it <= 200 || it >= 500 }.map { fixo(it, "gps", 3.0) } +
            (200..500 step 20).map { fixo(it, "network", 100.0) }
        val r = AnaliseAquisicao.analisar(linhas, fixos)
        assertEquals(2, r.janelasImuVazias)
        assertEquals(300.0, r.maiorIntervaloFixosS!!, 1e-9)
        assertEquals(20.0, r.maiorIntervaloQualquerFixoS!!, 1e-9)
        assertEquals(0.5, r.coberturaGps!!, 1e-9)
        assertEquals(100.0, r.r68RedeMedianoM!!, 1e-9)
        assertEquals(302, r.fixosGpsNoTrecho)
    }
}

class JanelasTest {
    @Test fun `janela com posicao retida pelo receptor e reconhecida`() {
        val parado = (0 until 10).map { k -> RegistroFix(1_000_000_000L + k * 1_000_000_000L, 1_790_000_000_000L + k * 1000L,
            Sintetico.LAT0, Sintetico.LON0, 0.5, 0.0, 0.1, null, null, "gps") }
        assertTrue(Janelas.retida(parado))
        // Basta um fixo com posição ou velocidade diferente para a janela ter informação.
        assertFalse(Janelas.retida(parado.mapIndexed { i, f -> if (i == 5) f.copy(longitude = f.longitude + 1e-6) else f }))
        assertFalse(Janelas.retida(parado.mapIndexed { i, f -> if (i == 5) f.copy(velocidadeMps = 0.2) else f }))
        assertFalse(Janelas.retida(Sintetico.caminhada(10, 3)))
    }
}

class ReprodutorRegistroTest {
    @Test fun `reproducao e deterministica e o salto injetado e eliminado`() {
        val registro = Sintetico.caminhada(60, 77)
        fun rodar(): List<PassoReproducao> {
            val rep = ReprodutorRegistro(registro, semente = 5, saltoM = 40.0)
            return (0 until rep.tamanho).map { rep.passo(it) }
        }
        val a = rodar(); val b = rodar()
        assertEquals(a, b)
        val alvo = a.first().saltoInjetadoEm!!
        assertTrue(a.any { p -> p.eliminados.any { it.indiceOriginal == alvo } })
        assertTrue(a.filter { it.rmsDepoisM != null }.all { abs(it.rmsDepoisM!!) < 1e-6 })
    }
}
