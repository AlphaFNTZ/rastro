package com.example.rastro.chat

import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import com.google.crypto.tink.subtle.AesGcmJce
import java.util.UUID

/** Real SQLiteOpenHelper exercised in Android simulation; Keystore has separate device tests. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28], manifest=Config.NONE)
class ChatStoreTest {
    private fun database(test: (ChatStore, String, AesGcmJce) -> Unit) {
        val context = RuntimeEnvironment.getApplication()
        val name = "chat-${UUID.randomUUID()}.db"
        val aead = AesGcmJce(ByteArray(32).also { java.security.SecureRandom().nextBytes(it) })
        val db = ChatStore(context,aead,name)
        try { test(db,name,aead) } finally { db.close(); context.deleteDatabase(name) }
    }
    @Test fun reopeningKeepsContactInboxAndSameReceiptWithoutPlaintext() = database { db,name,key ->
        val a = ChatCrypto.generate(); val c = ChatCrypto.generate(); val ac = a.contact("Contato secreto")
        val text = "PRIVATE_SENTINEL_932984"
        val e = a.seal(c.contact("C"),"TEXT",text.toByteArray())
        val receipt = c.seal(ac,"ACK",byteArrayOf(1))
        db.saveContact(ac); db.incoming(e,text) { receipt }; db.close()
        val reopened = ChatStore(RuntimeEnvironment.getApplication(),key,name)
        try {
            assertEquals(ac.id,reopened.contact(ac.id)?.id)
            assertEquals(text,reopened.lines(ac.id).single().text)
            assertEquals(receipt.hash(),reopened.incoming(e,text) { error("Recibo já existe") }.hash())
            assertEquals(1,reopened.lines(ac.id).size)
            reopened.readableDatabase.rawQuery("SELECT body FROM messages",null).use { it.moveToFirst(); assertFalse(String(it.getBlob(0),Charsets.ISO_8859_1).contains(text)) }
            reopened.readableDatabase.rawQuery("SELECT qr FROM contacts",null).use { it.moveToFirst(); assertFalse(String(it.getBlob(0),Charsets.ISO_8859_1).contains(ac.name)) }
        } finally { reopened.close() }
    }
    @Test fun aliasesDoNotReplaceVerifiedIdentity() = database { db,_,_ ->
        val original=ChatCrypto.generate().contact("Mesmo nome")
        val other=ChatCrypto.generate().contact("Mesmo nome")
        db.saveContact(original); db.alias(original.id,"Equipe Alfa"); db.saveContact(other)
        val entries=db.entries()
        assertEquals(2,entries.size)
        assertEquals("Equipe Alfa",entries.first { it.contact.id == original.id }.alias)
        assertEquals("Mesmo nome",db.contact(original.id)?.name)
    }
    @Test fun encryptedConversationAndAuthenticatedReceiptCrossConnectedRelay() = database { senderDb,_,_ ->
        database { recipientDb,_,_ ->
            val a=ChatCrypto.generate(); val c=ChatCrypto.generate()
            val ac=a.contact("A"); val cc=c.contact("C")
            senderDb.saveContact(cc); recipientDb.saveContact(ac)
            lateinit var ra: ChatRelay; lateinit var rb: ChatRelay; lateinit var rc: ChatRelay
            ra=ChatRelay(ac.id,{ ack ->
                val plain=a.open(ack,cc)
                unpack(plain) { senderDb.acknowledge(ack.from,readUTF(),readUTF()) }
            },{ f,_ -> rb.receive(f,"A") })
            rc=ChatRelay(cc.id,{ message ->
                val text=utf8(c.open(message,ac))
                val receipt=recipientDb.incoming(message,text) { c.seal(ac,"ACK",pack { writeUTF(message.id); writeUTF(message.hash()) }) }
                rc.originate(receipt)
            },{ f,_ -> rb.receive(f,"C") })
            rb=ChatRelay("b".repeat(64),{ error("Intermediário não pode entregar localmente") },{ f,except ->
                if(except != "A") ra.receive(f,"B")
                if(except != "C") rc.receive(f,"B")
            })
            val message=a.seal(cc,"TEXT","Mensagem pela malha".toByteArray())
            senderDb.outgoing(message,"Mensagem pela malha")
            ra.originate(message); ra.originate(message)
            assertEquals("DELIVERED",senderDb.lines(cc.id).single().status)
            assertEquals("Mensagem pela malha",recipientDb.lines(ac.id).single().text)
        }
    }
    @Test fun forgedReferenceOrPeerCannotCompleteOutgoing() = database { db,_,_ ->
        val a=ChatCrypto.generate(); val c=ChatCrypto.generate().contact("C")
        val e=a.seal(c,"TEXT","olá".toByteArray())
        db.saveContact(c); db.outgoing(e,"olá")
        assertTrue(runCatching { db.acknowledge("0".repeat(64),e.id,e.hash()) }.isFailure)
        assertTrue(runCatching { db.acknowledge(c.id,e.id,"0".repeat(64)) }.isFailure)
        assertEquals(1,db.pending().size)
        db.acknowledge(c.id,e.id,e.hash()); db.acknowledge(c.id,e.id,e.hash())
        assertTrue(db.pending().isEmpty())
        assertEquals("DELIVERED",db.lines(c.id).single().status)
    }
    @Test fun failedReceiptCreationRollsBackInboxAndCanRetry() = database { db,_,_ ->
        val a=ChatCrypto.generate(); val c=ChatCrypto.generate(); val ac=a.contact("A")
        val e=a.seal(c.contact("C"),"TEXT","olá".toByteArray())
        db.saveContact(ac)
        assertTrue(runCatching { db.incoming(e,"olá") { error("Falha de gravação simulada") } }.isFailure)
        assertTrue(db.lines(ac.id).isEmpty())
        db.incoming(e,"olá") { c.seal(ac,"ACK",byteArrayOf(1)) }
        assertEquals(1,db.lines(ac.id).size)
        val collision=e.copy(created=e.created+1)
        assertTrue(runCatching { db.incoming(collision,"outro") { error("Nunca") } }.isFailure)
        assertEquals(1,db.lines(ac.id).size)
    }
    @Test fun fullPendingQueueDoesNotDropExistingMessages() = database { db,_,_ ->
        val a=ChatCrypto.generate(); val c=ChatCrypto.generate().contact("C")
        db.saveContact(c)
        repeat(128) { db.outgoing(a.seal(c,"TEXT","$it".toByteArray()),"$it") }
        assertTrue(runCatching { db.outgoing(a.seal(c,"TEXT",byteArrayOf(1)),"excesso") }.isFailure)
        assertEquals(128,db.lines(c.id).size)
    }
}