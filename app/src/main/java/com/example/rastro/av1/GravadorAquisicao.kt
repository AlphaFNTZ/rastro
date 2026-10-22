package com.example.rastro.av1

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.location.Location
import android.os.BatteryManager
import android.os.PowerManager
import android.os.SystemClock
import com.example.rastro.s2.LeituraImu
import java.io.BufferedWriter
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

/**
 * Resumo da gravação para a tela e para a notificação (que aparece na tela de bloqueio).
 *
 * @property fixos fixos de qualquer provedor desde o início; [fixosGps] só do provedor "gps".
 * @property leiturasImu leituras do acelerômetro drenadas do buffer desde o início.
 * @property hzImu taxa efetiva do último segundo.
 * @property inicioMonotonicoMs SystemClock.elapsedRealtime() no início da gravação.
 */
data class ResumoAquisicao(
    val gravando: Boolean = false,
    val fixos: Long = 0,
    val fixosGps: Long = 0,
    val leiturasImu: Long = 0,
    val hzImu: Double = 0.0,
    val inicioMonotonicoMs: Long = 0,
    val arquivoGnss: String? = null,
    val arquivoAquisicao: String? = null,
    val diretorio: String? = null,
    val erro: String? = null
) {
    fun duracaoTexto(agoraMonotonicoMs: Long): String {
        if (!gravando || inicioMonotonicoMs == 0L) return "00:00:00"
        val s = (agoraMonotonicoMs - inicioMonotonicoMs) / 1000
        return String.format(Locale.ROOT, "%02d:%02d:%02d", s / 3600, (s / 60) % 60, s % 60)
    }

    /** Uma linha curta: "Fixos GNSS 1234 · IMU 61700 (50 Hz) · 00:20:34". */
    fun linhaCurta(agoraMonotonicoMs: Long): String = when {
        erro != null -> "Registro: $erro"
        !gravando -> "Registro de aquisição parado"
        else -> String.format(Locale.ROOT, "Fixos GNSS %d · IMU %d (%.0f Hz) · %s", fixosGps, leiturasImu, hzImu, duracaoTexto(agoraMonotonicoMs))
    }
}

/**
 * Grava, sem enviar nada para fora do aparelho, os dois registros da AV1:
 *  - gnss_AAAAMMDD_HHMMSS.csv: um fixo por linha ([RegistroFixCsv]), exatamente como o Android entregou;
 *  - aquisicao_AAAAMMDD_HHMMSS.csv: uma linha por segundo ([RegistroAquisicaoCsv]) com leituras IMU,
 *    taxa efetiva, intervalos entre amostras, fixos acumulados, tela/bloqueio e bateria.
 *
 * Os arquivos ficam em Android/data/com.example.rastro/files/registros (área do app; o
 * Android não os mostra a outros apps e os apaga na desinstalação). Copie-os por USB.
 *
 * Mantém um PARTIAL_WAKE_LOCK durante a gravação: sem ele, o processador pode dormir com
 * a tela apagada e o acelerômetro (sensor não-wakeup) para de entregar amostras, mesmo com
 * o serviço em primeiro plano. O custo de energia é medido no próprio registro (bateria_pct).
 *
 * Concorrência: o GNSS chama [registrarFix] na thread principal; o consumidor do buffer
 * IMU chama [registrarImu] na thread dele; a tarefa periódica de 1 s escreve a linha de
 * aquisição na thread "rastro-registro-1hz". Um único monitor ([lock]) protege escritores
 * e contadores — é uma segunda região crítica, curta, sem E/S de rede.
 */
class GravadorAquisicao(private val context: Context) {
    private val lock = Any()
    private var gnss: BufferedWriter? = null
    private var aquisicao: BufferedWriter? = null
    private var periodica: ScheduledExecutorService? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var fixos = 0L
    private var fixosGps = 0L
    private var imuTotal = 0L
    private var imuJanela = 0
    private var imuPrimeiroNs = 0L
    private var imuUltimoNs = 0L
    private var imuAnteriorNs = 0L
    private var imuSomaDtNs = 0L
    private var imuMaxDtNs = 0L
    private var imuIntervalos = 0
    private var descartadas = 0L
    private var hz = 0.0

