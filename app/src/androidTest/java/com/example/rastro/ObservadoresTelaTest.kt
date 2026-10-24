package com.example.rastro

import android.content.*
import android.os.IBinder
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.rastro.service.RastroService
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class ObservadoresTelaTest {
    @Test fun sairDaTelaAnteriorNaoRemoveObservadorDaNovaTela() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val conectado = CountDownLatch(1)
        lateinit var servico: RastroService
        val conexao = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                servico = (binder as RastroService.LocalBinder).servico()
                conectado.countDown()
            }
            override fun onServiceDisconnected(name: ComponentName?) = Unit
        }
        instrumentation.runOnMainSync {
            assertTrue(context.bindService(Intent(context, RastroService::class.java), conexao, Context.BIND_AUTO_CREATE))
        }
        try {
            assertTrue(conectado.await(5, TimeUnit.SECONDS))
            instrumentation.runOnMainSync {
                var primeira = 0
                var segunda = 0
                var ultimoLimite = 0L
                var limiteOriginal = 0L
                val anterior = RastroService.Observador { primeira++; limiteOriginal = it.limitePosicaoRecenteMs }
                val nova = RastroService.Observador { segunda++; ultimoLimite = it.limitePosicaoRecenteMs }
                servico.observar(anterior)
                servico.observar(nova)
                try {
                    assertEquals(1, primeira)
                    assertEquals(1, segunda)
                    servico.removerObservador(anterior)
                    servico.configurarIdadePosicao(30_000)
                    assertEquals(1, primeira)
                    assertTrue(segunda > 1)
                    assertEquals(30_000L, ultimoLimite)
                } finally {
                    servico.removerObservador(anterior)
                    servico.removerObservador(nova)
                    servico.configurarIdadePosicao(limiteOriginal)
                }
            }
        } finally {
            instrumentation.runOnMainSync { context.unbindService(conexao) }
        }
    }
}
