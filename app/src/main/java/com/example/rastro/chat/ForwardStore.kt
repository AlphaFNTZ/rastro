package com.example.rastro.chat

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase

data class ForwardLane(val id: String,val message: String,val state: String,val peer: String,val incoming: String,val incomingHash: String,val incomingPeer: String,val chain: ByteArray?)
data class ForwardPacket(val id: String,val origin: String,val destination: String,val hash: String,val kind: String,val state: String,val own: Boolean,val remaining: Long,val initial: Long,val envelope: ByteArray?,val policy: ByteArray?,val proof: ByteArray?)

/** Serialized on the chat worker. Every authorization transition is durable before transmission. */
class ForwardStore(val chat: ChatStore,val delegateIntervalMs: Long=60_000,private val clock: () -> CustodyClock) {
    private val db get()=chat.writableDatabase
    fun <T> atomic(block: () -> T): T {
        db.beginTransaction()
        try { val result=block(); db.setTransactionSuccessful(); return result } finally { db.endTransaction() }
    }
    private fun stamp(v: ContentValues)=v.apply { val c=clock(); put("wall",c.wall); put("elapsed",c.elapsed); put("boot",c.boot) }
    fun maintain() = atomic {
        val now=clock()
        val changes=mutableListOf<Triple<String,Long,Long>>()
        db.rawQuery("SELECT id,remaining,retention,wall,elapsed,boot FROM forward_packets",null).use { c ->
            while(c.moveToNext()) {
                val elapsed=if(c.getString(5)==now.boot && now.elapsed>=c.getLong(4)) now.elapsed-c.getLong(4)
                    else if(now.wall>=c.getLong(3)) now.wall-c.getLong(3) else CustodyStore.LIFETIME
                changes.add(Triple(c.getString(0),(c.getLong(1)-elapsed).coerceAtLeast(0),(c.getLong(2)-elapsed).coerceAtLeast(0)))
            }
        }
        changes.forEach { (id,remaining,retention) ->
            db.update("forward_packets",stamp(ContentValues().apply { put("remaining",remaining); put("retention",retention) }),"id=?",arrayOf(id))
            if(remaining==0L) {
                db.execSQL("UPDATE forward_packets SET state=CASE WHEN state='ACTIVE' THEN 'EXPIRED' ELSE state END,envelope=NULL,policy=NULL WHERE id=?",arrayOf(id))
                db.execSQL("UPDATE forward_lanes SET state=CASE WHEN state IN ('ACTIVE','PENDING','TRANSFERRED') THEN 'EXPIRED' ELSE state END,chain=NULL WHERE message=?",arrayOf(id))
                db.execSQL("UPDATE messages SET status='EXPIRED' WHERE id=? AND outgoing=1 AND status!='DELIVERED'",arrayOf(id))
            }
            if(retention==0L) {
                db.delete("forward_lanes","message=?",arrayOf(id))
                db.delete("forward_packets","id=?",arrayOf(id))
            }
        }
    }
    fun packet(id: String): ForwardPacket? = db.rawQuery("SELECT id,origin,destination,hash,kind,state,own,remaining,initial,envelope,policy,proof FROM forward_packets WHERE id=?",arrayOf(id)).use {
        if(!it.moveToFirst()) null else ForwardPacket(it.getString(0),it.getString(1),it.getString(2),it.getString(3),it.getString(4),it.getString(5),it.getInt(6)==1,it.getLong(7),it.getLong(8),
            if(it.isNull(9)) null else it.getBlob(9),if(it.isNull(10)) null else it.getBlob(10),if(it.isNull(11)) null else it.getBlob(11))
    }
    fun lane(id: String): ForwardLane? = db.rawQuery("SELECT id,message,state,peer,incoming,incoming_hash,incoming_peer,chain FROM forward_lanes WHERE id=?",arrayOf(id)).use {
        if(!it.moveToFirst()) null else ForwardLane(it.getString(0),it.getString(1),it.getString(2),it.getString(3),it.getString(4),it.getString(5),it.getString(6),if(it.isNull(7)) null else it.getBlob(7))
    }
    fun lanes(id: String): List<ForwardLane> = db.rawQuery("SELECT id FROM forward_lanes WHERE message=? ORDER BY id",arrayOf(id)).use { c -> buildList { while(c.moveToNext()) add(requireNotNull(lane(c.getString(0)))) } }
    fun bundle(lane: ForwardLane): ForwardBundle {
        val p=requireNotNull(packet(lane.message))
        return ForwardBundle.from(ChatEnvelope.decode(requireNotNull(p.envelope)),ForwardPolicy.decode(requireNotNull(p.policy)),lane.id,requireNotNull(lane.chain))
    }
    fun capacity(origin: String,own: Boolean=false,kind: String="TEXT"): Boolean {
        val existing=db.rawQuery("SELECT count(*),coalesce(sum(CASE WHEN state='ACTIVE' THEN 32768 ELSE coalesce(length(proof),0) END),0),coalesce(sum(CASE WHEN state='ACTIVE' THEN 1 ELSE 0 END),0) FROM forward_packets",null).use { it.moveToFirst(); Triple(it.getInt(0),it.getLong(1),it.getInt(2)) }
        val legacy=db.rawQuery("SELECT count(*),coalesce(sum(coalesce(length(packet),0)+CASE WHEN state='HELD' THEN 12288 ELSE coalesce(length(receipt),0) END),0),coalesce(sum(CASE WHEN state IN ('HELD','RECEIPT') THEN 1 ELSE 0 END),0) FROM custody",null).use { it.moveToFirst(); Triple(it.getInt(0),it.getLong(1),it.getInt(2)) }
        val perOrigin=db.rawQuery("SELECT (SELECT count(*) FROM forward_packets WHERE origin=? AND state='ACTIVE' AND own=0)+(SELECT count(*) FROM custody WHERE origin=? AND state IN ('HELD','RECEIPT'))",arrayOf(origin,origin)).use { it.moveToFirst(); it.getInt(0) }
        return existing.first+legacy.first<2048 && existing.third+legacy.third<128 &&
            existing.second+legacy.second+RESERVATION<=(if(kind=="ACK") 2*1024*1024 else 1536*1024) && (own || perOrigin<8)
    }
    private fun insertPacket(bundle: ForwardBundle,remaining: Long,own: Boolean) {
        val e=bundle.envelope
        check(capacity(e.from,own,e.kind)) { "Caixa de transporte cheia; nenhuma autorização foi descartada" }
        db.insertOrThrow("forward_packets",null,stamp(ContentValues().apply {
            put("id",e.id); put("origin",e.from); put("destination",e.to); put("hash",e.hash()); put("kind",e.kind)
            put("state","ACTIVE"); put("own",if(own) 1 else 0); put("remaining",remaining); put("initial",remaining)
            put("retention",remaining+RETENTION); put("envelope",e.encode()); put("policy",bundle.policy.encode()); put("attempted",0)
        }))
    }
    private fun insertLane(bundle: ForwardBundle,incomingPeer: String="") {
        db.insertOrThrow("forward_lanes",null,ContentValues().apply {
            put("id",bundle.lane); put("message",bundle.envelope.id); put("state","ACTIVE"); put("peer","")
            put("incoming",bundle.hops.lastOrNull()?.transaction ?: "")
            put("incoming_hash",if(bundle.hops.isEmpty()) "" else digest(bundle.encode()))
            put("incoming_peer",incomingPeer); put("chain",bundle.chain())
        })
    }
    fun createOwn(crypto: ChatCrypto,e: ChatEnvelope,reference: String="",hash: String=""): ForwardBundle = atomic {
        require(packet(e.id)==null)
        val policy=ForwardPolicy.create(crypto,e,reference,hash)
        val bundles=policy.lanes.map { ForwardBundle(e,policy,it,emptyList()) }
        bundles.first().validate()
        insertPacket(bundles.first(),CustodyStore.LIFETIME,true)
        bundles.forEach { insertLane(it) }
        bundles.first()
    }
    /** The inbox's persisted proof prevents receipt budgets being recreated after expiry or removal. */
    fun receipt(crypto: ChatCrypto,original: ChatEnvelope,ack: ChatEnvelope): ForwardBundle = atomic {
        proofFor(original.id)?.let { return@atomic it }
        require(ack.kind=="ACK" && ack.from==original.to && ack.to==original.from)
        val proof=createOwn(crypto,ack,original.id,original.hash())
        check(db.update("messages",ContentValues().apply { put("forward_receipt",proof.encode()) },"id=? AND outgoing=0",arrayOf(original.id))==1)
        proof
    }
    fun proofFor(message: String): ForwardBundle? = db.rawQuery("SELECT forward_receipt FROM messages WHERE id=? AND outgoing=0",arrayOf(message)).use {
        if(it.moveToFirst() && !it.isNull(0)) ForwardBundle.decode(it.getBlob(0)) else null
    }
    fun reserve(id: String,peer: String,crypto: ChatCrypto): ForwardBundle = atomic {
        val lane=requireNotNull(lane(id)); val packet=requireNotNull(packet(lane.message))
        check(packet.state=="ACTIVE" && packet.remaining>0)
        if(lane.state=="PENDING") {
            require(lane.peer==peer); return@atomic bundle(lane)
        }
        check(lane.state=="ACTIVE" && (packet.own || packet.initial-packet.remaining>=delegateIntervalMs))
        val grant=bundle(lane).delegate(crypto,peer,packet.remaining)
        check(db.update("forward_lanes",ContentValues().apply { put("state","PENDING"); put("peer",peer); put("chain",grant.chain()) },"id=? AND state='ACTIVE'",arrayOf(id))==1)
        db.execSQL("UPDATE messages SET status='FORWARD_PENDING' WHERE id=? AND outgoing=1 AND status NOT IN ('DELIVERED','EXPIRED','FORWARDED')",arrayOf(packet.id))
        grant
    }
    fun accept(bundle: ForwardBundle,peer: String,local: String,remaining: Long): String = atomic {
        bundle.validate()
        require(bundle.hops.isNotEmpty() && bundle.owner()==local && bundle.hops.last().signer.id==peer)
        require(remaining in 1..bundle.hops.last().remaining)
        val oldLane=lane(bundle.lane)
        if(oldLane!=null) {
            require(oldLane.message==bundle.envelope.id && oldLane.incoming==bundle.hops.last().transaction &&
                oldLane.incomingHash==digest(bundle.encode()) && oldLane.incomingPeer==peer)
            return@atomic oldLane.state
        }
        check(CustodyStore(chat,clock).enabled()) { "Novas custódias desativadas" }
        val previous=packet(bundle.envelope.id)
        if(previous==null) insertPacket(bundle,remaining,false)
        else {
            require(previous.hash==bundle.envelope.hash() && previous.policy?.contentEquals(bundle.policy.encode())==true)
            check(previous.state=="ACTIVE")
            // Receiving the second lane cannot refresh the packet's time budget.
            db.execSQL("UPDATE forward_packets SET remaining=min(remaining,?),initial=min(initial,?) WHERE id=?",arrayOf<Any>(remaining,remaining,previous.id))
        }
        insertLane(bundle,peer)
        "ACTIVE"
    }
    fun stored(query: ForwardQuery,peer: String) = atomic {
        val lane=requireNotNull(lane(query.lane)); val grant=bundle(lane)
        require(lane.peer==peer && lane.state in listOf("PENDING","TRANSFERRED") && grant.envelope.id==query.id &&
            grant.envelope.hash()==query.hash && grant.hops.last().transaction==query.transaction && digest(grant.encode())==query.offerHash)
        db.execSQL("UPDATE forward_lanes SET state='TRANSFERRED' WHERE id=?",arrayOf(lane.id))
        db.execSQL("UPDATE messages SET status='FORWARDED' WHERE id=? AND outgoing=1 AND status NOT IN ('DELIVERED','EXPIRED')",arrayOf(query.id))
    }
    fun received(query: ForwardQuery,peer: String): ForwardLane? {
        val l=lane(query.lane) ?: return null
        require(l.message==query.id && packet(query.id)?.hash==query.hash && l.incoming==query.transaction &&
            l.incomingHash==query.offerHash && l.incomingPeer==peer)
        return l
    }
    fun active(): List<ForwardPacket> = db.rawQuery("SELECT id FROM forward_packets WHERE state='ACTIVE' AND remaining>0 ORDER BY CASE kind WHEN 'ACK' THEN 0 ELSE 1 END,attempted,id LIMIT 128",null).use { c -> buildList { while(c.moveToNext()) add(requireNotNull(packet(c.getString(0)))) } }
    fun attempted(id: String) { db.execSQL("UPDATE forward_packets SET attempted=? WHERE id=?",arrayOf<Any>(clock().elapsed,id)) }
    fun applyProof(proof: ForwardBundle): Boolean = atomic {
        proof.validate(); require(proof.envelope.kind=="ACK" && proof.hops.isEmpty())
        val p=packet(proof.policy.reference) ?: return@atomic false
        require(p.kind=="TEXT" && p.hash==proof.policy.referenceHash && p.destination==proof.envelope.from && p.origin==proof.envelope.to)
        finish(p.id,"DONE",proof.encode())
        true
    }
    fun finish(id: String,state: String="DONE",proof: ByteArray?=null) {
        require(state in listOf("DONE","REMOVED"))
        atomic {
            val existing=packet(id) ?: return@atomic
            if(existing.state in listOf("DONE","REMOVED")) {
                if(proof!=null && existing.proof==null) {
                    require(proof.size<=4096)
                    db.update("forward_packets",ContentValues().apply { put("proof",proof) },"id=?",arrayOf(id))
                }
                return@atomic
            }
            // Retain terminal markers for 14 days; repeated controls never renew their timer.
            db.update("forward_packets",stamp(ContentValues().apply { put("state",state); put("retention",RETENTION); putNull("envelope"); putNull("policy"); if(proof!=null) { require(proof.size<=4096); put("proof",proof) } }),"id=?",arrayOf(id))
            db.execSQL("UPDATE forward_lanes SET state=?,chain=NULL WHERE message=?",arrayOf(state,id))
        }
    }
    fun knownProof(id: String): ForwardBundle? = packet(id)?.proof?.let(ForwardBundle::decode)
    fun snapshot(): List<HeldPacket> = active().filter { !it.own }.map { p ->
        val lanes=lanes(p.id)
        HeldPacket(p.id,p.origin,p.destination,p.hash,
            if(lanes.any { it.state=="PENDING" }) "FORWARD_PENDING" else if(lanes.all { it.state=="TRANSFERRED" }) "FORWARD_TRANSFERRED" else if(p.kind=="ACK") "FORWARD_RECEIPT" else "FORWARD_ACTIVE",
            p.remaining,p.envelope,null,lanes.count { it.state=="ACTIVE" },lanes.count { it.state=="PENDING" })
    }
    fun usage(): Pair<Int,Long> = db.rawQuery("SELECT count(*),coalesce(sum(coalesce(length(envelope),0)+coalesce(length(policy),0)+coalesce(length(proof),0)),0)+(SELECT coalesce(sum(length(chain)),0) FROM forward_lanes) FROM forward_packets WHERE state='ACTIVE'",null).use { it.moveToFirst(); it.getInt(0) to it.getLong(1) }
    companion object {
        const val RESERVATION=32768L
        const val RETENTION=14L*24*60*60*1000
        fun create(db: SQLiteDatabase) {
            db.execSQL("CREATE TABLE IF NOT EXISTS forward_packets(id TEXT PRIMARY KEY,origin TEXT NOT NULL,destination TEXT NOT NULL,hash TEXT NOT NULL,kind TEXT NOT NULL,state TEXT NOT NULL,own INTEGER NOT NULL,remaining INTEGER NOT NULL,initial INTEGER NOT NULL,retention INTEGER NOT NULL,wall INTEGER NOT NULL,elapsed INTEGER NOT NULL,boot TEXT NOT NULL,envelope BLOB,policy BLOB,proof BLOB,attempted INTEGER NOT NULL)")
            db.execSQL("CREATE TABLE IF NOT EXISTS forward_lanes(id TEXT PRIMARY KEY,message TEXT NOT NULL,state TEXT NOT NULL,peer TEXT NOT NULL,incoming TEXT NOT NULL,incoming_hash TEXT NOT NULL,incoming_peer TEXT NOT NULL,chain BLOB)")
            db.execSQL("CREATE INDEX IF NOT EXISTS forward_message ON forward_lanes(message)")
        }
    }
}
