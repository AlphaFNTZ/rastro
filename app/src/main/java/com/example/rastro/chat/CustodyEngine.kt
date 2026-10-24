package com.example.rastro.chat

import java.util.UUID

/** Only talks to explicit connected endpoints. Never invokes ChatRelay or broadcasts custody. */
class CustodyEngine(
    private val crypto: ChatCrypto,
    private val store: ChatStore,
    private val custody: CustodyStore,
    private val name: () -> String,
    private val transmit: (String, ByteArray) -> Unit,
    private val deliver: (ChatEnvelope, String?) -> ChatEnvelope?,
    private val changed: () -> Unit = {}
) {
    private data class Peer(val localNonce: String=UUID.randomUUID().toString(), var remoteNonce: String?=null, var identity: ChatContact?=null, var carrier: Boolean=false, val seen: LinkedHashSet<String> = LinkedHashSet())
    private val peers=linkedMapOf<String,Peer>()
    private val localId=crypto.contact("local").id
    fun connections(endpoints: Set<String>) {
        peers.keys.retainAll(endpoints)
        endpoints.take(32).forEach { endpoint ->
            if(endpoint !in peers) { val p=Peer(); peers[endpoint]=p; transmit(endpoint,CustodyFrame.challenge(p.localNonce).encode()) }
        }
    }
    fun clear() { peers.clear() }
    fun tick() {
        custody.maintain()
        peers.toMap().forEach { (endpoint,p) ->
            if(p.identity==null || p.remoteNonce==null) transmit(endpoint,CustodyFrame.challenge(p.localNonce).encode())
            else { hello(endpoint); pump(endpoint) }
        }
        changed()
    }
    private fun send(endpoint: String, action: String, body: ByteArray) {
        val nonce=peers[endpoint]?.remoteNonce ?: return
        val unsigned=CustodyFrame(action,nonce,UUID.randomUUID().toString(),body,byteArrayOf())
        transmit(endpoint,unsigned.copy(signature=crypto.sign(unsigned.signed())).encode())
    }
    private fun hello(endpoint: String) = send(endpoint,"HELLO",pack { writeUTF(crypto.contact(name()).qr()); writeBoolean(custody.enabled()) })
    fun receive(endpoint: String, bytes: ByteArray) {
        val peer=peers[endpoint] ?: return
        val frame=CustodyFrame.decode(bytes)
        if(frame.action=="CHALLENGE") {
            require(frame.body.isEmpty() && frame.signature.isEmpty())
            peer.remoteNonce=frame.nonce
            hello(endpoint)
            return
        }
        require(frame.nonce==peer.localNonce) { "Sessão expirada" }
        if(frame.action=="HELLO") {
            lateinit var identity: ChatContact; var mode=false
            unpack(frame.body) { identity=ChatContact.parse(readUTF()); mode=readBoolean() }
            ChatCrypto.validate(identity); require(identity.id!=localId)
            ChatCrypto.verify(identity,frame.signed(),frame.signature)
            require(peer.identity==null || peer.identity?.id==identity.id) { "Identidade mudou durante a conexão" }
            peer.identity=identity; peer.carrier=mode
        } else ChatCrypto.verify(requireNotNull(peer.identity),frame.signed(),frame.signature)
        if(!peer.seen.add(frame.id)) return
        if(peer.seen.size>512) peer.seen.remove(peer.seen.first())
        custody.maintain()
        val actor=requireNotNull(peer.identity)
        when(frame.action) {
            "HELLO" -> pump(endpoint)
            "QUERY" -> {
                val q=Query.decode(frame.body)
                val old=custody.held(q.id)
                if(old!=null) {
                    require(old.origin==actor.id && old.hash==q.hash)
                    when(old.state) {
                        "HELD" -> send(endpoint,"STORED",reference(q.id,q.hash))
                        "RECEIPT" -> send(endpoint,"RETURN",returnBody(old))
                        else -> send(endpoint,"REJECT",reference(q.id,q.hash))
                    }
                } else send(endpoint,if(custody.enabled() && custody.capacity(actor.id,q.size)) "NEED" else "REJECT",reference(q.id,q.hash))
            }
            "NEED" -> {
                val (id,hash)=readReference(frame.body)
                val e=requireNotNull(custody.outgoing(id))
                require(e.from==localId && e.hash()==hash && custody.carrier(id)==actor.id)
                if(custody.status(id) !in listOf("DELIVERED","EXPIRED") && custody.budget(id)>0)
                    send(endpoint,"OFFER",packetBody(e,custody.budget(id)))
            }
            "OFFER" -> {
                val (e,remaining)=readPacket(frame.body)
                require(e.from==actor.id && e.to!=localId && e.kind=="TEXT") { "Repasse entre portadores não permitido" }
                ChatCrypto.verify(actor,e.signedBytes(),e.signature)
                val state=runCatching { custody.accept(e,actor.id,remaining) }.getOrElse {
                    send(endpoint,"REJECT",reference(e.id,e.hash())); return
                }
                if(state=="HELD") send(endpoint,"STORED",reference(e.id,e.hash()))
                else if(state=="RECEIPT") send(endpoint,"RETURN",returnBody(requireNotNull(custody.held(e.id))))
                else send(endpoint,"REJECT",reference(e.id,e.hash()))
            }
            "STORED" -> {
                val (id,hash)=readReference(frame.body)
                require(custody.outgoing(id)?.hash()==hash)
                custody.stored(id,actor.id)
            }
            "REJECT" -> {
                val (id,hash)=readReference(frame.body)
                require(custody.carrier(id)==actor.id && custody.outgoing(id)?.hash()==hash)
                // Keep the assignment. Lost/late custody replies must not create another carrier.
            }
            "DELIVER" -> {
                val (e,_)=readPacket(frame.body)
                require(e.to==localId && e.kind=="TEXT")
                val receipt=requireNotNull(deliver(e,null)) // persists before replying
                send(endpoint,"RETURN",pack { writeUTF(e.id); writeUTF(e.hash()); field(receipt.encode()) })
            }
            "RETURN" -> {
                var id=""; var hash=""; lateinit var receipt: ChatEnvelope
                unpack(frame.body) { id=readUTF(); hash=readUTF(); receipt=ChatEnvelope.decode(field(12288)) }
                uuid(id); require(receipt.kind=="ACK")
                if(receipt.to==localId) {
                    val original=requireNotNull(custody.outgoing(id))
                    require(original.hash()==hash && original.to==receipt.from)
                    deliver(receipt,id) // validates recipient signature, decrypted reference and hash
                    require(custody.status(id)=="DELIVERED")
                    send(endpoint,"SAVED",reference(id,hash))
                } else {
                    val old=requireNotNull(custody.held(id))
                    require(old.hash==hash && actor.id==old.destination && receipt.from==actor.id)
                    ChatCrypto.verify(actor,receipt.signedBytes(),receipt.signature)
                    custody.receipt(id,receipt,actor.id)
                }
            }
            "SAVED", "RELEASE" -> {
                val (id,hash)=readReference(frame.body)
                custody.held(id)?.let { require(it.hash==hash); custody.finish(id,actor.id) }
                send(endpoint,"RELEASED",reference(id,hash))
            }
            "RELEASED" -> {
                val (id,hash)=readReference(frame.body)
                require(custody.outgoing(id)?.hash()==hash && custody.status(id)=="DELIVERED")
                custody.released(id,actor.id)
            }
        }
        changed()
    }
    private fun pump(endpoint: String) {
        val peer=peers[endpoint] ?: return
        val identity=peer.identity ?: return
        if(peer.remoteNonce==null) return
        custody.forPeer(identity.id).forEach { packet ->
            // Immutable destination, authenticated on this direct endpoint; no generic routing.
            if(packet.state=="RECEIPT") send(endpoint,"RETURN",returnBody(packet))
            else send(endpoint,"DELIVER",packetBody(ChatEnvelope.decode(requireNotNull(packet.packet)),packet.remaining))
            custody.markAttempt(packet.id)
        }
        custody.releases(identity.id).forEach { id -> send(endpoint,"RELEASE",reference(id,requireNotNull(custody.outgoing(id)).hash())) }
        val connectedIds=peers.values.mapNotNull { it.identity?.id }.toSet()
        store.pending().forEach { e ->
            if(e.to in connectedIds || e.from!=localId || custody.budget(e.id)<=0) return@forEach
            val chosen=custody.carrier(e.id)
            if(chosen.isNullOrEmpty() && peer.carrier) custody.bind(e.id,identity.id)
            if(custody.carrier(e.id)==identity.id) send(endpoint,"QUERY",Query(e.id,e.hash(),e.encode().size).encode())
        }
    }
    private data class Query(val id: String,val hash: String,val size: Int) {
        init { uuid(id); require(hash.matches(Regex("[a-f0-9]{64}")) && size in 1..12288) }
        fun encode()=pack { writeUTF(id); writeUTF(hash); writeInt(size) }
        companion object { fun decode(bytes: ByteArray): Query { lateinit var q: Query; unpack(bytes) { q=Query(readUTF(),readUTF(),readInt()) }; return q } }
    }
    private fun reference(id: String,hash: String)=pack { writeUTF(id); writeUTF(hash) }
    private fun readReference(bytes: ByteArray): Pair<String,String> { var id=""; var hash=""; unpack(bytes) { id=readUTF(); hash=readUTF() }; uuid(id); require(hash.matches(Regex("[a-f0-9]{64}"))); return id to hash }
    private fun packetBody(e: ChatEnvelope,remaining: Long)=pack { writeLong(remaining); field(e.encode()) }
    private fun readPacket(bytes: ByteArray): Pair<ChatEnvelope,Long> { var remaining=0L; lateinit var e: ChatEnvelope; unpack(bytes) { remaining=readLong(); e=ChatEnvelope.decode(field(12288)) }; require(remaining in 1..CustodyStore.LIFETIME); return e to remaining }
    private fun returnBody(p: HeldPacket)=pack { writeUTF(p.id); writeUTF(p.hash); field(requireNotNull(p.receipt)) }
}