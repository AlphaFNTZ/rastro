package com.example.rastro

import android.content.*
import android.os.IBinder
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.rastro.service.EstadoRastro
import com.example.rastro.service.RastroService

/** Cada tela remove somente seu próprio observador ao sair. */
abstract class TelaRastroActivity : AppCompatActivity() {
    protected var servico: RastroService? = null
    protected var estadoAtual = EstadoRastro()
    private var bindSolicitado = false
    private val observador = RastroService.Observador {
        estadoAtual = it
        renderizar(it)
    }
    private val conexao = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            servico = (binder as RastroService.LocalBinder).servico()
            servico?.observar(observador)
        }
        override fun onServiceDisconnected(name: ComponentName?) {
            servico = null
            estadoAtual = EstadoRastro(status = "Serviço encerrado pelo sistema")
            renderizar(estadoAtual)
        }
    }
    override fun onStart() {
        super.onStart()
        bindSolicitado = bindService(Intent(this, RastroService::class.java), conexao, Context.BIND_AUTO_CREATE)
    }
    override fun onStop() {
        servico?.removerObservador(observador)
        if (bindSolicitado) unbindService(conexao)
        bindSolicitado = false
        servico = null
        super.onStop()
    }
    protected fun aplicarInsets(id: Int) {
        ViewCompat.setOnApplyWindowInsetsListener(findViewById<View>(id)) { view, insets ->
            val barras = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime())
            view.setPadding(barras.left, barras.top, barras.right, barras.bottom)
            insets
        }
    }
    protected abstract fun renderizar(estado: EstadoRastro)
}
