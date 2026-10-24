package com.example.rastro.chat

import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class ChatProtocolTest {
    private fun rejected(action: () -> Unit) { assertTrue(runCatching(action).isFailure) }
    @Test fun onlyRecipientCanOpenAndPublicQrContainsNoPrivateKeys() {
        val a = ChatCrypto.generate(); val b = ChatCrypto.generate(); val c = ChatCrypto.generate()
        val ac = a.contact("Alfa 🛰"); val cc = c.contact("C")
        val qr = ChatContact.parse(ac.qr()); assertEquals(ac.id,qr.id); ChatCrypto.validate(qr)
        val e = a.seal(cc,"TEXT","Texto privado ç 🛰".toByteArray())
        assertEquals("Texto privado ç 🛰",utf8(c.open(ChatEnvelope.decode(e.encode()),qr)))
        rejected { b.open(e,qr) }
        assertFalse(String(e.encode(),Charsets.ISO_8859_1).contains("Texto privado"))
    }
    @Test fun tamperingWithHeaderCipherOrSignatureIsRejected() {
        val a = ChatCrypto.generate(); val c = ChatCrypto.generate(); val ac = a.contact("A")
        val e = a.seal(c.contact("C"),"TEXT","mensagem".toByteArray())
        val mutations = listOf(e.copy(id=UUID.randomUUID().toString()), e.copy(created=e.created+1), e.copy(kind="ACK"), e.copy(cipher=e.cipher.clone().also { it[it.lastIndex] = (it.last().toInt() xor 1).toByte() }), e.copy(signature=e.signature.clone().also { it[0] = (it[0].toInt() xor 1).toByte() }))
        mutations.forEach { changed -> rejected { c.open(changed,ac) } }
    }
    @Test fun attackerCannotImpersonateKnownSender() {
        val a = ChatCrypto.generate(); val attacker = ChatCrypto.generate(); val c = ChatCrypto.generate()
        val fake = attacker.seal(c.contact("C"),"ACK","forged receipt".toByteArray()).copy(from=a.contact("A").id)
        rejected { c.open(fake,a.contact("A")) }
    }
    @Test fun malformedQrFramesAndTrailingBytesAreRejected() {
        rejected { ChatContact.parse("https://example.com") }
        rejected { ChatContact.parse("rastro-contact:2:aaaa") }
        rejected { ChatContact.parse("rastro-contact:1:" + "x".repeat(4096)) }
        val a = ChatCrypto.generate(); val c = ChatCrypto.generate()
        val e = a.seal(c.contact("C"),"TEXT",byteArrayOf(1))
        rejected { ChatEnvelope.decode(e.encode()+byteArrayOf(0)) }
        rejected { ChatFrame.decode(ByteArray(ChatFrame.MAX_BYTES+1)) }
        rejected { ChatFrame(UUID.randomUUID().toString(),9,e) }
        rejected { utf8(byteArrayOf(0xc3.toByte(),0x28)) }
        val f = ChatFrame(UUID.randomUUID().toString(),8,e)
        assertTrue(ChatFrame.recognizes(f.encode()))
        assertEquals(e.hash(),ChatFrame.decode(f.encode()).envelope.hash())
    }
    @Test fun newKeyHasDifferentIdentityEvenWithSameName() {
        assertNotEquals(ChatCrypto.generate().contact("Igual").id,ChatCrypto.generate().contact("Igual").id)
    }
    @Test fun multiHopPreservesEnvelopeAndDuplicateAttemptIsSuppressed() {
        val a = ChatCrypto.generate(); val c = ChatCrypto.generate(); val e = a.seal(c.contact("C"),"TEXT","Olá".toByteArray())
        var delivered = 0; var forwarded = 0
        val destination = ChatRelay(c.contact("C").id,{ assertEquals(e.hash(),it.hash()); assertEquals("Olá",utf8(c.open(it,a.contact("A")))); delivered++ },{ _,_ -> fail("Destinatário não retransmite") })
        val relay = ChatRelay("b".repeat(64),{ fail("Intermediário não entrega localmente") },{ f, except -> forwarded++; assertEquals("A",except); assertEquals(7,f.remaining); destination.receive(f,"B") })
        val f = ChatFrame(UUID.randomUUID().toString(),8,e)
        relay.receive(f,"A"); relay.receive(f,"A")
        assertEquals(1,delivered); assertEquals(1,forwarded)
        relay.receive(f.copy(attempt=UUID.randomUUID().toString()),"A")
        assertEquals(2,delivered) // Recipient persistence handles duplicates and reissues a lost receipt.
    }
    @Test fun ttlStopsRelayButStillAllowsLocalDelivery() {
        val a = ChatCrypto.generate(); val c = ChatCrypto.generate(); val e = a.seal(c.contact("C"),"TEXT",byteArrayOf(1))
        val frame = ChatFrame(UUID.randomUUID().toString(),0,e)
        ChatRelay("b".repeat(64),{ fail() },{ _,_ -> fail() }).receive(frame,"A")
        var delivered = false
        ChatRelay(e.to,{ delivered=true },{ _,_ -> fail() }).receive(frame,"B")
        assertTrue(delivered)
    }
    @Test fun encryptionIsRandomizedAndMaximumTextFitsFrame() {
        val a=ChatCrypto.generate(); val c=ChatCrypto.generate().contact("C")
        val text = "a".repeat(ChatEnvelope.MAX_TEXT_BYTES).toByteArray()
        val first=a.seal(c,"TEXT",text); val second=a.seal(c,"TEXT",text)
        assertFalse(first.cipher.contentEquals(second.cipher))
        assertTrue(ChatFrame(UUID.randomUUID().toString(),8,first).encode().size <= ChatFrame.MAX_BYTES)
    }
}