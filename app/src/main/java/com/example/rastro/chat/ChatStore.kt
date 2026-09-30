package com.example.rastro.chat

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.google.crypto.tink.Aead

data class ChatContactEntry(val contact: ChatContact, val alias: String, val added: Long, val lastActivity: Long)

data class ChatLine(val id: String, val peer: String, val outgoing: Boolean, val created: Long, val received: Long, val text: String, val status: String)

/** Accessed only on the chat worker. Protected columns use row-specific associated data. */
class ChatStore(context: Context, private val vault: Aead, databaseName: String = "rastro-chat.db") : SQLiteOpenHelper(context, databaseName, null, 2) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE contacts(id TEXT PRIMARY KEY, qr BLOB NOT NULL, added INTEGER NOT NULL, last_seen INTEGER NOT NULL DEFAULT 0, alias BLOB)")
        db.execSQL("CREATE TABLE messages(id TEXT PRIMARY KEY, peer TEXT NOT NULL, outgoing INTEGER NOT NULL, created INTEGER NOT NULL, received INTEGER NOT NULL, body BLOB NOT NULL, envelope BLOB NOT NULL, status TEXT NOT NULL, attempted INTEGER NOT NULL DEFAULT 0, receipt BLOB)")
        db.execSQL("CREATE INDEX messages_peer ON messages(peer, received, id)")
        db.execSQL("CREATE INDEX messages_pending ON messages(outgoing, status, attempted)")
        CustodyStore.create(db)
    }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) { require(oldVersion == 1 && newVersion == 2); CustodyStore.create(db) }
    private fun protect(value: String, aad: String) = vault.encrypt(value.toByteArray(), aad.toByteArray())
    private fun reveal(value: ByteArray, aad: String) = utf8(vault.decrypt(value, aad.toByteArray()))
    fun contacts(): List<ChatContact> = readableDatabase.rawQuery("SELECT id,qr FROM contacts ORDER BY added,id", null).use { c -> buildList { while (c.moveToNext()) add(ChatContact.parse(reveal(c.getBlob(1), "contact:${c.getString(0)}"))) } }
    fun entries(): List<ChatContactEntry> = readableDatabase.rawQuery("SELECT id,qr,alias,added,last_seen FROM contacts ORDER BY added,id", null).use { c ->
        buildList { while(c.moveToNext()) {
            val id = c.getString(0); val contact = ChatContact.parse(reveal(c.getBlob(1), "contact:$id"))
            add(ChatContactEntry(contact, if(c.isNull(2)) contact.name else reveal(c.getBlob(2), "alias:$id"), c.getLong(3), c.getLong(4)))
        } }
    }
    fun alias(id: String, value: String) {
        require(value.isNotBlank() && value.toByteArray().size <= 96 && value.none { it.isISOControl() }) { "Apelido inválido (limite 96 bytes)" }
        check(writableDatabase.update("contacts", ContentValues().apply { put("alias",protect(value,"alias:$id")) },"id=?",arrayOf(id)) == 1)
    }
    fun contact(id: String): ChatContact? = readableDatabase.rawQuery("SELECT qr FROM contacts WHERE id=?", arrayOf(id)).use { if (it.moveToFirst()) ChatContact.parse(reveal(it.getBlob(0), "contact:$id")) else null }
    fun saveContact(c: ChatContact) {
        ChatCrypto.validate(c)
        check(contact(c.id) != null || contacts().size < 256) { "Limite de 256 contatos atingido" }
        val values = ContentValues().apply { put("id", c.id); put("qr", protect(c.qr(), "contact:${c.id}")); put("added", System.currentTimeMillis()) }
        // QR re-scan confirms the same key; it never changes an existing contact's identity.
        if (contact(c.id) == null) writableDatabase.insertOrThrow("contacts", null, values)
        else writableDatabase.update("contacts", ContentValues().apply { put("qr", values.getAsByteArray("qr")) }, "id=?", arrayOf(c.id))
    }
    private fun room() { readableDatabase.rawQuery("SELECT count(*) FROM messages", null).use { it.moveToFirst(); check(it.getInt(0) < 10000) { "Histórico cheio (10.000 mensagens); nenhuma mensagem foi descartada" } } }
    fun outgoing(e: ChatEnvelope, text: String) {
        room()
        readableDatabase.rawQuery("SELECT count(*) FROM messages WHERE outgoing=1 AND status NOT IN ('DELIVERED','EXPIRED')", null).use { it.moveToFirst(); check(it.getInt(0) < 128) { "Aguarde a entrega das mensagens pendentes (limite 128)" } }
        insert(e, text, true, "WAITING", null)
    }
    private fun insert(e: ChatEnvelope, text: String, outgoing: Boolean, status: String, receipt: ChatEnvelope?) {
        writableDatabase.insertOrThrow("messages", null, ContentValues().apply {
            put("id", e.id); put("peer", if(outgoing) e.to else e.from); put("outgoing", if(outgoing) 1 else 0)
            put("created", e.created); put("received", System.currentTimeMillis()); put("body", protect(text, "message:${e.id}")); put("envelope", e.encode()); put("status", status)
            if(receipt != null) put("receipt", receipt.encode())
        })
    }
    /** Atomic inbox + receipt. A retry returns the same durable receipt, not a second chat entry. */
    fun incoming(e: ChatEnvelope, text: String, receipt: () -> ChatEnvelope): ChatEnvelope {
        val db = writableDatabase
        db.beginTransaction()
        try {
            var result: ChatEnvelope? = null
            db.rawQuery("SELECT envelope,receipt,outgoing FROM messages WHERE id=?", arrayOf(e.id)).use {
                if(it.moveToFirst()) {
                    require(it.getInt(2) == 0 && it.getBlob(0).contentEquals(e.encode())) { "ID de mensagem conflitante" }
                    result = ChatEnvelope.decode(it.getBlob(1))
                }
            }
            if(result == null) { room(); result = receipt(); insert(e, text, false, "RECEIVED", result) }
            db.execSQL("UPDATE contacts SET last_seen=? WHERE id=?", arrayOf<Any>(System.currentTimeMillis(), e.from))
            db.setTransactionSuccessful()
            return requireNotNull(result)
        } finally { db.endTransaction() }
    }
    fun acknowledge(sender: String, reference: String, hash: String) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.rawQuery("SELECT envelope,peer,outgoing FROM messages WHERE id=?", arrayOf(reference)).use {
                if(it.moveToFirst()) {
                    require(it.getInt(2) == 1 && it.getString(1) == sender && digest(it.getBlob(0)) == hash) { "Confirmação incompatível" }
                    db.execSQL("UPDATE messages SET status='DELIVERED' WHERE id=?", arrayOf(reference))
                }
            }
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }
    fun pending(): List<ChatEnvelope> = readableDatabase.rawQuery("SELECT envelope FROM messages WHERE outgoing=1 AND status NOT IN ('DELIVERED','EXPIRED') ORDER BY attempted,received LIMIT 4", null).use { c -> buildList { while(c.moveToNext()) add(ChatEnvelope.decode(c.getBlob(0))) } }
    fun attempted(id: String) { writableDatabase.execSQL("UPDATE messages SET attempted=?,status=CASE WHEN status IN ('CUSTODY_PENDING','CARRIED') THEN status ELSE 'AWAITING_ACK' END WHERE id=? AND status NOT IN ('DELIVERED','EXPIRED')", arrayOf<Any>(System.currentTimeMillis(), id)) }
    fun lines(peer: String): List<ChatLine> = readableDatabase.rawQuery("SELECT id,outgoing,created,received,body,status FROM messages WHERE peer=? ORDER BY received DESC,rowid DESC LIMIT 200", arrayOf(peer)).use { c -> buildList { while(c.moveToNext()) add(ChatLine(c.getString(0), peer,c.getInt(1)==1,c.getLong(2),c.getLong(3),reveal(c.getBlob(4),"message:${c.getString(0)}"),c.getString(5))) }.reversed() }
}