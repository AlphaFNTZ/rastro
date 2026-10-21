package com.example.rastro.av1

import android.graphics.Typeface
import android.os.Bundle
import android.os.SystemClock
import android.text.InputType
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import com.example.rastro.TelaRastroActivity
import com.example.rastro.service.EstadoRastro
import java.io.File
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Tela do MVP da Apresentação I. Mostra, ao vivo, o caminho do dado da AV1:
 *  1. aquisição: contagem de fixos e de leituras IMU crescendo (o serviço continua com a tela bloqueada);
 *  2. reprodução acelerada de um registro gravado, com salto injetado pela semente;
 *  3. resíduo antes/depois da reconciliação e o erro grosseiro sendo detectado, com números.
 *
 * Toda a matemática roda na thread "rastro-reproducao"; a interface só recebe texto pronto.
 * Abre por Configurações → Diagnóstico → "Abrir ensaio AV1", ou com toque longo no
 * status da tela principal.
 */
class EnsaioActivity : TelaRastroActivity() {
    private lateinit var textoAquisicao: TextView
    private lateinit var textoArquivo: TextView
    private lateinit var textoPasso: TextView
    private lateinit var textoEventos: TextView
    private lateinit var campoSemente: EditText
    private lateinit var campoSalto: EditText
    private lateinit var campoAceleracao: EditText
    private lateinit var botaoReproduzir: Button
    private lateinit var botaoParar: Button
    private var arquivo: File? = null
    private val executor: ExecutorService = Executors.newSingleThreadExecutor { Thread(it, "rastro-reproducao") }
    private val cancelar = AtomicBoolean(false)
    private val eventos = ArrayDeque<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val densidade = resources.displayMetrics.density
        fun px(v: Int) = (v * densidade).toInt()
        val raiz = ScrollView(this).apply { id = View.generateViewId() }
        val coluna = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(px(20), px(16), px(20), px(32))
        }
        raiz.addView(coluna)
        fun titulo(t: String) = TextView(this).apply {
            text = t; textSize = 17f; setTypeface(typeface, Typeface.BOLD); setPadding(0, px(18), 0, px(6))
        }
        fun texto(mono: Boolean) = TextView(this).apply {
            textSize = 14f; setTextIsSelectable(true); if (mono) typeface = Typeface.MONOSPACE
        }
        fun rotulo(t: String) = TextView(this).apply { text = t; textSize = 13f; setPadding(0, px(8), 0, 0) }
        fun campo(valor: String, tipo: Int) = EditText(this).apply { setText(valor); inputType = tipo; setSingleLine(true) }
        fun botao(t: String, acao: () -> Unit) = Button(this).apply { text = t; setAllCaps(false); setOnClickListener { acao() } }

        coluna.addView(TextView(this).apply { text = "Ensaio AV1 — sente, corrige e detecta"; textSize = 20f; setTypeface(typeface, Typeface.BOLD) })
        coluna.addView(botao("Voltar") { finish() })

        coluna.addView(titulo("1. Aquisição (camada 1)"))
        textoAquisicao = texto(mono = true)
        coluna.addView(textoAquisicao)

        coluna.addView(titulo("2. Reprodução de um registro gravado"))
        textoArquivo = texto(mono = false)
        coluna.addView(textoArquivo)
        coluna.addView(botao("Escolher registro GNSS") { escolherArquivo() })
        coluna.addView(rotulo("Semente (reprodução determinística)"))
        campoSemente = campo(EvidenciasPadrao.SEMENTE.toString(), InputType.TYPE_CLASS_NUMBER)
        coluna.addView(campoSemente)
        coluna.addView(rotulo("Salto injetado no fixo central, em metros (0 = sem falha)"))
        campoSalto = campo("30", InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL)
        coluna.addView(campoSalto)
        coluna.addView(rotulo("Aceleração da reprodução (vezes o tempo real)"))
        campoAceleracao = campo("10", InputType.TYPE_CLASS_NUMBER)
        coluna.addView(campoAceleracao)
        botaoReproduzir = botao("Reproduzir") { reproduzir() }
        coluna.addView(botaoReproduzir)
        botaoParar = botao("Parar reprodução") { cancelar.set(true) }.apply { isEnabled = false }
        coluna.addView(botaoParar)
        coluna.addView(botao("Comparar com e sem detector") { comparar() })

        coluna.addView(titulo("3. Reconciliação e erro grosseiro"))
        textoPasso = texto(mono = true)
        coluna.addView(textoPasso)
        coluna.addView(titulo("Eventos"))
        textoEventos = texto(mono = true)
        coluna.addView(textoEventos)

        setContentView(raiz)
        aplicarInsets(raiz.id)
        arquivo = registros().firstOrNull()
        atualizarArquivo()
        renderizar(estadoAtual)
    }

    override fun onDestroy() {
        cancelar.set(true)
        executor.shutdownNow()
        super.onDestroy()
    }

    override fun renderizar(estado: EstadoRastro) {
        if (!::textoAquisicao.isInitialized) return
        val r = estado.aquisicao
        textoAquisicao.text = buildString {
            appendLine(if (estado.ativo) "Serviço ativo (continua com a tela bloqueada)" else "Serviço parado: toque em Ativar na tela principal")
            appendLine(r.linhaCurta(SystemClock.elapsedRealtime()))
            appendLine("Fixos de todos os provedores: ${r.fixos}")
            r.arquivoGnss?.let { appendLine("Gravando: $it") }
            r.arquivoAquisicao?.let { appendLine("          $it") }
            r.diretorio?.let { append("Pasta: $it") }
        }
    }

    private fun pastaRegistros(): File = File(getExternalFilesDir(null) ?: filesDir, "registros")

    private fun registros(): List<File> =
        pastaRegistros().listFiles { f -> f.name.startsWith("gnss_") && f.name.endsWith(".csv") }
            ?.sortedByDescending { it.name }.orEmpty()

    private fun atualizarArquivo() {
        textoArquivo.text = arquivo?.let { "Registro: ${it.name} (${it.length() / 1024} KiB)" }
            ?: "Nenhum registro em ${pastaRegistros().absolutePath}. Ative o Rastro para gravar."
    }

    private fun escolherArquivo() {
        val lista = registros()
        if (lista.isEmpty()) { atualizarArquivo(); return }
        AlertDialog.Builder(this).setTitle("Registros GNSS")
            .setItems(lista.map { "${it.name} (${it.length() / 1024} KiB)" }.toTypedArray()) { _, i ->
                arquivo = lista[i]
                atualizarArquivo()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun semente(): Long = campoSemente.text.toString().trim().toLongOrNull() ?: EvidenciasPadrao.SEMENTE
    private fun salto(): Double = campoSalto.text.toString().replace(',', '.').trim().toDoubleOrNull()?.coerceIn(0.0, 1000.0) ?: 0.0

    private fun reproduzir() {
        val f = arquivo ?: run { escolherArquivo(); return }
        val semente = semente()
        val salto = salto()
        val aceleracao = campoAceleracao.text.toString().trim().toDoubleOrNull()?.coerceIn(1.0, 1000.0) ?: 10.0
        cancelar.set(false)
        botaoReproduzir.isEnabled = false
        botaoParar.isEnabled = true
        synchronized(eventos) { eventos.clear() }
        textoEventos.text = ""
        executor.execute {
            try {
                val rep = ReprodutorRegistro(RegistroFixCsv.ler(f.readText(), tolerarUltimaLinha = true), semente = semente, saltoM = salto)
                if (rep.tamanho < rep.tamanhoJanela) {
                    evento("Registro com ${rep.tamanho} fixos GPS reconciliáveis; são necessários ${rep.tamanhoJanela}.")
                    return@execute
                }
                evento("Registro ${f.name}: ${rep.tamanho} fixos GPS · semente $semente · " +
                    (rep.saltoEm?.let { "salto de ${SaidaEvidencias.f(salto, 0)} m injetado no fixo $it" } ?: "sem falha injetada"))
                val jaDetectados = HashSet<Int>()
                for (i in 0 until rep.tamanho) {
                    if (cancelar.get()) { evento("Reprodução interrompida no fixo $i"); break }
                    val passo = rep.passo(i)
                    passo.eliminados.filter { jaDetectados.add(it.indiceOriginal) }.forEach {
                        val origem = if (it.indiceOriginal == rep.saltoEm) "← o salto injetado" else "(não injetado: falso alarme ou erro real do GNSS)"
                        evento("t=${SaidaEvidencias.f(passo.tempoS, 1)} s · ERRO GROSSEIRO no fixo ${it.indiceOriginal}: ${it.medida}, " +
                            "z = ${SaidaEvidencias.f(it.z, 1)} > ${SaidaEvidencias.f(it.limiar, 1)} $origem")
                    }
                    runOnUiThread { mostrarPasso(passo, rep.tamanhoJanela) }
                    Thread.sleep((rep.intervaloAteProximoS(i) / aceleracao * 1000).toLong().coerceIn(10L, 2000L))
                }
                evento("Fim da reprodução: ${jaDetectados.size} fixo(s) eliminado(s) pelo detector")
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
            } catch (e: Exception) {
                evento("Erro: ${e.message}")
            } finally {
                runOnUiThread {
                    if (!isDestroyed) { botaoReproduzir.isEnabled = true; botaoParar.isEnabled = false }
                }
            }
        }
    }

    private fun mostrarPasso(p: PassoReproducao, tamanhoJanela: Int) {
        if (isDestroyed) return
        textoPasso.text = buildString {
            appendLine("Fixo ${p.indice + 1}/${p.total} · t = ${SaidaEvidencias.f(p.tempoS, 1)} s")
            appendLine("Janela deslizante: fixos ${p.janelaInicio}–${p.indice}")
            when {
                p.erro != null -> appendLine("Sem reconciliação: ${p.erro}")
                p.rmsAntesM == null -> appendLine("Aguardando $tamanhoJanela fixos para a primeira janela")
                else -> {
                    appendLine("Resíduo RMS antes : ${SaidaEvidencias.f(p.rmsAntesM, 3)} m")
                    appendLine("Resíduo RMS depois: ${String.format(Locale.ROOT, "%.1e", p.rmsDepoisM ?: 0.0)} m")
                    appendLine("γ = ${SaidaEvidencias.f(p.estatisticaGlobal, 1)} (χ² 95% = ${SaidaEvidencias.f(p.limiarGlobal, 1)})")
                    if (p.eliminados.isEmpty()) appendLine("Erro grosseiro: nenhum nesta janela")
                    else p.eliminados.forEach {
                        appendLine("ERRO GROSSEIRO: fixo ${it.indiceOriginal} (${it.medida}, z = ${SaidaEvidencias.f(it.z, 1)})")
                    }
                }
            }
            p.saltoInjetadoEm?.let { append("Salto injetado no fixo $it") }
        }
    }

    private fun comparar() {
        val f = arquivo ?: run { escolherArquivo(); return }
        val semente = semente()
        val salto = salto().takeIf { it > 0 } ?: 30.0
        executor.execute {
            try {
                val janelas = Janelas.disjuntas(RegistroFixCsv.ler(f.readText(), tolerarUltimaLinha = true), 10)
                if (janelas.isEmpty()) { evento("Sem janelas de 10 fixos GPS contínuos neste registro"); return@execute }
                val linhas = DemonstracaoDetector.calcular(janelas.take(10), DetectorErroGrosseiro(), InjetorFalhas(semente), salto)
                evento("Salto de ${SaidaEvidencias.f(salto, 0)} m no fixo central de ${linhas.size} janela(s), semente $semente:")
                linhas.forEach {
                    evento("  janela ${it.janela}: vizinhos SEM detector ${SaidaEvidencias.f(it.rmsSemDetectorM, 2)} m · " +
                        "COM detector ${SaidaEvidencias.f(it.rmsComDetectorM, 2)} m · ${if (it.localizouCorretamente) "localizado" else "não localizado"}")
                }
                evento("Média: SEM ${SaidaEvidencias.f(linhas.map { it.rmsSemDetectorM }.average(), 2)} m · " +
                    "COM ${SaidaEvidencias.f(linhas.map { it.rmsComDetectorM }.average(), 2)} m")
            } catch (e: Exception) {
                evento("Erro: ${e.message}")
            }
        }
    }

    private fun evento(t: String) {
        val texto = synchronized(eventos) {
            eventos.addFirst(t)
            while (eventos.size > 80) eventos.removeLast()
            eventos.joinToString("\n")
        }
        runOnUiThread { if (!isDestroyed) textoEventos.text = texto }
    }
}
