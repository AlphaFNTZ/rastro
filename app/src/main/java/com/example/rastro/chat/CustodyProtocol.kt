package com.example.rastro.chat

import java.nio.ByteBuffer
import java.util.UUID

/** Direct-only protocol. The receiver's fresh challenge binds controls to this encounter. */
data class CustodyFrame(val action: String, val nonce: String, val id: String, val body: ByteArray, val signature: ByteArray) {
    init { require(action in ACTIONS); uuid(nonce); uuid(id); require(body.size <= 15000 && signature.size <= 256) }
    fun signed(): ByteArray = pack { writeInt(MAGIC); writeUTF(action); writeUTF(nonce); writeUTF(id); field(body) }
    fun encode(): ByteArray = pack { field(signed()); field(signature) }
    companion object {
        const val MAGIC = 0x52435331
        const val MAX_BYTES = 16384
        private val ACTIONS = setOf("CHALLENGE","HELLO","QUERY","NEED","OFFER","STORED","REJECT","DELIVER","RETURN","SAVED","RELEASE","RELEASED")
        fun recognizes(bytes: ByteArray) = bytes.size >= 8 && ByteBuffer.wrap(bytes,4,4).int == MAGIC
        fun decode(bytes: ByteArray): CustodyFrame {
            require(bytes.size <= MAX_BYTES)
            lateinit var result: CustodyFrame
            unpack(bytes) {
                val inner=field(16000); val sig=field(256)
                unpack(inner) { require(readInt()==MAGIC); result=CustodyFrame(readUTF(),readUTF(),readUTF(),field(15000),sig) }
            }
            require(result.encode().contentEquals(bytes))
            return result
        }
        fun challenge(nonce: String) = CustodyFrame("CHALLENGE",nonce,UUID.randomUUID().toString(),byteArrayOf(),byteArrayOf())
    }
}

data class CustodyClock(val wall: Long, val elapsed: Long, val boot: String)

/** Across reboots use nonnegative wall delta; backward/unknown clocks expire conservatively. */
fun remainingBudget(remaining: Long, previous: CustodyClock, now: CustodyClock): Long {
    val delta = if(previous.boot == now.boot && now.elapsed >= previous.elapsed) now.elapsed-previous.elapsed
        else if(now.wall >= previous.wall) now.wall-previous.wall else CustodyStore.LIFETIME
    return (remaining - delta.coerceIn(0, CustodyStore.LIFETIME*3)).coerceAtLeast(-CustodyStore.LIFETIME)
}