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
 * Resumo da gravaÃ§Ã£o para a tela e para a notificaÃ§Ã£o (que aparece na tela de bloqueio).
 *
 * @property fixos fixos de qualquer provedor desde o inÃ­cio; [fixosGps] sÃ³ do provedor "gps".
 * @property leiturasImu leituras do acelerÃ´metro drenadas do buffer desde o inÃ­cio.
 * @property hzImu taxa efetiva do Ãºltimo segundo.
 * @property inicioMonotonicoMs SystemClock.elapsedRealtime() no inÃ­cio da gravaÃ§Ã£o.
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

    /** Uma linha curta: "Fixos GNSS 1234 Â· IMU 61700 (50 Hz) Â· 00:20:34". */
    fun linhaCurta(agoraMonotonicoMs: Long): String = when {
        erro != null -> "Registro: $erro"
        !gravando -> "Registro de aquisiÃ§Ã£o parado"
        else -> String.format(Locale.ROOT, "Fixos GNSS %d Â· IMU %d (%.0f Hz) Â· %s", fixosGps, leiturasImu, hzImu, duracaoTexto(agoraMonotonicoMs))
    }
}

/**
 * Grava, sem enviar nada para fora do aparelho, os dois registros da AV1:
 *  - gnss_AAAAMMDD_HHMMSS.csv: um fixo por linha ([RegistroFixCsv]), exatamente como o Android entregou;
 *  - aquisicao_AAAAMMDD_HHMMSS.csv: uma linha por segundo ([RegistroAquisicaoCsv]) com leituras IMU,
 *    taxa efetiva, intervalos entre amostras, fixos acumulados, tela/bloqueio e bateria.
 *
 * Os arquivos ficam em Android/data/com.example.rastro/files/registros (Ã¡rea do app; o
 * Android nÃ£o os mostra a outros apps e os apaga na desinstalaÃ§Ã£o). Copie-os por USB.
 *
 * MantÃ©m um PARTIAL_WAKE_LOCK durante a gravaÃ§Ã£o: sem ele, o processador pode dormir com
 * a tela apagada e o acelerÃ´metro (sensor nÃ£o-wakeup) para de entregar amostras, mesmo com
 * o serviÃ§o em primeiro plano. O custo de energia Ã© medido no prÃ³prio registro (bateria_pct).
 *
 * ConcorrÃªncia: o GNSS chama [registrarFix] na thread principal; o consumidor do buffer
 * IMU chama [registrarImu] na thread dele; a tarefa periÃ³dica de 1 s escreve a linha de
 * aquisiÃ§Ã£o na thread "rastro-registro-1hz". Um Ãºnico monitor ([lock]) protege escritores
 * e contadores â€” Ã© uma segunda regiÃ£o crÃ­tica, curta, sem E/S de rede.
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
                resumo = ResumoAquisicao(erro = "nÃ£o foi possÃ­vel criar o arquivo (${e.message})")
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
                // Fixo fisicamente invÃ¡lido: contado, nÃ£o gravado (o CSV sÃ³ aceita leituras vÃ¡lidas).
            } catch (e: IOException) {
                resumo = resumo.copy(erro = "falha ao gravar GNSS (${e.message})")
            }
            resumo = resumo.copy(fixos = fixos, fixosGps = fixosGps)
        }
    }

    /** Recebe o MESMO lote que o consumidor drenou do buffer; nÃ£o toca no buffer. */
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
        // The IMU consumer is also periodic. On a collision, wait outside the lock
        // for a real following sample instead of fabricating a non-empty window.
        val janelaImuVazia = synchronized(lock) { aquisicao != null && imuJanela == 0 }
        if (janelaImuVazia) {
            try { Thread.sleep(ESPERA_IMU_VAZIA_MS) } catch (e: InterruptedException) { Thread.currentThread().interrupt() }
        }
        // LÃª bateria e tela FORA da regiÃ£o crÃ­tica: chamadas de sistema podem demorar.
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
                resumo = resumo.copy(erro = "falha ao gravar aquisiÃ§Ã£o (${e.message})")
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
        // Fora do lock: a tarefa periÃ³dica pode estar esperando por ele.
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
        /** Limite de seguranÃ§a do wake lock; uma sessÃ£o de ensaio nunca passa disso. */
        const val WAKE_LOCK_MAX_MS = 4L * 60 * 60 * 1000
        /** O consumidor IMU inicia em 1 s; o meio segundo evita a corrida da primeira janela. */
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
