package com.example.rastro

import android.app.Activity
import android.content.res.Configuration
import android.graphics.Bitmap
import android.view.View
import android.widget.EditText
import androidx.appcompat.app.AppCompatDelegate
import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import com.example.rastro.network.*
import com.example.rastro.service.EstadoRastro
import com.example.rastro.sos.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Dados fictícios apenas no teste: nenhum vizinho ou evento é inserido no serviço. */
@RunWith(AndroidJUnit4::class)
class TelasDesignTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()

    @Test fun telasClarasENavegacao() = verificar(AppCompatDelegate.MODE_NIGHT_NO, "claro")
    @Test fun telasEscurasENavegacao() = verificar(AppCompatDelegate.MODE_NIGHT_YES, "escuro")

    private fun verificar(modo: Int, sufixo: String) {
        instrumentation.runOnMainSync { AppCompatDelegate.setDefaultNightMode(modo) }
        try {
            instrumentation.startActivitySync(Intent(instrumentation.targetContext, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
            instrumentation.waitForIdleSync()
            run {
                verificarView(R.id.btn_ativar_rastro) { isShown }
                instrumentation.runOnMainSync {
                    val noite = atual().resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
                    assertEquals(if (modo == AppCompatDelegate.MODE_NIGHT_YES) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO, noite)
                }
                capturar("principal-inativa-$sufixo")
                clicar(R.id.btn_configuracao)
                verificarView(R.id.card_nome_usuario) { isShown }; clicar(R.id.card_nome_usuario)
                clicarTexto("Cancelar")
                clicar(R.id.card_tempo_atualizacao)
                verificarTexto("Validade da posição recente")
                clicarTexto("Cancelar")
                capturar("configuracao-$sufixo")
                clicar(R.id.btn_voltar)
                verificarView(R.id.nav_item_home) { isSelected }
                clicar(R.id.nav_item_devices)
                verificarView(R.id.nav_item_devices) { isSelected }
                val direto = IdentidadeNo("11111111-1111-1111-1111-111111111111", "Aparelho de teste A")
                val indireto = IdentidadeNo("22222222-2222-2222-2222-222222222222", "Aparelho de teste B")
                val fraco = IdentidadeNo("33333333-3333-3333-3333-333333333333", "Aparelho de teste C")
                val estado = EstadoRastro(ativo = true, nomeLocal = "Juliano", status = "Modo Rastro ativo", vizinhos = listOf(
                    Vizinho("teste-a", direto, EstadoVizinho.CONECTADO, QualidadeEnlace.ALTA),
                    Vizinho("teste-c", fraco, EstadoVizinho.CONECTADO, QualidadeEnlace.BAIXA)),
                    rotasIndiretas = listOf(RotaIndireta(indireto, 2, direto, 0)))
                renderizarFixture(estado)
                capturar("dispositivos-$sufixo")
                clicarTexto(direto.nome)
                clicarTexto("Fechar")
                buscar("sem-correspondencia")
                verificarView(R.id.tv_dispositivos_vazio) { isShown && (this as android.widget.TextView).text == context.getString(R.string.no_search_results) }
                buscar("teste A")
                verificarTexto(direto.nome)
                clicar(R.id.nav_item_history)
                verificarView(R.id.nav_item_history) { isSelected }
                val agora = System.currentTimeMillis()
                renderizarFixture(estado.copy(eventos = listOf(
                    EventoRastro(agora - 86_400_000, TipoEvento.CONEXAO, direto, descricao = "Dispositivo conectado"),
                    EventoRastro(agora - 120_000, TipoEvento.ENVIO, direto, descricao = "Transmitido aos vizinhos"),
                    EventoRastro(agora, TipoEvento.ACK, indireto, descricao = "Recebimento confirmado"))))
                capturar("historico-$sufixo")
                clicarTexto("ACK · Recebimento confirmado")
                clicarTexto("Fechar")
                clicar(R.id.nav_item_home)
                verificarView(R.id.nav_item_home) { isSelected }
                renderizarFixture(estado)
                verificarView(R.id.btn_enviar_sos) { isEnabled }
                capturar("principal-ativa-$sufixo")
                clicar(R.id.btn_configuracao)
                clicar(R.id.card_recarregar_mapa)
                verificarView(R.id.nav_item_home) { isSelected }
            }
        } finally {
            instrumentation.runOnMainSync {
                ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).toList().forEach { it.finishAndRemoveTask() }
            }
            instrumentation.waitForIdleSync()
            instrumentation.runOnMainSync { AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM) }
        }
    }

    private fun verificarView(id: Int, condicao: View.() -> Boolean) {
        instrumentation.waitForIdleSync()
        instrumentation.runOnMainSync { assertTrue(atual().findViewById<View>(id).condicao()) }
    }
    private fun clicar(id: Int) {
        instrumentation.waitForIdleSync()
        instrumentation.runOnMainSync { assertTrue(atual().findViewById<View>(id).performClick()) }
        instrumentation.waitForIdleSync()
        android.os.SystemClock.sleep(350)
    }
    private fun buscar(texto: String) {
        instrumentation.runOnMainSync { atual().findViewById<EditText>(R.id.et_buscar_dispositivos).setText(texto) }
        instrumentation.waitForIdleSync()
    }
    private fun verificarTexto(texto: String) {
        instrumentation.waitForIdleSync()
        assertTrue("Texto não encontrado: $texto", instrumentation.uiAutomation.rootInActiveWindow.findAccessibilityNodeInfosByText(texto).isNotEmpty())
    }
    private fun clicarTexto(texto: String) {
        instrumentation.waitForIdleSync()
        var node = instrumentation.uiAutomation.rootInActiveWindow.findAccessibilityNodeInfosByText(texto).first()
        while (!node.isClickable && node.parent != null) node = node.parent
        assertTrue(node.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK))
        instrumentation.waitForIdleSync()
        android.os.SystemClock.sleep(350)
    }
    private fun atual(): Activity = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).single()
    private fun renderizarFixture(estado: EstadoRastro) {
        instrumentation.waitForIdleSync()
        instrumentation.runOnMainSync {
            val activity = atual()
            activity.javaClass.getDeclaredMethod("renderizar", EstadoRastro::class.java).apply { isAccessible = true }.invoke(activity, estado)
        }
        instrumentation.waitForIdleSync()
    }
    private fun capturar(nome: String) {
        instrumentation.waitForIdleSync()
        // Espera curta apenas para o SurfaceView do mapa e animações de Activity.
        android.os.SystemClock.sleep(500)
        val pasta = File(instrumentation.targetContext.getExternalFilesDir(null), "telas-design").apply { mkdirs() }
        val bitmap = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
        File(pasta, "$nome.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
}
