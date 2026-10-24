package com.example.rastro.chat

import java.io.*
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.security.MessageDigest
import java.util.Base64
import java.util.UUID

internal fun pack(block: DataOutputStream.() -> Unit): ByteArray = ByteArrayOutputStream().also { out -> DataOutputStream(out).use(block) }.toByteArray()
internal fun DataOutputStream.field(bytes: ByteArray) { writeInt(bytes.size); write(bytes) }
internal fun DataInputStream.field(max: Int): ByteArray { val n = readInt(); require(n in 0..max && n <= available()); return ByteArray(n).also { readFully(it) } }
internal fun utf8(bytes: ByteArray): String = Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString()
internal fun unpack(bytes: ByteArray, block: DataInputStream.() -> Unit) { DataInputStream(ByteArrayInputStream(bytes)).use { it.block(); require(it.available() == 0) } }
internal fun uuid(value: String) { require(UUID.fromString(value).toString() == value) }
fun digest(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

/** Independent protocol: never decoded as a legacy SOS packet. */
data class ChatEnvelope(val id: String, val from: String, val to: String, val kind: String, val created: Long, val cipher: ByteArray, val signature: ByteArray) {
    init { uuid(id); require(from.matches(Regex("[0-9a-f]{64}")) && to.matches(Regex("[0-9a-f]{64}"))); require(from != to); require(kind in listOf("TEXT", "ACK")); require(created > 0); require(cipher.size <= 8192 && signature.size <= 256) }
    fun header(): ByteArray = pack { writeUTF("rastro-chat-envelope-1"); writeUTF(id); writeUTF(from); writeUTF(to); writeUTF(kind); writeLong(created) }
    fun signedBytes(): ByteArray = pack { field(header()); field(cipher) }
    fun encode(): ByteArray = pack { field(signedBytes()); field(signature) }
    fun hash(): String = digest(encode())
    companion object {
        const val MAX_TEXT_BYTES = 2048
        fun decode(bytes: ByteArray): ChatEnvelope {
            require(bytes.size <= 12288)
            lateinit var result: ChatEnvelope
            unpack(bytes) {
                val signed = field(11000); val sig = field(256)
                unpack(signed) {
                    val header = field(1024); val cipher = field(8192)
                    unpack(header) { require(readUTF() == "rastro-chat-envelope-1"); result = ChatEnvelope(readUTF(), readUTF(), readUTF(), readUTF(), readLong(), cipher, sig) }
                }
                require(result.encode().contentEquals(bytes))
            }
            return result
        }
    }
}

data class ChatFrame(val attempt: String, val remaining: Int, val envelope: ChatEnvelope) {
    init { uuid(attempt); require(remaining in 0..8) }
    fun encode(): ByteArray = pack { writeInt(MAGIC); writeUTF(attempt); writeInt(remaining); field(envelope.encode()) }
    companion object {
        const val MAGIC = 0x52434831
        const val MAX_BYTES = 16384
        fun recognizes(bytes: ByteArray) = bytes.size >= 4 && ByteBuffer.wrap(bytes).int == MAGIC
        fun decode(bytes: ByteArray): ChatFrame {
            require(bytes.size <= MAX_BYTES)
            lateinit var frame: ChatFrame
            unpack(bytes) { require(readInt() == MAGIC); frame = ChatFrame(readUTF(), readInt(), ChatEnvelope.decode(field(12288))) }
            return frame
        }
    }
}

/** Public material only. The stable ID is bound to both public keys. */
data class ChatContact(val name: String, val encryption: ByteArray, val verification: ByteArray) {
    val id: String get() = digest(pack { field(encryption); field(verification) })
    init { require(name.isNotBlank() && name.toByteArray().size <= 96 && name.none { it.isISOControl() }); require(encryption.size in 1..1024 && verification.size in 1..1024) }
    fun qr(): String = "rastro-contact:1:" + Base64.getUrlEncoder().withoutPadding().encodeToString(pack { field(name.toByteArray()); field(encryption); field(verification) })
    companion object {
        fun parse(qr: String): ChatContact {
            require(qr.length <= 4096 && qr.startsWith("rastro-contact:1:")) { "QR Code não é um contato Rastro compatível" }
            lateinit var c: ChatContact
            unpack(Base64.getUrlDecoder().decode(qr.removePrefix("rastro-contact:1:"))) { c = ChatContact(utf8(field(96)), field(1024), field(1024)) }
            return c
        }
    }
}

/** In-memory relay only: no third-party packet is written to disk. */
class ChatRelay(private val localId: String, private val deliver: (ChatEnvelope) -> Unit, private val transmit: (ChatFrame, String?) -> Unit) {
    private val seen = LinkedHashSet<String>()
    fun receive(frame: ChatFrame, endpoint: String?) {
        val key = frame.attempt + frame.envelope.hash()
        if (!seen.add(key)) return
        if (seen.size > 4096) seen.remove(seen.first())
        if (frame.envelope.to == localId) { deliver(frame.envelope); return }
        if (frame.remaining > 0) transmit(frame.copy(remaining = frame.remaining - 1), endpoint)
    }
    fun originate(envelope: ChatEnvelope) {
        val frame = ChatFrame(UUID.randomUUID().toString(), 8, envelope)
        seen.add(frame.attempt + envelope.hash())
        if (seen.size > 4096) seen.remove(seen.first())
        transmit(frame, null)
    }
}