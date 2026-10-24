package com.example.rastro

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.rastro.chat.*
import com.google.crypto.tink.subtle.AesGcmJce
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

/** Isolated database: never deletes the user's contacts, conversations or custody. */
@RunWith(AndroidJUnit4::class)
class CustodyPersistenceTest {
    @Test fun ciphertextAndReceiptSurviveSeparateDatabaseReopens() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val file="test-custody-${UUID.randomUUID()}.db"
        val aead=AesGcmJce(ByteArray(32).also { java.security.SecureRandom().nextBytes(it) })
        val origin=ChatCrypto.generate(); val destination=ChatCrypto.generate()
        val sender=origin.contact("A"); val receiver=destination.contact("C")
        val packet=origin.seal(receiver,"TEXT","segredo exclusivo do destinatário".toByteArray())
        val clock={ CustodyClock(System.currentTimeMillis(),android.os.SystemClock.elapsedRealtime(),"instrumented-boot") }
        try {
            ChatStore(context,aead,file).use { db ->
                val custody=CustodyStore(db,clock)
                assertFalse(custody.enabled()); custody.setEnabled(true)
                assertEquals("HELD",custody.accept(packet,sender.id,CustodyStore.LIFETIME))
            }
            val receipt=destination.seal(sender,"ACK",pack { writeUTF(packet.id); writeUTF(packet.hash()) })
            ChatStore(context,aead,file).use { db ->
                val custody=CustodyStore(db,clock)
                assertTrue(custody.enabled())
                val stored=requireNotNull(custody.held(packet.id)?.packet)
                assertArrayEquals(packet.encode(),stored)
                assertFalse(String(stored,Charsets.ISO_8859_1).contains("segredo exclusivo"))
                assertEquals("segredo exclusivo do destinatário",String(destination.open(ChatEnvelope.decode(stored),sender)))
                custody.receipt(packet.id,receipt,receiver.id)
                custody.setEnabled(false)
            }
            ChatStore(context,aead,file).use { db ->
                val custody=CustodyStore(db,clock)
                assertFalse(custody.enabled())
                assertEquals("RECEIPT",custody.held(packet.id)?.state)
                assertArrayEquals(receipt.encode(),custody.forPeer(sender.id).single().receipt)
                custody.finish(packet.id,sender.id)
                assertEquals(0,custody.snapshot().active)
                assertEquals("DONE",custody.accept(packet,sender.id,CustodyStore.LIFETIME))
            }
        } finally { context.deleteDatabase(file) }
    }

    @Test fun upgradeKeepsExistingConversationAndIdentityReference() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val file="test-upgrade-${UUID.randomUUID()}.db"
        val aead=AesGcmJce(ByteArray(32).also { java.security.SecureRandom().nextBytes(it) })
        val origin=ChatCrypto.generate(); val recipient=ChatCrypto.generate().contact("Contato")
        val packet=origin.seal(recipient,"TEXT","histórico preservado".toByteArray())
        try {
            ChatStore(context,aead,file).use { db ->
                db.saveContact(recipient); db.outgoing(packet,"histórico preservado")
                db.writableDatabase.execSQL("DROP TABLE dispatch")
                db.writableDatabase.execSQL("DROP TABLE custody")
                db.writableDatabase.execSQL("DROP TABLE custody_settings")
                db.writableDatabase.version=1
            }
            ChatStore(context,aead,file).use { db ->
                assertEquals(3,db.writableDatabase.version)
                assertEquals(recipient.id,db.contact(recipient.id)?.id)
                assertEquals("histórico preservado",db.lines(recipient.id).single().text)
                val custody=CustodyStore(db) { CustodyClock(10000,1000,"boot") }
                custody.maintain()
                assertEquals(CustodyStore.LIFETIME,custody.budget(packet.id))
                assertFalse(custody.enabled())
            }
        } finally { context.deleteDatabase(file) }
    }
}