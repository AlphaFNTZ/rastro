package com.example.rastro.sos

/** O relógio injetado é sempre monotônico e pertence somente a este aparelho. */
class ConfirmacoesPendentes(private val agoraMonotonicoMs: () -> Long) {
    private val inicios = LinkedHashMap<String, Long>()
    val size: Int get() = inicios.size
    fun registrar(id: String) { inicios[id] = agoraMonotonicoMs() }
    fun confirmar(id: String?): Long? = inicios.remove(id)?.let { (agoraMonotonicoMs() - it).coerceAtLeast(0) }
    fun expirar(id: String): Boolean = inicios.remove(id) != null
    fun clear() = inicios.clear()
}
