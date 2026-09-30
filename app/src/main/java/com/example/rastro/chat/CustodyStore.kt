package com.example.rastro.chat

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase

data class HeldPacket(val id: String, val origin: String, val destination: String, val hash: String, val state: String, val remaining: Long, val packet: ByteArray?, val receipt: ByteArray?)
data class CustodySnapshot(val enabled: Boolean, val active: Int, val bytes: Long, val packets: List<HeldPacket>)

/** All operations serialized on ChatRuntime's worker. Only ciphertext of third parties is stored. */
class CustodyStore(private val store: ChatStore, private val clock: () -> CustodyClock) {
    private val db get()=store.writableDatabase
    fun enabled(): Boolean = db.rawQuery("SELECT value FROM custody_settings WHERE key='enabled'",null).use { it.moveToFirst() && it.getInt(0)==1 }
    fun setEnabled(value: Boolean) { db.execSQL("INSERT OR REPLACE INTO custody_settings(key,value) VALUES('enabled',?)",arrayOf(if(value) 1 else 0)) }
    private fun stamp(values: ContentValues, remaining: Long) = values.apply { val now=clock(); put("remaining",remaining); put("wall",now.wall); put("elapsed",now.elapsed); put("boot",now.boot) }
    fun maintain() {
        val now=clock()
        db.beginTransaction()
        try {
            db.rawQuery("SELECT id FROM messages WHERE outgoing=1 AND status NOT IN ('DELIVERED','EXPIRED') AND id NOT IN (SELECT id FROM dispatch)",null).use { c ->
                while(c.moveToNext()) db.insertOrThrow("dispatch",null,stamp(ContentValues().apply { put("id",c.getString(0)); put("carrier",""); put("released",0) },LIFETIME))
            }
            for(table in listOf("dispatch","custody")) {
                val updates=mutableListOf<Pair<String,Long>>()
                db.rawQuery("SELECT id,remaining,wall,elapsed,boot FROM $table",null).use { c -> while(c.moveToNext()) updates.add(c.getString(0) to remainingBudget(c.getLong(1),CustodyClock(c.getLong(2),c.getLong(3),c.getString(4)),now)) }
                updates.forEach { (id,remaining) ->
                    db.update(table,stamp(ContentValues(),remaining),"id=?",arrayOf(id))
                    if(remaining<=0) {
                        if(table=="custody") db.execSQL("UPDATE custody SET state=CASE WHEN state IN ('HELD','RECEIPT') THEN 'EXPIRED' ELSE state END,packet=NULL,receipt=NULL WHERE id=?",arrayOf(id))
                        else db.execSQL("UPDATE messages SET status='EXPIRED' WHERE id=? AND status!='DELIVERED'",arrayOf(id))
                    }
                    if(table=="custody" && remaining<=-LIFETIME) db.delete(table,"id=?",arrayOf(id))
                }
            }
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }
    fun held(id: String): HeldPacket? = db.rawQuery("SELECT id,origin,destination,hash,state,remaining,packet,receipt FROM custody WHERE id=?",arrayOf(id)).use { if(it.moveToFirst()) HeldPacket(it.getString(0),it.getString(1),it.getString(2),it.getString(3),it.getString(4),it.getLong(5),if(it.isNull(6)) null else it.getBlob(6),if(it.isNull(7)) null else it.getBlob(7)) else null }
    fun capacity(origin: String, size: Int): Boolean {
        val totals=db.rawQuery("SELECT count(*),coalesce(sum(coalesce(length(packet),0)+CASE WHEN state='HELD' THEN 12288 ELSE coalesce(length(receipt),0) END),0),sum(CASE WHEN state IN ('HELD','RECEIPT') THEN 1 ELSE 0 END) FROM custody",null).use { it.moveToFirst(); Triple(it.getInt(0),it.getLong(1),it.getInt(2)) }
        val perOrigin=db.rawQuery("SELECT count(*) FROM custody WHERE origin=? AND state IN ('HELD','RECEIPT')",arrayOf(origin)).use { it.moveToFirst(); it.getInt(0) }
        return totals.first<2048 && totals.third<128 && perOrigin<8 && size in 1..12288 && totals.second+size+12288<=2*1024*1024
    }
    fun accept(e: ChatEnvelope, actor: String, remaining: Long): String {
        require(e.from==actor && e.kind=="TEXT" && remaining in 1..LIFETIME)
        db.beginTransaction()
        try {
            val old=held(e.id)
            if(old!=null) { require(old.hash==e.hash() && old.origin==actor); return old.state }
            check(enabled() && capacity(actor,e.encode().size)) { "Custódia indisponível" }
            db.insertOrThrow("custody",null,stamp(ContentValues().apply {
                put("id",e.id); put("origin",e.from); put("destination",e.to); put("hash",e.hash()); put("state","HELD"); put("packet",e.encode()); put("attempted",0)
            },remaining))
            db.setTransactionSuccessful()
            return "HELD"
        } finally { db.endTransaction() }
    }
    fun outgoing(id: String): ChatEnvelope? = db.rawQuery("SELECT envelope FROM messages WHERE id=? AND outgoing=1",arrayOf(id)).use { if(it.moveToFirst()) ChatEnvelope.decode(it.getBlob(0)) else null }
    fun carrier(id: String): String? = db.rawQuery("SELECT carrier FROM dispatch WHERE id=?",arrayOf(id)).use { if(it.moveToFirst()) it.getString(0) else null }
    fun budget(id: String): Long = db.rawQuery("SELECT remaining FROM dispatch WHERE id=?",arrayOf(id)).use { if(it.moveToFirst()) it.getLong(0) else 0 }
    fun bind(id: String, carrier: String): Boolean {
        val changed=db.update("dispatch",ContentValues().apply { put("carrier",carrier) },"id=? AND carrier='' AND remaining>0",arrayOf(id))
        if(changed==1) db.execSQL("UPDATE messages SET status='CUSTODY_PENDING' WHERE id=? AND status NOT IN ('DELIVERED','EXPIRED')",arrayOf(id))
        return this.carrier(id)==carrier
    }
    fun stored(id: String, carrier: String) {
        require(this.carrier(id)==carrier)
        db.execSQL("UPDATE messages SET status='CARRIED' WHERE id=? AND status NOT IN ('DELIVERED','EXPIRED')",arrayOf(id))
    }
    fun receipt(id: String, e: ChatEnvelope, actor: String) {
        val held=requireNotNull(held(id))
        require(held.destination==actor && e.from==actor && e.to==held.origin && e.kind=="ACK")
        require(e.encode().size<=12288)
        if(held.state !in listOf("HELD","RECEIPT")) return
        db.update("custody",ContentValues().apply { put("receipt",e.encode()); put("state","RECEIPT"); put("attempted",0) },"id=?",arrayOf(id))
    }
    fun finish(id: String, actor: String) {
        val held=held(id) ?: return
        require(held.origin==actor)
        remove(id,"DONE")
    }
    fun remove(id: String, state: String="REMOVED") { require(state in listOf("DONE","REMOVED")); db.update("custody",ContentValues().apply { put("state",state); putNull("packet"); putNull("receipt") },"id=?",arrayOf(id)) }
    fun markAttempt(id: String) { db.execSQL("UPDATE custody SET attempted=? WHERE id=?",arrayOf<Any>(clock().wall,id)) }
    fun forPeer(peer: String): List<HeldPacket> = db.rawQuery("SELECT id FROM custody WHERE remaining>0 AND ((state='HELD' AND destination=?) OR (state='RECEIPT' AND origin=?)) ORDER BY attempted,id LIMIT 4",arrayOf(peer,peer)).use { c -> buildList { while(c.moveToNext()) add(requireNotNull(held(c.getString(0)))) } }
    fun releases(peer: String): List<String> = db.rawQuery("SELECT d.id FROM dispatch d JOIN messages m ON m.id=d.id WHERE d.carrier=? AND d.released=0 AND m.status='DELIVERED' LIMIT 4",arrayOf(peer)).use { c -> buildList { while(c.moveToNext()) add(c.getString(0)) } }
    fun released(id: String, actor: String) { require(carrier(id)==actor); db.execSQL("UPDATE dispatch SET released=1 WHERE id=?",arrayOf(id)) }
    fun status(id: String): String? = db.rawQuery("SELECT status FROM messages WHERE id=?",arrayOf(id)).use { if(it.moveToFirst()) it.getString(0) else null }
    fun snapshot(): CustodySnapshot {
        maintain()
        val items=db.rawQuery("SELECT id FROM custody WHERE state IN ('HELD','RECEIPT') ORDER BY remaining,id",null).use { c -> buildList { while(c.moveToNext()) add(requireNotNull(held(c.getString(0)))) } }
        return CustodySnapshot(enabled(),items.size,items.sumOf { (it.packet?.size ?: 0).toLong()+(it.receipt?.size ?: 0) },items)
    }
    companion object {
        const val LIFETIME=7L*24*60*60*1000
        fun create(db: SQLiteDatabase) {
            db.execSQL("CREATE TABLE IF NOT EXISTS dispatch(id TEXT PRIMARY KEY,carrier TEXT NOT NULL,released INTEGER NOT NULL,remaining INTEGER NOT NULL,wall INTEGER NOT NULL,elapsed INTEGER NOT NULL,boot TEXT NOT NULL)")
            db.execSQL("CREATE TABLE IF NOT EXISTS custody(id TEXT PRIMARY KEY,origin TEXT NOT NULL,destination TEXT NOT NULL,hash TEXT NOT NULL,state TEXT NOT NULL,packet BLOB,receipt BLOB,remaining INTEGER NOT NULL,wall INTEGER NOT NULL,elapsed INTEGER NOT NULL,boot TEXT NOT NULL,attempted INTEGER NOT NULL)")
            db.execSQL("CREATE TABLE IF NOT EXISTS custody_settings(key TEXT PRIMARY KEY,value INTEGER NOT NULL)")
        }
    }
}