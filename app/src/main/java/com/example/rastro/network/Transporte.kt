package com.example.rastro.network

data class Dispositivo(val endereco: String, val nome: String)

interface Transporte : AutoCloseable {
    fun iniciar()
    fun descobrir()
    fun conectar(dispositivo: Dispositivo)
    fun enviar(mensagem: Mensagem): Boolean
    fun desconectar()

    interface Eventos {
        fun status(texto: String)
        fun dispositivos(lista: List<Dispositivo>)
        fun conectado(ativo: Boolean)
        fun recebida(mensagem: Mensagem)
    }
}
