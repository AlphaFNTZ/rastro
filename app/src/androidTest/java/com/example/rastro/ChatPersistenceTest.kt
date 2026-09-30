package com.example.rastro

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.rastro.chat.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class ChatPersistenceTest {
    @Test fun durableInboxReceiptAndEncryptedStorage() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val file="chat-test-${UUID.randomUUID()}.db"
        val vault=ChatVault(context)
        val a=ChatCrypto.generate(); val c=ChatCrypto.generate()
        val ac=a.contact("A"); val cc=c.contact("C")
        val text="PRIVATE_SENTINEL_932984"
        val e=a.seal(cc,"TEXT",text.toByteArray())
        val ack=c.seal(ac,"ACK",e.id.toByteArray())
        var store=ChatStore(context,vault,file)
        try {
            store.saveContact(ac)
            assertEquals(ack.hash(),store.incoming(e,text) { ack }.hash())
            store.close(); store=ChatStore(context,ChatVault(context),file)
            assertEquals(ac.id,store.contact(ac.id)?.id)
            assertEquals(text,store.lines(ac.id).single().text)
            assertEquals(ack.hash(),store.incoming(e,text) { error("Não recriar recibo") }.hash())
            assertEquals(1,store.lines(ac.id).size)
            store.readableDatabase.rawQuery("SELECT body FROM messages",null).use { it.moveToFirst(); assertFalse(String(it.getBlob(0),Charsets.ISO_8859_1).contains(text)) }
        } finally { store.close(); context.deleteDatabase(file) }
    }
    @Test fun onlyMatchingReceiptUpdatesOwnMessage() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val file="chat-test-${UUID.randomUUID()}.db"
        val a=ChatCrypto.generate(); val c=ChatCrypto.generate(); val cc=c.contact("C")
        val e=a.seal(cc,"TEXT","olá".toByteArray())
        val store=ChatStore(context,ChatVault(context),file)
        try {
            store.saveContact(cc); store.outgoing(e,"olá")
            assertTrue(runCatching { store.acknowledge("b".repeat(64),e.id,e.hash()) }.isFailure)
            assertTrue(runCatching { store.acknowledge(cc.id,e.id,"0".repeat(64)) }.isFailure)
            assertEquals(1,store.pending().size)
            store.acknowledge(cc.id,e.id,e.hash())
            store.acknowledge(cc.id,e.id,e.hash())
            assertEquals("DELIVERED",store.lines(cc.id).single().status)
            assertTrue(store.pending().isEmpty())
        } finally { store.close(); context.deleteDatabase(file) }
    }
    @Test fun identityReopensAndAadProtectsLocalRows() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val first=ChatVault(context)
        val id=first.identity().contact("local").id
        assertEquals(id,ChatVault(context).identity().contact("local").id)
        val bytes=first.encrypt("secret".toByteArray(),"row1".toByteArray())
        assertTrue(runCatching { first.decrypt(bytes,"row2".toByteArray()) }.isFailure)
    }
}