    @Volatile var resumo = ResumoAquisicao()
        private set

    fun diretorio(): File = File(context.getExternalFilesDir(null) ?: context.filesDir, "registros").also { it.mkdirs() }

    fun iniciar() {
        synchronized(lock) {
            if (gnss != null) return
            val carimbo = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.ROOT).format(Date())
            val dir = diretorio()
            val arqGnss = File(dir, "gnss_$carimbo.csv")
            val arqAq = File(dir, "aquisicao_$carimbo.csv")
            try {
                gnss = arqGnss.bufferedWriter().also { it.write(RegistroFixCsv.CABECALHO); it.newLine(); it.flush() }
                aquisicao = arqAq.bufferedWriter().also { it.write(RegistroAquisicaoCsv.CABECALHO); it.newLine(); it.flush() }
            } catch (e: IOException) {
                fecharEscritores()
                resumo = ResumoAquisicao(erro = "não foi possível criar o arquivo (${e.message})")
                return
            }
            fixos = 0; fixosGps = 0; imuTotal = 0; descartadas = 0; hz = 0.0
            zerarJanelaImu(); imuAnteriorNs = 0L
            wakeLock = context.getSystemService(PowerManager::class.java)
                ?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "rastro:aquisicao")
                ?.also { it.setReferenceCounted(false); it.acquire(WAKE_LOCK_MAX_MS) }
            resumo = ResumoAquisicao(gravando = true, inicioMonotonicoMs = SystemClock.elapsedRealtime(),
                arquivoGnss = arqGnss.name, arquivoAquisicao = arqAq.name, diretorio = dir.absolutePath)
            periodica = Executors.newSingleThreadScheduledExecutor { Thread(it, "rastro-registro-1hz") }.also {
                it.scheduleAtFixedRate({ escreverLinhaAquisicao() }, PRIMEIRO_REGISTRO_MS, 1_000, TimeUnit.MILLISECONDS)
            }
        }
    }

    fun registrarFix(location: Location) {
        synchronized(lock) {
            val w = gnss ?: return
            fixos++
            if (location.provider == "gps") fixosGps++
            try {
                val registro = paraRegistro(location)
                w.write(RegistroFixCsv.linha(registro)); w.newLine(); w.flush()
            } catch (e: com.example.rastro.InvalidSensorReadException) {
                // Fixo fisicamente inválido: contado, não gravado (o CSV só aceita leituras válidas).
            } catch (e: IOException) {
                resumo = resumo.copy(erro = "falha ao gravar GNSS (${e.message})")
            }
            resumo = resumo.copy(fixos = fixos, fixosGps = fixosGps)
        }
    }

    /** Recebe o MESMO lote que o consumidor drenou do buffer; não toca no buffer. */
    fun registrarImu(lote: List<LeituraImu>, descartadasBuffer: Long) {
        synchronized(lock) {
            if (aquisicao == null) return
            descartadas = descartadasBuffer
            for (l in lote) {
                val t = l.timestampMonotonicoNs
                if (imuAnteriorNs != 0L && t > imuAnteriorNs) {
                    val dt = t - imuAnteriorNs
                    imuSomaDtNs += dt; imuIntervalos++
                    if (dt > imuMaxDtNs) imuMaxDtNs = dt
                }
                if (imuPrimeiroNs == 0L) imuPrimeiroNs = t
                imuUltimoNs = t
                imuAnteriorNs = t
            }
            imuJanela += lote.size
            imuTotal += lote.size
        }
    }

    private fun escreverLinhaAquisicao() {
        // O consumidor IMU também é periódico (1 s com atraso fixo) e a fase entre as duas tarefas
        // escorrega devagar. Quando o lote do consumidor ainda não chegou nesta janela, espera
        // FORA do monitor por até ESPERA_IMU_VAZIA_MS pela entrega real, em vez de gravar zero.
        val janelaImuVazia = synchronized(lock) { aquisicao != null && imuJanela == 0 }
        if (janelaImuVazia) {
            try { Thread.sleep(ESPERA_IMU_VAZIA_MS) } catch (e: InterruptedException) { Thread.currentThread().interrupt() }
        }
        // Lê bateria e tela FORA da região crítica: chamadas de sistema podem demorar.
        val agoraNs = SystemClock.elapsedRealtimeNanos()
        val interativa = context.getSystemService(PowerManager::class.java)?.isInteractive ?: true
        val bloqueado = context.getSystemService(KeyguardManager::class.java)?.isKeyguardLocked ?: false
        val bateria = context.getSystemService(BatteryManager::class.java)
            ?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)?.takeIf { it in 0..100 }
        val temperatura = try {
            context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
                ?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE)?.takeIf { it != Int.MIN_VALUE }?.div(10.0)
        } catch (e: RuntimeException) { null }
        synchronized(lock) {
            val w = aquisicao ?: return
            hz = if (imuJanela > 1 && imuUltimoNs > imuPrimeiroNs) (imuJanela - 1) * 1e9 / (imuUltimoNs - imuPrimeiroNs) else 0.0
            val linha = RegistroAquisicao(agoraNs, System.currentTimeMillis(), imuTotal, imuJanela, hz,
                if (imuIntervalos > 0) imuSomaDtNs / imuIntervalos / 1e6 else null,
                if (imuIntervalos > 0) imuMaxDtNs / 1e6 else null,
                descartadas, fixos, interativa, bloqueado, bateria, temperatura)
            try {
                w.write(RegistroAquisicaoCsv.linha(linha)); w.newLine(); w.flush()
            } catch (e: IOException) {
                resumo = resumo.copy(erro = "falha ao gravar aquisição (${e.message})")
            }
            zerarJanelaImu()
            resumo = resumo.copy(leiturasImu = imuTotal, hzImu = hz)
        }
    }

    fun parar() {
        val executor: ScheduledExecutorService?
        synchronized(lock) {
            executor = periodica
            periodica = null
        }
        // Fora do lock: a tarefa periódica pode estar esperando por ele.
        executor?.shutdown()
        try { executor?.awaitTermination(2, TimeUnit.SECONDS) } catch (e: InterruptedException) { Thread.currentThread().interrupt() }
        synchronized(lock) {
            if (gnss == null && aquisicao == null) return
            fecharEscritores()
            wakeLock?.let { if (it.isHeld) it.release() }
            wakeLock = null
            resumo = resumo.copy(gravando = false)
        }
    }

    private fun zerarJanelaImu() {
        imuJanela = 0; imuPrimeiroNs = 0L; imuUltimoNs = 0L; imuSomaDtNs = 0L; imuMaxDtNs = 0L; imuIntervalos = 0
    }

    private fun fecharEscritores() {
        try { gnss?.close() } catch (e: IOException) { }
        try { aquisicao?.close() } catch (e: IOException) { }
        gnss = null
        aquisicao = null
    }

    companion object {
        /** Limite de segurança do wake lock; uma sessão de ensaio nunca passa disso. */
        const val WAKE_LOCK_MAX_MS = 4L * 60 * 60 * 1000
        /** O consumidor IMU começa em 1 s; começar o registro em 1,5 s deixa as duas tarefas meio período defasadas. */
        const val PRIMEIRO_REGISTRO_MS = 1_500L
        private const val ESPERA_IMU_VAZIA_MS = 100L

        fun paraRegistro(l: Location): RegistroFix = RegistroFix(
            tempoMonotonicoNs = l.elapsedRealtimeNanos,
            tempoUtcMs = l.time,
            latitude = l.latitude,
            longitude = l.longitude,
            acuraciaHorizontalM = if (l.hasAccuracy()) l.accuracy.toDouble() else null,
            velocidadeMps = if (l.hasSpeed()) l.speed.toDouble() else null,
            acuraciaVelocidadeMps = if (l.hasSpeedAccuracy()) l.speedAccuracyMetersPerSecond.toDouble() else null,
            rumoGraus = if (l.hasBearing()) l.bearing.toDouble().let { if (it >= 360.0) it - 360.0 else it } else null,
            acuraciaRumoGraus = if (l.hasBearingAccuracy()) l.bearingAccuracyDegrees.toDouble() else null,
            provedor = (l.provider ?: "desconhecido").replace(',', '_').ifBlank { "desconhecido" }
        )
    }
}
