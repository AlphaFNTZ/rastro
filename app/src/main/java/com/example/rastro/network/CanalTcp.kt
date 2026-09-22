package com.example.rastro.network

import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.TimeUnit

/** Um par por sessão. Leitor e escritor dedicados; fila de saída limitada.
 * Fechar sockets interrompe accept/read/connect; nunca há I/O na thread da UI.
 */
class CanalTcp(
    private val servidor: Boolean,
    private val endereco: String,
    private val porta: Int = 8988,
    private val estado: (Boolean, String) -> Unit,
    private val receber: (Mensagem) -> Unit
) : AutoCloseable {
    private val lock = Any()
    private val saida = ArrayBlockingQueue<Mensagem>(64)
    @Volatile private var fechado = false
    @Volatile private var pronto = false
    private var listener: ServerSocket? = null
    private var socket: Socket? = null
    private var escritor: Thread? = null

    fun iniciar() {
        Thread({
            try {
                val conectado = if (servidor) aceitar() else conectar()
                synchronized(lock) {
                    if (fechado) { conectado.close(); return@Thread }
                    socket = conectado
                    conectado.tcpNoDelay = true
                    conectado.keepAlive = true
                    pronto = true
                }
                estado(true, "Canal pronto para SOS")
                val output = DataOutputStream(conectado.getOutputStream())
                escritor = Thread({
                    try {
                        while (!fechado) {
                            val mensagem = saida.poll(1, TimeUnit.SECONDS) ?: continue
                            val bytes = mensagem.codificar().toByteArray(Charsets.UTF_8)
                            output.writeInt(bytes.size)
                            output.write(bytes)
                            output.flush()
                        }
                    } catch (_: InterruptedException) {
                        Thread.currentThread().interrupt()
                    } catch (e: Exception) {
                        if (!fechado) estado(false, "Falha de envio: ${e.message}")
                        close()
                    }
                }, "rastro-tcp-escrita").apply { start() }
                val input = DataInputStream(conectado.getInputStream())
                while (!fechado) {
                    val tamanho = input.readInt()
                    require(tamanho in 1..512) { "Quadro de rede inválido" }
                    val bytes = ByteArray(tamanho)
                    input.readFully(bytes)
                    receber(Mensagem.decodificar(bytes.toString(Charsets.UTF_8)))
                }
            } catch (e: Exception) {
                if (!fechado) estado(false, "Canal encerrado: ${e.message ?: e.javaClass.simpleName}")
            } finally {
                close()
            }
        }, "rastro-tcp-leitura").start()
    }

    private fun aceitar(): Socket {
        val server = ServerSocket()
        synchronized(lock) {
            check(!fechado)
            listener = server
            server.reuseAddress = true
            server.bind(InetSocketAddress(porta))
        }
        return server.accept().also { server.close() }
    }

    private fun conectar(): Socket {
        // O cliente pode obter o grupo antes de o dono abrir o servidor.
        var ultima: Exception? = null
        repeat(5) {
            check(!fechado)
            val client = Socket()
            synchronized(lock) {
                if (fechado) { client.close(); error("Sessão encerrada") }
                socket = client
            }
            try {
                client.connect(InetSocketAddress(endereco, porta), 2000)
                return client
            } catch (e: Exception) {
                ultima = e
                client.close()
                Thread.sleep(400)
            }
        }
        throw checkNotNull(ultima)
    }

    fun enviar(mensagem: Mensagem): Boolean = synchronized(lock) {
        !fechado && pronto && saida.offer(mensagem)
    }

    override fun close() {
        synchronized(lock) {
            if (fechado) return
            fechado = true
            pronto = false
            runCatching { listener?.close() }
            runCatching { socket?.close() }
            saida.clear()
            escritor?.interrupt()
        }
    }
}
